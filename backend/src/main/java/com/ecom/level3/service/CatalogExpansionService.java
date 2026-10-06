package com.ecom.level3.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.ecom.level3.model.Product;
import com.ecom.level3.repository.ProductRepository;

/**
 * Adds EXACTLY 200 new catalog products to MongoDB (idempotent).
 *
 * <p>Runs on {@link ApplicationReadyEvent} so it always executes AFTER
 * {@link SeedService} (an ApplicationRunner) without modifying it.
 * Only inserts products whose SKU is absent — existing products are never
 * updated, renamed, or deleted. No PostgreSQL / RabbitMQ / Elasticsearch
 * interaction: catalog-only expansion.
 */
@Service
public class CatalogExpansionService {

  private static final Logger log = LoggerFactory.getLogger(CatalogExpansionService.class);

  /** Number of NEW products this expansion owns. Must stay exactly 200. */
  public static final int EXPECTED_NEW_PRODUCTS = 200;

  private final boolean enabled;
  private final ProductRepository products;

  public CatalogExpansionService(@Value("${app.seed.enabled:false}") boolean enabled,
      ProductRepository products) {
    this.enabled = enabled;
    this.products = products;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    if (!enabled) {
      return;
    }
    try {
      int inserted = ensureExpanded();
      log.info("Catalog expansion check complete: inserted={} (owns {} SKUs).", inserted,
          EXPECTED_NEW_PRODUCTS);
    } catch (Exception ex) {
      log.warn("Catalog expansion failed (will retry on next restart): {}", ex.getMessage());
    }
  }

  /**
   * Insert every missing expansion SKU. Idempotent: re-running inserts 0.
   *
   * @return number of products inserted by this call.
   */
  public int ensureExpanded() {
    List<Product> all = buildExpansionProducts();
    if (all.size() != EXPECTED_NEW_PRODUCTS) {
      throw new IllegalStateException(
          "Expansion must define exactly 200 products, got " + all.size());
    }
    int inserted = 0;
    for (Product p : all) {
      if (products.findBySku(p.getSku()).isPresent()) {
        continue;
      }
      try {
        products.save(p);
        inserted++;
      } catch (DuplicateKeyException dup) {
        log.debug("SKU already present (concurrent insert): {}", p.getSku());
      }
    }
    return inserted;
  }

  // ---------- 200 fixed products: 50 peripherals + 50 audio + 50 cables + 50 office ----------

