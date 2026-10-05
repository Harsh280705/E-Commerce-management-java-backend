package com.ecom.level3.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ecom.level3.model.Level3Order;
import com.ecom.level3.model.Level3OrderItem;
import com.ecom.level3.model.Level3User;
import com.ecom.level3.model.Product;
import com.ecom.level3.repository.OrderItemRepository;
import com.ecom.level3.repository.OrderRepository;
import com.ecom.level3.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Reproducible Level 3 seed (idempotent, fixed plan — mirrors Level 2).
 *
 * <p>PostgreSQL: EXACTLY 8 users + 42 orders (14 PENDING / 14 PROCESSING / 14 SHIPPED).
 * MongoDB: 24 active + 1 inactive products. ES: rebuilt from PostgreSQL, then the
 * MANDATORY SNAPSHOT TEST renames "Wireless Mouse" to "Wireless Mouse Pro"
 * (catalog only — history untouched).
 *
 * <p>Runs only when {@code RUN_SEED_ON_START=true} and data is missing.
 * Never touches Level 2 databases.
 */
@Service
public class SeedService implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SeedService.class);

  static final List<String[]> USERS = List.of(
      new String[]{"John Doe", "john.doe@example.com"},
      new String[]{"Jane Smith", "jane.smith@example.com"},
      new String[]{"Wendy Wireless", "wendy.wireless@example.com"},
      new String[]{"Alex Rivera", "alex.rivera@example.com"},
      new String[]{"Sam Patel", "sam.patel@example.com"},
      new String[]{"Casey Nguyen", "casey.nguyen@example.com"},
      new String[]{"Morgan Lee", "morgan.lee@example.com"},
      new String[]{"Riley Brooks", "riley.brooks@example.com"});

  private final boolean enabled;
  private final UserRepository users;
  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final MongoTemplate mongo;
  private final ReindexService reindex;
  private final TransactionTemplate txTemplate;

  @PersistenceContext
  private EntityManager em;

  public SeedService(@Value("${app.seed.enabled:false}") boolean enabled,
      UserRepository users, OrderRepository orders, OrderItemRepository items,
      MongoTemplate mongo, ReindexService reindex, TransactionTemplate txTemplate) {
    this.enabled = enabled;
    this.users = users;
    this.orders = orders;
    this.items = items;
    this.mongo = mongo;
    this.reindex = reindex;
    this.txTemplate = txTemplate;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled) {
      return;
    }
    if (alreadySeeded()) {
      log.info("Seed data already present; skipping.");
      return;
    }
    seedAll();
  }

  public boolean alreadySeeded() {
    long nUsers;
    long nOrders;
    try {
      nUsers = users.count();
      nOrders = orders.count();
    } catch (Exception ex) {
      log.warn("PostgreSQL unreachable, skipping seed (no changes made): {}", ex.getMessage());
      return true;
    }
    long nProducts;
    try {
      nProducts = mongo.getCollection("products").countDocuments();
    } catch (Exception ex) {
      log.warn("MongoDB unreachable, skipping seed (no changes made): {}", ex.getMessage());
      return true;
    }
    return nUsers == 8 && nOrders >= 40 && nProducts >= 24;
  }

  public void seedAll() {
    Instant now = Instant.now();
    txTemplate.executeWithoutResult(tx -> {
      items.deleteAll();
      orders.deleteAll();
      users.deleteAll();
      List<Level3User> saved = new ArrayList<>();
      for (String[] u : USERS) {
        saved.add(users.save(new Level3User(u[0], u[1])));
      }
      saved.sort((a, b) -> Long.compare(a.getId(), b.getId()));

      mongo.dropCollection("products");
      List<Product> docs = buildProducts();
      mongo.insert(docs, "products");
      IndexOperations ops = mongo.indexOps("products");
      ops.ensureIndex(new Index().on("sku", org.springframework.data.domain.Sort.Direction.ASC).unique());

      Map<String, Product> byTitle = new LinkedHashMap<>();
      for (Product p : mongo.findAll(Product.class, "products")) {
        byTitle.put(p.getTitle(), p);
      }
      Map<String, Level3User> byFirst = new LinkedHashMap<>();
      for (Level3User u : saved) {
        byFirst.put(u.getName().split(" ")[0], u);
      }
      List<Level3Order> created = new ArrayList<>();
      for (Object[] spec : orderSpecs()) {
        @SuppressWarnings("unchecked")
        List<Object[]> lines = (List<Object[]>) spec[3];
        created.add(createSeedOrder(byFirst, byTitle, now,
            (String) spec[0], (String) spec[1], (int) spec[2], lines));
      }
      em.flush();
      for (Level3Order o : created) {
        em.createQuery("update Level3Order o set o.orderDate = :d, o.updatedAt = :u where o.id = :id")
            .setParameter("d", o.getOrderDate())
            .setParameter("u", o.getUpdatedAt())
            .setParameter("id", o.getId())
            .executeUpdate();
      }
      em.flush();
      em.clear();
      // Inside the ambient transaction so lazy order items load (self-invocation
      // bypasses the @Transactional proxy on verifyQuotas).
      verifyQuotas();
    });
    try {
      Map<String, Object> es = reindex.rebuildIndex();
      log.info("Seeded ES index: {}", es);
    } catch (Exception ex) {
      log.warn("Seed ES rebuild failed (poller will backfill): {}", ex.getMessage());
    }
    snapshotTestRenameMouse();
    log.info("Seeded 8 users (PG), 26 products (MongoDB), 42 orders (PG).");
  }

  private Level3Order createSeedOrder(Map<String, Level3User> byFirst, Map<String, Product> byTitle,
      Instant now, String firstName, String status, int daysAgo, List<Object[]> lines) {
    Instant orderDate = now.minus(daysAgo, ChronoUnit.DAYS);
    Level3User user = byFirst.get(firstName);
    Level3Order order = new Level3Order();
    order.setUser(user);
    order.setStatus(status);
    order.setTotalAmount(BigDecimal.ZERO);
    order = orders.save(order);
    BigDecimal total = BigDecimal.ZERO;
    for (Object[] line : lines) {
      String title = (String) line[0];
      int qty = (int) line[1];
      Product prod = byTitle.get(title);
      BigDecimal unit = BigDecimal.valueOf(prod.getPrice()).setScale(2, RoundingMode.HALF_UP);
      total = total.add(unit.multiply(BigDecimal.valueOf(qty)));
      Level3OrderItem item = new Level3OrderItem();
      item.setOrder(order);
      item.setProductId(prod.getId());
      item.setTitle(prod.getTitle());
      item.setQuantity(qty);
      item.setUnitPrice(unit);
      order.getItems().add(item);
    }
    order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
    order.setOrderDate(orderDate);
    order.setUpdatedAt(orderDate.plus(2, ChronoUnit.HOURS));
    return orders.save(order);
  }

  @Transactional(readOnly = true)
  public void verifyQuotas() {
    List<Level3Order> all = orders.findAll();
    for (Level3Order o : all) {
      o.getItems().size();
    }
    check(all.size() == 42, "expected 42 orders, got " + all.size());
    for (String st : List.of("PENDING", "PROCESSING", "SHIPPED")) {
      long n = all.stream().filter(o -> st.equals(o.getStatus())).count();
      check(n == 14, "expected 14 " + st + ", got " + n);
    }
    Instant now = Instant.now();
    check(all.stream().filter(o -> !o.getOrderDate().isAfter(now.minus(60, ChronoUnit.DAYS))).count() >= 5,
        "need >=5 orders older than 60 days");
    check(all.stream().filter(o -> !o.getOrderDate().isBefore(now.minus(7, ChronoUnit.DAYS))).count() >= 5,
        "need >=5 orders within 7 days");
    check(all.stream().filter(o -> o.getTotalAmount().doubleValue() < 30).count() >= 5, "need >=5 orders < $30");
    check(all.stream().filter(o -> {
      double t = o.getTotalAmount().doubleValue();
      return t >= 30 && t <= 150;
    }).count() >= 5, "need >=5 orders $30-$150");
    check(all.stream().filter(o -> o.getTotalAmount().doubleValue() > 200).count() >= 5, "need >=5 orders > $200");
    for (Level3User u : users.findAll()) {
      final Long uid = u.getId();
      check(all.stream().filter(o -> uid.equals(o.getUser().getId())).count() >= 2,
          "user needs >=2 orders: " + u.getEmail());
    }
    Long wendyId = users.findAll().stream()
        .filter(u -> u.getName().startsWith("Wendy")).findFirst().orElseThrow().getId();
    check(all.stream().filter(o -> wendyId.equals(o.getUser().getId())
        && o.getItems().stream().anyMatch(i -> i.getTitle().contains("Wireless"))).count() >= 3,
        "wendy needs >=3 wireless orders");
    check(all.stream().filter(o -> o.getItems().stream().anyMatch(i -> "Wireless Mouse".equals(i.getTitle()))).count() >= 8,
        "need >=8 Wireless Mouse orders");
    check(all.stream().filter(o -> o.getItems().stream().anyMatch(i -> "Mechanical Keyboard".equals(i.getTitle()))).count() >= 3,
        "need >=3 Mechanical Keyboard orders");
    long both = all.stream().filter(o
        -> o.getItems().stream().anyMatch(i -> "Wireless Mouse".equals(i.getTitle()))
        && o.getItems().stream().anyMatch(i -> "Mechanical Keyboard".equals(i.getTitle()))).count();
    check(both >= 2, "need >=2 orders with both mouse+keyboard");
    long nItems = all.stream().mapToLong(o -> o.getItems().size()).sum();
    check(nItems >= 80 && (double) nItems / 42 >= 2, "item quota failed: " + nItems);
    for (Level3Order o : all) {
      double sum = o.getItems().stream()
          .mapToDouble(i -> i.getQuantity() * i.getUnitPrice().doubleValue()).sum();
      check(Math.abs(o.getTotalAmount().doubleValue() - sum) < 0.01, "total mismatch order " + o.getId());
    }
  }

  private static void check(boolean ok, String msg) {
    if (!ok) {
      throw new IllegalStateException("Seed quota failed: " + msg);
    }
  }

  void snapshotTestRenameMouse() {
    mongo.updateFirst(Query.query(Criteria.where("title").is("Wireless Mouse")),
        new Update().set("title", "Wireless Mouse Pro").set("price", 34.99).set("updated_at", Instant.now()),
        "products");
    log.info("Catalog now shows: Wireless Mouse Pro @ $34.99 (history unchanged).");
  }

  // ---------- fixed catalog (24 active + 1 inactive, mirrors Level 2) ----------

  static List<Product> buildProducts() {
    List<Product> out = new ArrayList<>();
    out.add(p("PER-001", "Wireless Mouse", "Ergonomic wireless mouse with silent clicks", 24.99,
        "peripherals", List.of("wireless", "mouse", "office"), "LogiTech",
        Map.of("connection", "Bluetooth", "color", "Black"),
        List.of(v("Black", "PER-001-BLK", 0), v("White", "PER-001-WHT", 2.0)), true));
    out.add(p("PER-002", "Mechanical Keyboard", "Hot-swap RGB mechanical keyboard", 89.99,
        "peripherals", List.of("keyboard", "gaming", "rgb"), "KeyPro",
        Map.of("switch", "Brown", "layout", "TKL"),
        List.of(v("Brown switch", "PER-002-BRN", 0), v("Red switch", "PER-002-RED", 0)), true));
    out.add(p("PER-003", "USB-C Hub 7-in-1", "7-in-1 USB-C docking hub", 45.50,
        "peripherals", List.of("usb-c", "hub", "office"), "DockWell",
        Map.of("ports", 7, "color", "Grey"), List.of(v("Standard", "PER-003-STD", 0)), true));
    out.add(p("PER-004", "Wireless Keyboard Combo", "Wireless keyboard and mouse combo", 69.99,
        "peripherals", List.of("wireless", "keyboard", "mouse"), "KeyPro",
        Map.of("connection", "2.4GHz", "color", "Black"),
        List.of(v("US layout", "PER-004-US", 0), v("UK layout", "PER-004-UK", 3.0)), true));
    out.add(p("PER-005", "Ergonomic Wired Mouse", "Comfortable wired optical mouse", 14.99,
        "peripherals", List.of("mouse", "wired", "office"), "LogiTech",
        Map.of("connection", "USB", "color", "Grey"), List.of(v("Standard", "PER-005-STD", 0)), true));
    out.add(p("PER-006", "Webcam HD", "1080p webcam with dual microphone", 79.00,
        "peripherals", List.of("webcam", "video", "office"), "ViewClear",
        Map.of("resolution", "1080p", "color", "Black"), List.of(v("Standard", "PER-006-STD", 0)), true));
    out.add(p("PER-007", "4K Monitor 27in", "27 inch 4K UHD office monitor", 249.99,
        "peripherals", List.of("monitor", "display", "office"), "ViewClear",
        Map.of("size", "27in", "resolution", "4K"),
        List.of(v("With stand", "PER-007-STD", 0), v("VESA only", "PER-007-VESA", -10.0)), true));
    out.add(p("AUD-001", "Wireless Earbuds", "True wireless earbuds with charging case", 49.99,
        "audio", List.of("wireless", "earbuds", "audio"), "SoundBeat",
        Map.of("battery", "24h", "color", "White"),
        List.of(v("White", "AUD-001-WHT", 0), v("Black", "AUD-001-BLK", 0)), true));
    out.add(p("AUD-002", "Noise Cancelling Headphones", "Over-ear noise cancelling headphones", 199.99,
        "audio", List.of("headphones", "audio", "anc"), "SoundBeat",
        Map.of("battery", "40h", "color", "Blue"),
        List.of(v("Blue", "AUD-002-BLU", 0), v("Black", "AUD-002-BLK", 0)), true));
    out.add(p("AUD-003", "Bluetooth Speaker", "Portable waterproof bluetooth speaker", 35.00,
        "audio", List.of("wireless", "speaker", "audio"), "BoomBox",
        Map.of("waterproof", "IPX7", "color", "Green"), List.of(v("Standard", "AUD-003-STD", 0)), true));
    out.add(p("AUD-004", "Wired Earphones", "In-ear wired earphones with mic", 9.99,
        "audio", List.of("earphones", "wired", "audio"), "SoundBeat",
        Map.of("connection", "3.5mm", "color", "Black"), List.of(v("Standard", "AUD-004-STD", 0)), true));
    out.add(p("AUD-005", "Studio Headset", "Wired studio headset for calls", 129.00,
        "audio", List.of("headset", "audio", "office"), "CallPro",
        Map.of("connection", "USB", "color", "Black"), List.of(v("Standard", "AUD-005-STD", 0)), true));
    out.add(p("AUD-006", "Soundbar", "Compact TV soundbar with bluetooth", 149.99,
        "audio", List.of("speaker", "audio", "tv"), "BoomBox",
        Map.of("channels", "2.1", "color", "Black"), List.of(v("Standard", "AUD-006-STD", 0)), true));
    out.add(p("CAB-001", "USB-C Cable 2m", "Braided 2m USB-C fast-charge cable", 12.99,
        "cables", List.of("usb-c", "cable", "charging"), "CablePro",
        Map.of("length", "2m", "color", "Black"),
        List.of(v("1m", "CAB-001-1M", -3.0), v("2m", "CAB-001-2M", 0)), true));
    out.add(p("CAB-002", "HDMI Cable 4K", "4K high-speed HDMI cable", 18.50,
        "cables", List.of("hdmi", "cable", "video"), "CablePro",
        Map.of("length", "1.5m", "color", "Black"), List.of(v("Standard", "CAB-002-STD", 0)), true));
    out.add(p("CAB-003", "Wireless Charging Pad", "Fast wireless charging pad", 29.99,
        "cables", List.of("wireless", "charging", "pad"), "ChargeIt",
        Map.of("power", "15W", "color", "White"), List.of(v("Standard", "CAB-003-STD", 0)), true));
    out.add(p("CAB-004", "Ethernet Cable 10m", "Cat6 10m ethernet cable", 15.99,
        "cables", List.of("ethernet", "cable", "network"), "CablePro",
        Map.of("length", "10m", "color", "Yellow"), List.of(v("Standard", "CAB-004-STD", 0)), true));
    out.add(p("CAB-005", "Cable Organizer Kit", "Desk cable management kit", 8.99,
        "cables", List.of("organizer", "cable", "office"), "TidyUp",
        Map.of("pieces", 12, "color", "Black"), List.of(v("Standard", "CAB-005-STD", 0)), true));
    out.add(p("CAB-006", "USB Hub 4-Port", "Compact 4-port USB hub", 21.99,
        "cables", List.of("usb", "hub", "office"), "DockWell",
        Map.of("ports", 4, "color", "Black"), List.of(v("Standard", "CAB-006-STD", 0)), true));
    out.add(p("OFF-001", "Desk Lamp LED", "LED desk lamp with dimmer", 39.99,
        "office", List.of("lamp", "office", "led"), "BrightDesk",
        Map.of("power", "12W", "color", "White"), List.of(v("Standard", "OFF-001-STD", 0)), true));
    out.add(p("OFF-002", "Laptop Stand", "Ergonomic aluminium laptop stand", 27.50,
        "office", List.of("laptop", "stand", "ergonomic"), "ErgoPlus",
        Map.of("material", "Aluminium", "color", "Silver"), List.of(v("Standard", "OFF-002-STD", 0)), true));
    out.add(p("OFF-003", "Notebook Set", "Pack of 3 dotted notebooks", 11.99,
        "office", List.of("notebook", "office", "paper"), "PaperMate",
        Map.of("pages", 192, "color", "Mixed"), List.of(v("Standard", "OFF-003-STD", 0)), true));
    out.add(p("OFF-004", "Desk Organizer", "Bamboo desk organizer tray", 22.99,
        "office", List.of("organizer", "office", "desk"), "TidyUp",
        Map.of("material", "Bamboo", "color", "Natural"), List.of(v("Standard", "OFF-004-STD", 0)), true));
    out.add(p("OFF-005", "Wireless Presenter Remote", "Wireless presentation remote with laser", 32.99,
        "office", List.of("wireless", "presenter", "office"), "PresentPro",
        Map.of("range", "30m", "color", "Black"), List.of(v("Standard", "OFF-005-STD", 0)), true));
    out.add(p("OFF-006", "Ergonomic Chair Cushion", "Memory foam seat cushion", 24.99,
        "office", List.of("chair", "office", "ergonomic"), "ErgoPlus",
        Map.of("material", "Memory foam", "color", "Grey"), List.of(v("Standard", "OFF-006-STD", 0)), true));
    out.add(p("PER-099", "Old Wired Keyboard", "Discontinued wired keyboard", 29.99,
        "peripherals", List.of("keyboard", "wired"), "KeyPro",
        Map.of("layout", "Full", "color", "Beige"), List.of(v("Standard", "PER-099-STD", 0)), false));
    return out;
  }

  private static Map<String, Object> v(String name, String sku, double adj) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", name);
    m.put("sku", sku);
    m.put("priceAdjustment", adj);
    return m;
  }

  private static Product p(String sku, String title, String desc, double price, String category,
      List<String> tags, String brand, Map<String, Object> extra, List<Map<String, Object>> variants,
      boolean active) {
    Product doc = new Product();
    doc.setSku(sku);
    doc.setTitle(title);
    doc.setDescription(desc);
    doc.setPrice(price);
    doc.setCategory(category);
    doc.setTags(new ArrayList<>(tags));
    Map<String, Object> attrs = new LinkedHashMap<>();
    attrs.put("brand", brand);
    attrs.putAll(extra);
    doc.setAttributes(attrs);
    List<Product.Variant> vs = new ArrayList<>();
    for (Map<String, Object> m : variants) {
      Product.Variant var = new Product.Variant();
      var.setName((String) m.get("name"));
      var.setSku((String) m.get("sku"));
      var.setPriceAdjustment(((Number) m.get("priceAdjustment")).doubleValue());
      vs.add(var);
    }
    doc.setVariants(vs);
    doc.setActive(active);
    doc.setUpdatedAt(Instant.now());
    return doc;
  }

  // ---------- fixed 42-order plan (mirrors Level 2 seed.py) ----------

  static List<Object[]> orderSpecs() {
    List<Object[]> specs = new ArrayList<>();
    specs.add(spec("John", "PENDING", 2, line("Wireless Mouse", 1)));
    specs.add(spec("Jane", "PROCESSING", 3, line("Wireless Mouse", 1)));
    specs.add(spec("Alex", "SHIPPED", 10, line("Wireless Mouse", 1)));
    specs.add(spec("Sam", "PENDING", 1, line("Wireless Mouse", 1)));
    specs.add(spec("Casey", "SHIPPED", 20, line("Wireless Mouse", 1)));
    specs.add(spec("Morgan", "PROCESSING", 6, line("Wireless Mouse", 1), line("Mechanical Keyboard", 1)));
    specs.add(spec("Riley", "PENDING", 12, line("Wireless Mouse", 1), line("Mechanical Keyboard", 1)));
    specs.add(spec("Wendy", "SHIPPED", 30, line("Wireless Mouse", 1), line("Mechanical Keyboard", 1)));
    specs.add(spec("John", "SHIPPED", 15, line("Mechanical Keyboard", 1), line("USB-C Cable 2m", 1)));
    specs.add(spec("Jane", "PENDING", 8, line("Mechanical Keyboard", 1), line("HDMI Cable 4K", 1)));
    specs.add(spec("Alex", "PROCESSING", 25, line("Mechanical Keyboard", 1), line("Ethernet Cable 10m", 1)));
    specs.add(spec("Sam", "SHIPPED", 61, line("4K Monitor 27in", 1)));
    specs.add(spec("Casey", "SHIPPED", 70, line("4K Monitor 27in", 1)));
    specs.add(spec("Morgan", "SHIPPED", 75, line("4K Monitor 27in", 1)));
    specs.add(spec("Riley", "SHIPPED", 82, line("4K Monitor 27in", 1)));
    specs.add(spec("John", "SHIPPED", 89, line("4K Monitor 27in", 1)));
    specs.add(spec("Wendy", "PENDING", 1, line("Wireless Earbuds", 1), line("Wireless Charging Pad", 1)));
    specs.add(spec("Wendy", "PROCESSING", 4, line("Bluetooth Speaker", 2)));
    specs.add(spec("Wendy", "PENDING", 6, line("Wireless Keyboard Combo", 1), line("Wireless Presenter Remote", 1)));
    specs.add(spec("Jane", "PENDING", 5, line("Desk Lamp LED", 1), line("Notebook Set", 2)));
    specs.add(spec("Alex", "PROCESSING", 9, line("Laptop Stand", 1), line("Desk Organizer", 1)));
    specs.add(spec("Sam", "PENDING", 11, line("Wired Earphones", 2), line("Cable Organizer Kit", 1)));
    specs.add(spec("Casey", "PROCESSING", 14, line("Webcam HD", 1), line("USB Hub 4-Port", 1)));
    specs.add(spec("Morgan", "PENDING", 18, line("Ergonomic Chair Cushion", 1), line("Notebook Set", 1)));
    specs.add(spec("Riley", "PROCESSING", 22, line("Wireless Earbuds", 1), line("USB-C Cable 2m", 1)));
    specs.add(spec("John", "SHIPPED", 28, line("Ergonomic Wired Mouse", 2), line("HDMI Cable 4K", 1)));
    specs.add(spec("Jane", "SHIPPED", 33, line("Studio Headset", 1), line("Ethernet Cable 10m", 2)));
    specs.add(spec("Alex", "PENDING", 40, line("Desk Lamp LED", 1), line("Cable Organizer Kit", 3)));
    specs.add(spec("Sam", "PROCESSING", 45, line("Bluetooth Speaker", 1), line("Wireless Charging Pad", 1)));
    specs.add(spec("Casey", "PENDING", 50, line("Laptop Stand", 1), line("Wired Earphones", 1)));
    specs.add(spec("Morgan", "SHIPPED", 55, line("Soundbar", 1), line("HDMI Cable 4K", 1)));
    specs.add(spec("Riley", "PENDING", 16, line("Desk Organizer", 1), line("Notebook Set", 1)));
    specs.add(spec("Wendy", "PROCESSING", 21, line("Wireless Presenter Remote", 1), line("USB Hub 4-Port", 2)));
    specs.add(spec("John", "PROCESSING", 26, line("Mechanical Keyboard", 1), line("Desk Lamp LED", 1)));
    specs.add(spec("Jane", "SHIPPED", 35, line("Noise Cancelling Headphones", 1), line("Cable Organizer Kit", 1)));
    specs.add(spec("Alex", "SHIPPED", 48, line("Ergonomic Chair Cushion", 1), line("Wired Earphones", 3)));
    specs.add(spec("Sam", "PROCESSING", 7, line("USB-C Cable 2m", 1), line("HDMI Cable 4K", 1),
        line("Ethernet Cable 10m", 1), line("Cable Organizer Kit", 1)));
    specs.add(spec("Casey", "PENDING", 13, line("Notebook Set", 1), line("Desk Organizer", 1),
        line("Wired Earphones", 1), line("USB Hub 4-Port", 1)));
    specs.add(spec("Morgan", "PROCESSING", 31, line("Wireless Mouse", 2), line("Cable Organizer Kit", 1),
        line("Notebook Set", 1), line("Wired Earphones", 1)));
    specs.add(spec("Riley", "PROCESSING", 44, line("Desk Lamp LED", 1), line("Laptop Stand", 1),
        line("Desk Organizer", 1), line("Notebook Set", 2)));
    specs.add(spec("Wendy", "PENDING", 52, line("Wireless Earbuds", 1), line("Bluetooth Speaker", 1),
        line("USB-C Cable 2m", 2), line("Cable Organizer Kit", 1)));
    specs.add(spec("John", "PROCESSING", 58, line("Webcam HD", 1), line("Ergonomic Wired Mouse", 1),
        line("USB Hub 4-Port", 1), line("HDMI Cable 4K", 1)));
    return specs;
  }

  private static Object[] line(String title, int qty) {
    return new Object[]{title, qty};
  }

  private static Object[] spec(String name, String status, int days, Object[]... lines) {
    return new Object[]{name, status, days, List.of(lines)};
  }
}