  static List<Product> buildExpansionProducts() {
    // sku, title, description, price, category, tagsCsv, brand, attrK1, attrV1, attrK2, attrV2
    List<Object[]> rows = List.of(
        // ----- PERIPHERALS (50): PER-101..PER-150 -----
        row("PER-101", "Silent Wireless Mouse Pro", "Ergonomic silent-click wireless mouse with adjustable DPI", 29.99, "peripherals", "wireless,mouse,office", "LogiTech", "connection", "2.4GHz", "color", "Graphite"),
        row("PER-102", "RGB Gaming Mouse 16000DPI", "High-precision gaming mouse with RGB lighting and 8 buttons", 54.99, "peripherals", "gaming,mouse,rgb", "VoltEdge", "dpi", "16000", "color", "Black"),
        row("PER-103", "Vertical Ergonomic Mouse", "Vertical ergonomic mouse that reduces wrist strain all day", 39.99, "peripherals", "mouse,ergonomic,office", "ErgoPlus", "connection", "Bluetooth", "handedness", "Right"),
        row("PER-104", "Travel Mini Bluetooth Mouse", "Compact travel mouse with multi-device bluetooth pairing", 19.99, "peripherals", "mouse,wireless,travel", "LogiTech", "devices", "3", "color", "White"),
        row("PER-105", "Low-Profile Mechanical Keyboard", "Slim low-profile mechanical keyboard with quiet switches", 99.99, "peripherals", "keyboard,gaming,office", "KeyPro", "switch", "Low-profile Red", "layout", "TKL"),
        row("PER-106", "Wireless Tenkeyless Keyboard", "Lag-free 2.4GHz tenkeyless wireless keyboard", 74.99, "peripherals", "keyboard,wireless,office", "KeyPro", "connection", "2.4GHz", "battery", "200h"),
        row("PER-107", "Ergonomic Split Keyboard", "Split ergonomic keyboard with tenting for comfy typing", 129.99, "peripherals", "keyboard,ergonomic,office", "ErgoPlus", "layout", "Split", "connection", "USB-C"),
        row("PER-108", "Compact 60 Percent Keyboard", "Hot-swap 60 percent keyboard for gaming and travel", 69.99, "peripherals", "keyboard,gaming,compact", "KeyPro", "layout", "60%", "switch", "Brown"),
        row("PER-109", "Backlit Membrane Keyboard", "Quiet backlit membrane keyboard with spill resistance", 34.99, "peripherals", "keyboard,office,backlit", "KeyPro", "backlight", "White", "layout", "Full"),
        row("PER-110", "Gaming Wrist Rest Pad", "Cooling gel wrist rest for keyboards", 16.99, "peripherals", "keyboard,gaming,ergonomic", "ErgoPlus", "material", "Gel", "color", "Black"),
        row("PER-111", "USB-C Docking Station 11-in-1", "11-in-1 docking station with dual HDMI and 100W delivery", 139.99, "peripherals", "dock,usb-c,office", "DockWell", "ports", "11", "power", "100W"),
        row("PER-112", "Portable Laptop Dock Mini", "Pocket-size 6-in-1 USB-C hub for travel", 49.99, "peripherals", "dock,usb-c,travel", "DockWell", "ports", "6", "color", "Grey"),
        row("PER-113", "Wireless Trackball Mouse", "Thumb-operated wireless trackball for precise control", 59.99, "peripherals", "mouse,wireless,ergonomic", "LogiTech", "connection", "Bluetooth", "dpi", "2000"),
        row("PER-114", "Precision Drawing Tablet 10in", "Battery-free stylus drawing tablet for designers", 79.99, "peripherals", "tablet,drawing,creative", "SketchPro", "size", "10in", "levels", "8192"),
        row("PER-115", "HD Pro Webcam 2K", "2K webcam with autofocus and noise-cancelling mics", 99.00, "peripherals", "webcam,video,office", "ViewClear", "resolution", "2K", "shutter", "Privacy"),
        row("PER-116", "4K Streaming Webcam", "4K webcam with light correction for streamers", 149.99, "peripherals", "webcam,streaming,video", "ViewClear", "resolution", "4K", "fps", "30"),
        row("PER-117", "Ring Light with Tripod 10in", "Dimmable 10 inch ring light with extendable tripod", 32.99, "peripherals", "lighting,video,streaming", "BrightDesk", "diameter", "10in", "modes", "3"),
        row("PER-118", "Curved Monitor 32in QHD", "32 inch curved QHD monitor with 165Hz refresh", 329.99, "peripherals", "monitor,display,gaming", "ViewClear", "size", "32in", "refresh", "165Hz"),
        row("PER-119", "Ultrawide Monitor 34in", "34 inch ultrawide office monitor with height adjust", 399.99, "peripherals", "monitor,display,office", "ViewClear", "size", "34in", "resolution", "UWQHD"),
        row("PER-120", "Portable Monitor 15.6in", "15.6 inch portable USB-C monitor for laptops", 179.99, "peripherals", "monitor,portable,travel", "ViewClear", "size", "15.6in", "panel", "IPS"),
        row("PER-121", "Monitor Light Bar", "Eye-care monitor light bar with auto dimming", 44.99, "peripherals", "monitor,lighting,office", "BrightDesk", "power", "USB", "color", "Black"),
        row("PER-122", "Dual Monitor Arm", "Gas-spring dual monitor arm for 32 inch screens", 89.99, "peripherals", "monitor,stand,office", "ErgoPlus", "arms", "2", "load", "9kg"),
        row("PER-123", "Single Monitor Stand", "Height-adjustable single monitor stand", 39.99, "peripherals", "monitor,stand,office", "ErgoPlus", "arms", "1", "material", "Steel"),
        row("PER-124", "Laptop Cooling Pad", "Five-fan laptop cooling pad with adjustable height", 27.99, "peripherals", "laptop,cooling,office", "ChillPad", "fans", "5", "size", "17in"),
        row("PER-125", "Foldable Laptop Riser", "Foldable aluminium laptop riser for desk ergonomics", 24.99, "peripherals", "laptop,stand,ergonomic", "ErgoPlus", "material", "Aluminium", "foldable", "Yes"),
        row("PER-126", "Wireless Numeric Keypad", "Bluetooth numeric keypad for spreadsheets", 22.99, "peripherals", "keyboard,wireless,office", "KeyPro", "keys", "34", "connection", "Bluetooth"),
        row("PER-127", "Programmable Macro Pad", "12-key programmable macro pad with rotary knob", 46.99, "peripherals", "keyboard,gaming,creative", "KeyPro", "keys", "12", "knob", "Yes"),
        row("PER-128", "Gaming Mouse Pad XL", "Extended XL gaming mouse pad with stitched edges", 18.99, "peripherals", "mousepad,gaming,desk", "VoltEdge", "size", "XL", "thickness", "4mm"),
        row("PER-129", "Leather Desk Mat Large", "Premium leather desk mat with water resistance", 29.99, "peripherals", "desk,office,leather", "TidyUp", "size", "Large", "color", "Brown"),
        row("PER-130", "Memory Foam Wrist Set", "Memory foam keyboard and mouse wrist rest set", 21.99, "peripherals", "ergonomic,office,comfort", "ErgoPlus", "pieces", "2", "color", "Grey"),
        row("PER-131", "USB Condenser Microphone", "Plug-and-play condenser mic for podcasts", 69.99, "peripherals", "microphone,audio,streaming", "CallPro", "pattern", "Cardioid", "connection", "USB"),
        row("PER-132", "Boom Arm Mic Stand", "Adjustable boom arm with cable management", 33.99, "peripherals", "microphone,stand,streaming", "CallPro", "load", "1.5kg", "mount", "Clamp"),
        row("PER-133", "Pop Filter for Microphone", "Dual-layer pop filter with gooseneck clamp", 12.99, "peripherals", "microphone,audio,recording", "CallPro", "diameter", "6in", "layers", "2"),
        row("PER-134", "4K Capture Card", "4K HDMI capture card with ultra-low latency", 129.99, "peripherals", "streaming,video,gaming", "ViewClear", "input", "HDMI", "passthrough", "4K60"),
        row("PER-135", "External SSD Enclosure", "USB-C NVMe SSD enclosure with 10Gbps transfer", 36.99, "peripherals", "storage,usb-c,office", "DockWell", "speed", "10Gbps", "size", "M.2"),
        row("PER-136", "USB Fingerprint Reader", "Windows Hello USB fingerprint reader", 26.99, "peripherals", "security,usb,office", "SecureKey", "interface", "USB-A", "os", "Windows"),
        row("PER-137", "Smart Card Reader", "USB smart card reader for ID and banking", 19.99, "peripherals", "security,usb,office", "SecureKey", "interface", "USB", "cards", "ISO7816"),
        row("PER-138", "Wireless Barcode Scanner", "2D wireless barcode scanner with stand", 89.99, "peripherals", "scanner,wireless,office", "ScanFast", "range", "100m", "mode", "2D"),
        row("PER-139", "Sheetfed Document Scanner", "Fast duplex sheetfed scanner for offices", 199.99, "peripherals", "scanner,office,paper", "ScanFast", "speed", "30ppm", "duplex", "Yes"),
        row("PER-140", "USB Foot Pedal Switch", "Hands-free USB foot pedal for transcription", 31.99, "peripherals", "usb,office,accessory", "KeyPro", "pedals", "1", "interface", "USB"),
        row("PER-141", "Presentation Clicker Pro", "Long-range presentation clicker with pointer", 28.99, "peripherals", "wireless,presenter,office", "PresentPro", "range", "100m", "battery", "AAA"),
        row("PER-142", "KVM Switch 2-Port", "2-port HDMI KVM switch with USB hub", 59.99, "peripherals", "switch,hdmi,office", "DockWell", "ports", "2", "resolution", "4K"),
        row("PER-143", "HDMI Switch 3-Port", "3-port 4K HDMI switch with remote", 24.99, "peripherals", "switch,hdmi,video", "CablePro", "ports", "3", "resolution", "4K60"),
        row("PER-144", "Wireless HDMI Transmitter", "Wireless 1080p HDMI transmitter and receiver kit", 119.99, "peripherals", "hdmi,wireless,video", "ViewClear", "range", "30m", "resolution", "1080p"),
        row("PER-145", "Stylus Pen for Tablet", "Rechargeable active stylus with palm rejection", 25.99, "peripherals", "stylus,tablet,creative", "SketchPro", "battery", "12h", "tips", "3"),
        row("PER-146", "VR Headset Stand", "Aluminium VR headset stand with cable holder", 29.99, "peripherals", "vr,gaming,stand", "VoltEdge", "material", "Aluminium", "color", "Black"),
        row("PER-147", "Controller Charging Dock", "Dual controller charging dock with LED indicators", 27.99, "peripherals", "gaming,charging,dock", "VoltEdge", "slots", "2", "input", "USB-C"),
        row("PER-148", "Gaming Headset Stand", "RGB headset stand with built-in USB hub", 34.99, "peripherals", "gaming,stand,audio", "VoltEdge", "lighting", "RGB", "ports", "2"),
        row("PER-149", "USB LED Strip Backlight", "Bias lighting LED strip for monitors", 15.99, "peripherals", "lighting,usb,gaming", "BrightDesk", "length", "2m", "power", "USB"),
        row("PER-150", "Mini Projector 1080p", "Portable 1080p mini projector with built-in speaker", 189.99, "peripherals", "projector,video,office", "ViewClear", "resolution", "1080p", "speaker", "Built-in"),
        // ----- AUDIO (50): AUD-101..AUD-150 -----
        row("AUD-101", "True Wireless Earbuds Lite", "Lightweight earbuds with ENC calls and 30h case", 39.99, "audio", "earbuds,wireless,audio", "SoundBeat", "battery", "30h", "color", "Black"),
        row("AUD-102", "Sports Ear Hooks", "Sweat-proof sports earbuds with secure ear hooks", 44.99, "audio", "earbuds,sports,audio", "SoundBeat", "waterproof", "IPX5", "battery", "8h"),
        row("AUD-103", "Bone Conduction Headphones", "Open-ear bone conduction headphones for runners", 89.99, "audio", "headphones,sports,audio", "SoundBeat", "battery", "10h", "waterproof", "IPX7"),
        row("AUD-104", "Kids Safe Headphones", "Volume-limited kids headphones with fun colors", 24.99, "audio", "headphones,kids,audio", "SoundBeat", "limit", "85dB", "color", "Pink"),
        row("AUD-105", "DJ Monitor Headphones", "Closed-back DJ headphones with swivel earcups", 119.99, "audio", "headphones,dj,audio", "SoundBeat", "driver", "50mm", "foldable", "Yes"),
        row("AUD-106", "Open-Ear Air Headphones", "Clip-on open-ear headphones for all-day wear", 69.99, "audio", "headphones,wireless,audio", "SoundBeat", "battery", "24h", "color", "White"),
        row("AUD-107", "Neckband Bluetooth Earphones", "Flexible neckband earphones with fast charging", 29.99, "audio", "earphones,wireless,audio", "SoundBeat", "battery", "20h", "charge", "USB-C"),
        row("AUD-108", "Bookshelf Speaker Pair", "Powered bookshelf speakers with bluetooth input", 149.99, "audio", "speaker,audio,home", "BoomBox", "power", "60W", "input", "Bluetooth"),
        row("AUD-109", "Party Speaker with Lights", "Loud party speaker with LED lights and mic input", 129.99, "audio", "speaker,party,audio", "BoomBox", "power", "80W", "lights", "LED"),
        row("AUD-110", "Smart WiFi Speaker", "Voice-assistant smart speaker with room-filling sound", 79.99, "audio", "speaker,smart,audio", "BoomBox", "assistant", "Built-in", "wifi", "Yes"),
        row("AUD-111", "Mini Pocket Speaker", "Pocket-size speaker with 12h playtime", 25.99, "audio", "speaker,portable,audio", "BoomBox", "battery", "12h", "waterproof", "IPX6"),
        row("AUD-112", "Desktop Stereo Speakers", "Compact desktop speakers with volume knob", 45.99, "audio", "speaker,desktop,audio", "BoomBox", "power", "20W", "input", "AUX"),
        row("AUD-113", "Studio Monitor Single", "5 inch powered studio monitor for creators", 139.99, "audio", "speaker,studio,audio", "BoomBox", "size", "5in", "power", "70W"),
        row("AUD-114", "Soundbar with Subwoofer", "2.1 soundbar with wireless subwoofer", 199.99, "audio", "soundbar,audio,tv", "BoomBox", "channels", "2.1", "subwoofer", "Wireless"),
        row("AUD-115", "Portable Karaoke Mic Speaker", "All-in-one karaoke mic with built-in speaker", 59.99, "audio", "karaoke,microphone,audio", "SoundBeat", "battery", "8h", "modes", "3"),
        row("AUD-116", "Lavalier Wireless Mic", "Wireless lapel mic for vlogging and interviews", 79.99, "audio", "microphone,wireless,video", "CallPro", "range", "50m", "battery", "6h"),
        row("AUD-117", "Shotgun Camera Microphone", "On-camera shotgun mic with shock mount", 99.99, "audio", "microphone,camera,audio", "CallPro", "pattern", "Supercardioid", "mount", "Shoe"),
        row("AUD-118", "Conference Speakerphone", "360-degree conference speakerphone with mic array", 109.99, "audio", "conference,audio,office", "CallPro", "mics", "6", "connection", "USB"),
        row("AUD-119", "Call Center Headset Pro", "Noise-cancelling headset with quick disconnect", 74.99, "audio", "headset,office,calls", "CallPro", "mic", "Noise-cancelling", "connection", "QD"),
        row("AUD-120", "Bluetooth Headset Mono", "Single-ear bluetooth headset for drivers", 34.99, "audio", "headset,wireless,calls", "CallPro", "talk", "16h", "color", "Black"),
        row("AUD-121", "Gaming Headset 7.1", "Surround gaming headset with detachable mic", 89.99, "audio", "headset,gaming,audio", "VoltEdge", "sound", "7.1", "mic", "Detachable"),
        row("AUD-122", "Kids Wireless Headset", "Foldable kids headset with mic for online classes", 32.99, "audio", "headset,kids,wireless", "SoundBeat", "battery", "25h", "color", "Blue"),
        row("AUD-123", "Sleep Earbuds Soft", "Ultra-soft sleep earbuds with companion app", 49.99, "audio", "earbuds,sleep,audio", "SoundBeat", "battery", "14h", "app", "Yes"),
        row("AUD-124", "Hearing Protection Earmuffs", "SNR 28dB earmuffs for workshops", 21.99, "audio", "protection,safety,audio", "SafeEar", "rating", "SNR28", "color", "Yellow"),
        row("AUD-125", "USB DAC Headphone Amp", "Portable USB DAC and headphone amplifier", 59.99, "audio", "dac,audio,headphones", "SoundBeat", "output", "3.5mm", "dac", "32-bit"),
        row("AUD-126", "Turntable Belt-Drive", "Belt-drive turntable with bluetooth output", 179.99, "audio", "turntable,music,audio", "RetroTone", "speed", "33/45", "output", "Bluetooth"),
        row("AUD-127", "Vinyl Record Brush Kit", "Anti-static vinyl cleaning kit with stylus brush", 16.99, "audio", "vinyl,music,care", "RetroTone", "pieces", "4", "material", "Carbon"),
        row("AUD-128", "CD Boombox Retro", "Retro boombox with CD bluetooth and FM radio", 89.99, "audio", "boombox,music,retro", "RetroTone", "media", "CD", "radio", "FM"),
        row("AUD-129", "FM Pocket Radio", "Pocket FM radio with rechargeable battery", 22.99, "audio", "radio,portable,audio", "RetroTone", "bands", "FM", "battery", "20h"),
        row("AUD-130", "White Noise Machine", "Sleep sound machine with 20 soothing sounds", 35.99, "audio", "sleep,audio,home", "DreamSound", "sounds", "20", "timer", "Yes"),
        row("AUD-131", "Guitar Clip-On Tuner", "Accurate clip-on tuner for guitar and ukulele", 13.99, "audio", "music,guitar,accessory", "StringLab", "modes", "5", "display", "LCD"),
        row("AUD-132", "Ukulele Soprano Starter", "Soprano ukulele starter kit with bag and tuner", 54.99, "audio", "music,ukulele,instrument", "StringLab", "size", "Soprano", "kit", "Yes"),
        row("AUD-133", "Digital Piano Sustain Pedal", "Universal sustain pedal for keyboards", 19.99, "audio", "music,piano,accessory", "StringLab", "polarity", "Switchable", "cable", "1.8m"),
        row("AUD-134", "Drum Practice Pad", "Quiet drum practice pad with stand mount", 27.99, "audio", "music,drums,practice", "BeatLab", "size", "12in", "mount", "Stand"),
        row("AUD-135", "Podcast Mixer Mini", "4-channel podcast mixer with USB interface", 119.99, "audio", "podcast,mixer,audio", "CallPro", "channels", "4", "interface", "USB"),
        row("AUD-136", "Audio Interface 2i2", "2-in 2-out USB audio interface for recording", 149.99, "audio", "recording,interface,audio", "CallPro", "inputs", "2", "phantom", "48V"),
        row("AUD-137", "XLR Cable 6m", "Balanced XLR microphone cable 6 meters", 18.99, "audio", "cable,xlr,audio", "CablePro", "length", "6m", "balanced", "Yes"),
        row("AUD-138", "Speaker Stand Pair", "Adjustable speaker stands for bookshelf monitors", 64.99, "audio", "speaker,stand,audio", "BoomBox", "pair", "Yes", "height", "Adjustable"),
        row("AUD-139", "Headphone Hanger Clamp", "Under-desk headphone hanger with clamp", 14.99, "audio", "headphones,stand,desk", "TidyUp", "mount", "Clamp", "load", "2kg"),
        row("AUD-140", "Earbud Tips Memory Foam", "Memory foam eartips 3-pair variety pack", 11.99, "audio", "earbuds,accessory,audio", "SoundBeat", "pairs", "3", "sizes", "S/M/L"),
        row("AUD-141", "Car Bluetooth Receiver", "Bluetooth 5.3 car receiver with hands-free calls", 17.99, "audio", "bluetooth,car,audio", "SoundBeat", "version", "5.3", "input", "AUX"),
        row("AUD-142", "TV Wireless Headphones", "Wireless TV headphones with charging dock", 99.99, "audio", "headphones,tv,wireless", "SoundBeat", "range", "30m", "dock", "Yes"),
        row("AUD-143", "Baby Monitor Audio", "Digital audio baby monitor with night light", 42.99, "audio", "baby,monitor,audio", "HomeCare", "range", "300m", "light", "Yes"),
        row("AUD-144", "Doorbell Chime Extender", "Wireless doorbell chime with 58 melodies", 26.99, "audio", "home,doorbell,audio", "HomeCare", "melodies", "58", "range", "150m"),
        row("AUD-145", "Megaphone Handheld", "25W handheld megaphone with siren", 33.99, "audio", "megaphone,outdoor,audio", "LoudPro", "power", "25W", "range", "800m"),
        row("AUD-146", "Voice Recorder Pen", "Discreet voice recorder pen with 32GB storage", 39.99, "audio", "recorder,office,audio", "CallPro", "storage", "32GB", "battery", "18h"),
        row("AUD-147", "Metronome Digital", "Clip-on digital metronome with tap tempo", 24.99, "audio", "music,practice,tool", "BeatLab", "tempo", "30-250", "clip", "Yes"),
        row("AUD-148", "Instrument Cable 3m", "Braided guitar cable with silent plug", 15.99, "audio", "cable,guitar,music", "CablePro", "length", "3m", "plug", "Silent"),
        row("AUD-149", "DJ Controller Stand", "Foldable laptop and controller stand for DJs", 49.99, "audio", "dj,stand,music", "BeatLab", "foldable", "Yes", "load", "10kg"),
        row("AUD-150", "Acoustic Foam Panels 12pk", "12-pack acoustic foam panels for home studios", 37.99, "audio", "studio,acoustic,treatment", "CallPro", "pieces", "12", "size", "12in"),
        // ----- CABLES (50): CAB-101..CAB-150 -----
        row("CAB-101", "USB-C to USB-C Cable 1m", "Durable 100W USB-C to C cable 1 meter", 11.99, "cables", "usb-c,cable,charging", "CablePro", "length", "1m", "power", "100W"),
        row("CAB-102", "USB-C to Lightning Cable", "MFi USB-C to Lightning fast-charge cable", 14.99, "cables", "lightning,cable,charging", "CablePro", "length", "2m", "mfi", "Yes"),
        row("CAB-103", "USB-A to USB-C Cable 3pk", "Three-pack USB-A to C nylon braided cables", 16.99, "cables", "usb-c,cable,charging", "CablePro", "pack", "3", "length", "1.5m"),
        row("CAB-104", "HDMI 2.1 Cable 2m", "8K HDMI 2.1 cable with ethernet support", 22.99, "cables", "hdmi,cable,video", "CablePro", "length", "2m", "version", "2.1"),
        row("CAB-105", "DisplayPort Cable 1.8m", "4K 144Hz DisplayPort 1.4 cable", 17.99, "cables", "displayport,cable,video", "CablePro", "length", "1.8m", "version", "1.4"),
        row("CAB-106", "USB-C to HDMI Adapter", "4K USB-C to HDMI adapter with aluminium shell", 19.99, "cables", "adapter,usb-c,hdmi", "DockWell", "resolution", "4K", "shell", "Aluminium"),
        row("CAB-107", "USB-C to Ethernet Adapter", "Gigabit USB-C ethernet adapter", 21.99, "cables", "adapter,usb-c,network", "DockWell", "speed", "1Gbps", "chipset", "RTL8153"),
        row("CAB-108", "SD Card Reader USB-C", "UHS-II SD and microSD card reader", 18.99, "cables", "reader,sd,usb-c", "DockWell", "speed", "UHS-II", "slots", "2"),
        row("CAB-109", "GaN Charger 65W", "Compact 65W GaN fast charger with 2 ports", 39.99, "cables", "charger,gan,charging", "ChargeIt", "power", "65W", "ports", "2"),
        row("CAB-110", "GaN Charger 100W", "Three-port 100W GaN charger for laptops", 54.99, "cables", "charger,gan,laptop", "ChargeIt", "power", "100W", "ports", "3"),
        row("CAB-111", "Wireless Charger Stand", "3-in-1 wireless charger for phone watch buds", 45.99, "cables", "wireless,charging,stand", "ChargeIt", "devices", "3", "power", "15W"),
        row("CAB-112", "Power Strip 6-Outlet", "Surge-protected 6-outlet strip with USB ports", 27.99, "cables", "power,strip,office", "VoltSafe", "outlets", "6", "usb", "2"),
        row("CAB-113", "Travel Power Adapter", "All-in-one world travel adapter with USB-C", 29.99, "cables", "travel,adapter,power", "VoltSafe", "countries", "150+", "usb", "Yes"),
        row("CAB-114", "USB-C Dock Cable 100W", "Right-angle 100W USB-C extension cable", 13.99, "cables", "usb-c,cable,charging", "CablePro", "length", "0.8m", "angle", "90deg"),
        row("CAB-115", "Coiled USB Keyboard Cable", "Aviator coiled cable for mechanical keyboards", 24.99, "cables", "keyboard,cable,coiled", "CablePro", "length", "1.5m", "connector", "Aviator"),
        row("CAB-116", "Ethernet Cable Cat8 5m", "High-speed Cat8 ethernet cable 5 meters", 19.99, "cables", "ethernet,cable,network", "CablePro", "length", "5m", "cat", "Cat8"),
        row("CAB-117", "Flat Ethernet Cable 15m", "Flat Cat6 cable for under-carpet runs", 21.99, "cables", "ethernet,cable,network", "CablePro", "length", "15m", "flat", "Yes"),
        row("CAB-118", "Fiber Optic Cable LC 10m", "Duplex LC fiber patch cable 10 meters", 16.99, "cables", "fiber,cable,network", "CablePro", "length", "10m", "mode", "OM3"),
        row("CAB-119", "Cable Sleeve Organizer", "Neoprene cable sleeve for desk tidiness", 9.99, "cables", "organizer,cable,desk", "TidyUp", "length", "1.2m", "color", "Black"),
        row("CAB-120", "Velcro Cable Ties 50pk", "Reusable velcro cable ties 50-pack", 8.99, "cables", "organizer,cable,ties", "TidyUp", "pieces", "50", "reusable", "Yes"),
        row("CAB-121", "Magnetic Cable Clips 6pk", "Adhesive magnetic cable clips 6-pack", 10.99, "cables", "organizer,cable,clips", "TidyUp", "pieces", "6", "mount", "Adhesive"),
        row("CAB-122", "Under-Desk Cable Tray", "Metal under-desk cable management tray", 29.99, "cables", "organizer,cable,desk", "TidyUp", "material", "Metal", "mount", "Screws"),
        row("CAB-123", "USB Hub Powered 7-Port", "Powered 7-port USB 3.0 hub with switches", 34.99, "cables", "usb,hub,office", "DockWell", "ports", "7", "powered", "Yes"),
        row("CAB-124", "USB-C Hub with Ethernet", "8-in-1 USB-C hub with gigabit ethernet", 52.99, "cables", "usb-c,hub,office", "DockWell", "ports", "8", "ethernet", "1Gbps"),
        row("CAB-125", "Thunderbolt 4 Cable", "0.8m Thunderbolt 4 cable with 40Gbps speed", 32.99, "cables", "thunderbolt,cable,usb-c", "CablePro", "length", "0.8m", "speed", "40Gbps"),
        row("CAB-126", "USB-C to DisplayPort Cable", "6ft USB-C to DisplayPort 4K cable", 20.99, "cables", "usb-c,displayport,video", "CablePro", "length", "1.8m", "resolution", "4K60"),
        row("CAB-127", "HDMI to VGA Adapter", "HDMI to VGA adapter with audio jack", 12.99, "cables", "adapter,hdmi,vga", "CablePro", "audio", "3.5mm", "resolution", "1080p"),
        row("CAB-128", "VGA Cable 3m", "Gold-plated VGA monitor cable 3 meters", 10.99, "cables", "vga,cable,video", "CablePro", "length", "3m", "plating", "Gold"),
        row("CAB-129", "Optical Audio Cable", "Toslink digital optical audio cable 2 meters", 11.99, "cables", "optical,audio,cable", "CablePro", "length", "2m", "connector", "Toslink"),
        row("CAB-130", "RCA Audio Cable Kit", "Stereo RCA cables with ground wire", 9.99, "cables", "rca,audio,cable", "CablePro", "length", "1.5m", "channels", "2"),
        row("CAB-131", "Extension Cord 10m", "Heavy-duty 10m extension cord with switch", 24.99, "cables", "power,extension,office", "VoltSafe", "length", "10m", "rating", "13A"),
        row("CAB-132", "Smart Plug 4pk", "WiFi smart plugs with energy monitoring 4-pack", 34.99, "cables", "smart,plug,home", "VoltSafe", "pack", "4", "wifi", "Yes"),
        row("CAB-133", "USB Wall Charger 4-Port", "40W 4-port USB wall charger", 22.99, "cables", "charger,usb,charging", "ChargeIt", "ports", "4", "power", "40W"),
        row("CAB-134", "Car Charger Dual USB", "38W dual-port car charger with display", 13.99, "cables", "charger,car,charging", "ChargeIt", "power", "38W", "display", "LED"),
        row("CAB-135", "Solar Power Bank 20000", "Solar 20000mAh power bank with flashlight", 36.99, "cables", "powerbank,solar,travel", "ChargeIt", "capacity", "20000mAh", "solar", "Yes"),
        row("CAB-136", "Power Bank 10000 Slim", "Slim 10000mAh bank with 22.5W fast charge", 25.99, "cables", "powerbank,travel,charging", "ChargeIt", "capacity", "10000mAh", "fast", "22.5W"),
        row("CAB-137", "Laptop Charger 90W Universal", "Universal 90W laptop charger with 16 tips", 42.99, "cables", "charger,laptop,universal", "ChargeIt", "power", "90W", "tips", "16"),
        row("CAB-138", "MagSafe Phone Mount Charger", "Magnetic car mount with 15W charging", 31.99, "cables", "charger,car,magnetic", "ChargeIt", "power", "15W", "mount", "Vent"),
        row("CAB-139", "Cable Tester Network", "Network cable tester for RJ45 and coax", 23.99, "cables", "tester,network,cable", "CablePro", "tests", "RJ45+Coax", "display", "LED"),
        row("CAB-140", "Network Switch 8-Port", "8-port gigabit desktop switch fanless", 29.99, "cables", "network,switch,office", "NetGear", "ports", "8", "speed", "1Gbps"),
        row("CAB-141", "WiFi Range Extender", "Dual-band WiFi extender with ethernet port", 35.99, "cables", "wifi,network,home", "NetGear", "bands", "Dual", "speed", "1200Mbps"),
        row("CAB-142", "Patch Panel 12-Port", "12-port Cat6 patch panel for racks", 44.99, "cables", "network,patch,rack", "NetGear", "ports", "12", "cat", "Cat6"),
        row("CAB-143", "Keystone Jack Kit 10pk", "Cat6 tool-less keystone jacks 10-pack", 18.99, "cables", "network,jack,cable", "NetGear", "pieces", "10", "cat", "Cat6"),
        row("CAB-144", "HDMI Wall Plate Kit", "Dual HDMI wall plate with 10m cable", 27.99, "cables", "hdmi,wall,video", "CablePro", "plates", "2", "cable", "10m"),
        row("CAB-145", "USB-C Magnetic Adapter", "Magnetic breakaway USB-C adapter 2-pack", 15.99, "cables", "usb-c,magnetic,adapter", "CablePro", "pack", "2", "power", "100W"),
        row("CAB-146", "Right-Angle HDMI Adapter 2pk", "4K right-angle HDMI adapters 2-pack", 9.99, "cables", "hdmi,adapter,video", "CablePro", "pack", "2", "angle", "90deg"),
        row("CAB-147", "SIM Ejector and Adapter Kit", "SIM tool kit with nano and micro adapters", 6.99, "cables", "sim,adapter,phone", "CablePro", "pieces", "8", "tool", "Yes"),
        row("CAB-148", "Phone Lanyard Tether 2pk", "Crossbody phone lanyard with patches 2-pack", 12.99, "cables", "phone,lanyard,accessory", "TidyUp", "pack", "2", "length", "Adjustable"),
        row("CAB-149", "Cable Label Clips 32pk", "Color-coded cable labels 32-pack", 7.99, "cables", "organizer,cable,labels", "TidyUp", "pieces", "32", "colors", "8"),
        row("CAB-150", "Surge Protector Tower", "12-outlet tower surge protector with USB-C", 39.99, "cables", "power,surge,office", "VoltSafe", "outlets", "12", "usb-c", "Yes"),
        // ----- OFFICE (50): OFF-101..OFF-150 -----
        row("OFF-101", "Architect Desk Lamp", "Swing-arm architect lamp with USB charging port", 46.99, "office", "lamp,office,desk", "BrightDesk", "arm", "Swing", "usb", "Yes"),
        row("OFF-102", "Clip-On Reading Light", "Rechargeable clip reading light with 3 colors", 14.99, "office", "lamp,reading,light", "BrightDesk", "modes", "3", "battery", "USB"),
        row("OFF-103", "Motion Sensor Night Light 3pk", "Motion sensor night lights 3-pack", 16.99, "office", "light,night,home", "BrightDesk", "pack", "3", "sensor", "Motion"),
        row("OFF-104", "Standing Desk Converter", "Height-adjustable standing desk converter", 149.99, "office", "desk,standing,ergonomic", "ErgoPlus", "load", "15kg", "levels", "Adjustable"),
        row("OFF-105", "Footrest Under Desk", "Adjustable memory foam under-desk footrest", 32.99, "office", "footrest,ergonomic,office", "ErgoPlus", "material", "Foam", "angle", "Adjustable"),
        row("OFF-106", "Balance Board for Desk", "Wobble balance board for standing desks", 49.99, "office", "standing,fitness,office", "ErgoPlus", "load", "120kg", "surface", "Anti-slip"),
        row("OFF-107", "Acoustic Desk Divider", "Sound-absorbing desk privacy panel", 59.99, "office", "desk,privacy,office", "TidyUp", "material", "Felt", "size", "60in"),
        row("OFF-108", "Monitor Memo Board", "Side-mount memo board for monitors", 12.99, "office", "memo,monitor,office", "PaperMate", "mount", "Clip", "pack", "2"),
        row("OFF-109", "Whiteboard 24x36in", "Magnetic whiteboard with markers and eraser", 39.99, "office", "whiteboard,office,memo", "PaperMate", "size", "24x36", "magnetic", "Yes"),
        row("OFF-110", "Cork Board with Frame", "Framed cork board for pins and notes", 24.99, "office", "corkboard,office,memo", "PaperMate", "size", "24x18", "frame", "Wood"),
        row("OFF-111", "Sticky Notes Bulk 12pk", "12-pad sticky notes in assorted colors", 11.99, "office", "notes,stationery,office", "PaperMate", "pads", "12", "colors", "Assorted"),
        row("OFF-112", "Ballpoint Pens 20pk", "Smooth black ballpoint pens 20-pack", 13.99, "office", "pens,stationery,office", "PaperMate", "pieces", "20", "color", "Black"),
        row("OFF-113", "Highlighters Pastel 6pk", "Pastel highlighters chisel tip 6-pack", 8.99, "office", "highlighters,stationery,office", "PaperMate", "pieces", "6", "tip", "Chisel"),
        row("OFF-114", "Mechanical Pencils 6pk", "0.5mm mechanical pencils with refills", 10.99, "office", "pencils,stationery,office", "PaperMate", "pieces", "6", "lead", "0.5mm"),
        row("OFF-115", "Binder Clips Assorted 30pk", "Assorted binder clips 30-pack", 7.99, "office", "clips,stationery,office", "TidyUp", "pieces", "30", "sizes", "Assorted"),
        row("OFF-116", "Heavy-Duty Stapler", "100-sheet heavy-duty metal stapler", 26.99, "office", "stapler,office,stationery", "TidyUp", "sheets", "100", "material", "Metal"),
        row("OFF-117", "Electric Pencil Sharpener", "Quiet electric sharpener with auto-stop", 21.99, "office", "sharpener,office,stationery", "TidyUp", "power", "USB", "auto-stop", "Yes"),
        row("OFF-118", "Paper Shredder 8-Sheet", "Crosscut 8-sheet shredder with basket", 69.99, "office", "shredder,office,paper", "TidyUp", "sheets", "8", "cut", "Crosscut"),
        row("OFF-119", "Laminator A4", "Fast warm-up A4 laminator with pouches", 44.99, "office", "laminator,office,paper", "TidyUp", "size", "A4", "warmup", "3min"),
        row("OFF-120", "Filing Organizer 5-Tier", "5-tier desktop mesh file organizer", 33.99, "office", "organizer,files,office", "TidyUp", "tiers", "5", "material", "Mesh"),
        row("OFF-121", "Expanding File Folder", "13-pocket expanding file with labels", 12.99, "office", "files,organizer,office", "PaperMate", "pockets", "13", "size", "A4"),
        row("OFF-122", "Label Maker Portable", "Portable label maker with tapes included", 36.99, "office", "labels,office,organizer", "PaperMate", "tapes", "Included", "display", "LCD"),
        row("OFF-123", "Tape Dispenser Weighted", "Weighted tape dispenser with 2 rolls", 15.99, "office", "tape,office,desk", "TidyUp", "rolls", "2", "weighted", "Yes"),
        row("OFF-124", "Scissors Titanium 2pk", "Titanium scissors 2-pack extra sharp", 11.99, "office", "scissors,office,stationery", "TidyUp", "pack", "2", "blade", "Titanium"),
        row("OFF-125", "Desk Drawer Organizer", "Bamboo drawer organizer with 6 compartments", 22.99, "office", "organizer,desk,office", "TidyUp", "compartments", "6", "material", "Bamboo"),
        row("OFF-126", "Pen Holder Mesh", "Rotating mesh pen holder for desks", 9.99, "office", "holder,desk,office", "TidyUp", "rotating", "Yes", "material", "Mesh"),
        row("OFF-127", "Bookends Metal Pair", "Non-slip metal bookends pair", 16.99, "office", "bookends,desk,office", "TidyUp", "pair", "Yes", "material", "Metal"),
        row("OFF-128", "Laptop Privacy Screen 14in", "14 inch laptop privacy filter", 31.99, "office", "privacy,laptop,office", "ViewClear", "size", "14in", "filter", "Privacy"),
        row("OFF-129", "Blue Light Glasses", "Blue-light blocking glasses with case", 19.99, "office", "glasses,office,health", "EyeCare", "filter", "Blue-light", "case", "Yes"),
        row("OFF-130", "Seat Cushion Gel", "Gel seat cushion for long sitting hours", 34.99, "office", "cushion,ergonomic,office", "ErgoPlus", "material", "Gel", "cover", "Washable"),
        row("OFF-131", "Lumbar Support Pillow", "Adjustable lumbar pillow with straps", 27.99, "office", "lumbar,ergonomic,office", "ErgoPlus", "straps", "Yes", "material", "Memory foam"),
        row("OFF-132", "Desk Exercise Bike Pedals", "Under-desk pedal exerciser with display", 79.99, "office", "fitness,desk,office", "FitDesk", "display", "LCD", "resistance", "Adjustable"),
        row("OFF-133", "Resistance Bands Set", "5-band resistance set with handles", 24.99, "office", "fitness,exercise,office", "FitDesk", "bands", "5", "handles", "Yes"),
        row("OFF-134", "Insulated Water Bottle 1L", "Vacuum insulated bottle keeps drinks cold 24h", 21.99, "office", "bottle,desk,hydration", "HydroPlus", "capacity", "1L", "insulation", "Vacuum"),
        row("OFF-135", "Electric Kettle 1.7L", "Fast-boil 1.7L electric kettle", 38.99, "office", "kettle,office,kitchen", "BrewWell", "capacity", "1.7L", "auto-off", "Yes"),
        row("OFF-136", "Desk Coffee Warmer", "USB coffee warmer with auto shut-off", 16.99, "office", "coffee,desk,warmer", "BrewWell", "power", "USB", "shutoff", "Auto"),
        row("OFF-137", "Mini Fridge 6-Can", "6-can mini fridge for desk and skincare", 46.99, "office", "fridge,desk,office", "ChillBox", "cans", "6", "power", "AC+DC"),
        row("OFF-138", "Air Purifier Desktop", "HEPA desktop air purifier runs quiet", 59.99, "office", "purifier,desk,health", "PureAir", "filter", "HEPA", "noise", "25dB"),
        row("OFF-139", "Desk Fan USB Quiet", "Quiet USB desk fan with 3 speeds", 18.99, "office", "fan,desk,cooling", "ChillPad", "speeds", "3", "power", "USB"),
        row("OFF-140", "Space Heater Mini", "Ceramic mini heater with thermostat", 34.99, "office", "heater,office,warmth", "WarmDesk", "power", "800W", "thermostat", "Yes"),
        row("OFF-141", "Wall Clock Silent 12in", "Silent sweep 12 inch wall clock", 19.99, "office", "clock,office,wall", "TimeWell", "size", "12in", "movement", "Silent"),
        row("OFF-142", "Desk Calendar 2026", "Large 2026 desk calendar with stickers", 10.99, "office", "calendar,office,planner", "PaperMate", "year", "2026", "stickers", "Yes"),
        row("OFF-143", "Weekly Planner Pad", "52-sheet weekly desk planner pad", 12.99, "office", "planner,office,desk", "PaperMate", "sheets", "52", "size", "A4"),
        row("OFF-144", "Key Lock Box Wall", "Wall-mount key lock box with code", 25.99, "office", "lockbox,keys,office", "SecureKey", "code", "4-digit", "mount", "Wall"),
        row("OFF-145", "Fingerprint Padlock", "USB rechargeable fingerprint padlock", 29.99, "office", "lock,security,fingerprint", "SecureKey", "prints", "10", "battery", "USB"),
        row("OFF-146", "Cable Lock for Laptop", "6ft laptop cable lock with 2 keys", 21.99, "office", "lock,laptop,security", "SecureKey", "length", "6ft", "keys", "2"),
        row("OFF-147", "Fireproof Document Bag", "Fireproof bag for documents and cash", 24.99, "office", "safe,documents,fireproof", "SecureKey", "rating", "1000F", "size", "A4"),
        row("OFF-148", "Desk Grommet Power Hub", "Round desk grommet with 2 outlets and USB", 35.99, "office", "power,desk,grommet", "VoltSafe", "outlets", "2", "usb", "Yes"),
        row("OFF-149", "Rolling Cart 3-Tier", "3-tier rolling storage cart with locking wheels", 39.99, "office", "cart,storage,office", "TidyUp", "tiers", "3", "wheels", "Locking"),
        row("OFF-150", "Office Chair Mat", "48x36 chair mat for hard floors", 44.99, "office", "chairmat,floor,office", "ErgoPlus", "size", "48x36", "floor", "Hard"));
    List<Product> out = new ArrayList<>(rows.size());
    for (Object[] r : rows) {
      out.add(p((String) r[0], (String) r[1], (String) r[2], ((Number) r[3]).doubleValue(),
          (String) r[4], (String) r[5], (String) r[6],
          (String) r[7], String.valueOf(r[8]), (String) r[9], String.valueOf(r[10])));
    }
    return out;
  }

  private static Object[] row(String sku, String title, String desc, double price, String category,
      String tagsCsv, String brand, String attrK1, String attrV1, String attrK2, String attrV2) {
    return new Object[]{sku, title, desc, price, category, tagsCsv, brand, attrK1, attrV1, attrK2, attrV2};
  }

  private static Product p(String sku, String title, String desc, double price, String category,
      String tagsCsv, String brand, String attrK1, String attrV1, String attrK2, String attrV2) {
    Product doc = new Product();
    doc.setSku(sku);
    doc.setTitle(title);
    doc.setDescription(desc);
    doc.setPrice(price);
    doc.setCategory(category);
    doc.setTags(new ArrayList<>(Arrays.asList(tagsCsv.split(","))));
    Map<String, Object> attrs = new LinkedHashMap<>();
    attrs.put("brand", brand);
    attrs.put(attrK1, attrV1);
    attrs.put(attrK2, attrV2);
    doc.setAttributes(attrs);
    Product.Variant v = new Product.Variant();
    v.setName("Standard");
    v.setSku(sku + "-STD");
    v.setPriceAdjustment(0.0);
    List<Product.Variant> vs = new ArrayList<>();
    vs.add(v);
    doc.setVariants(vs);
    doc.setActive(true);
    doc.setUpdatedAt(Instant.now());
    return doc;
  }
}
