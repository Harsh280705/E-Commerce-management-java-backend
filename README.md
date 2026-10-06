# Level 3 E-Commerce — Java Backend (Spring Boot port of Level 2)

**PostgreSQL** (Level 3 transactional data) · **MongoDB** (Level 3 product catalog) ·
**Elasticsearch** (Level 3 admin search projection) · **RabbitMQ** (Level 3 sync transport) ·
**Spring Boot 3 / Java 17** API · **Vue SPA** (same UI as Level 2, pointed at the Java API)

Level 2 (`Level 2 - E-Commerce`) is used as a **reference only** and is never
modified, connected to, or reused by this project.

## Run (one command)

```powershell
docker compose up -d --build
```

This starts isolated Level 3 containers only: `level3-postgres`, `level3-mongodb`,
`level3-rabbitmq`, `level3-elasticsearch`, `level3-java-api`, `level3-nginx`.
Demo data is seeded automatically on the first start (idempotent — skipped on restarts).

Fresh reset (Level 3 volumes only — Level 2 is a different compose project and is untouched):

```powershell
docker compose down -v
docker compose up -d --build
```

## URLs

- **Storefront:** http://localhost:8082
- **Checkout:** http://localhost:8082/checkout
- **My Orders:** http://localhost:8082/orders
- **Admin Search:** http://localhost:8082/admin
- **Catalog Admin:** http://localhost:8082/catalog
- **Java API:** http://localhost:8003
- **Swagger UI:** http://localhost:8003/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8003/v3/api-docs
- **Elasticsearch:** http://localhost:9211
- **RabbitMQ UI:** http://localhost:15773 (guest/guest)

## API (compatible with Level 2)

| Method | Path | Notes |
|---|---|---|
| GET | /api/health | `{status: ok}` |
| GET | /api/users | seeded users |
| GET | /api/products[?all] | MongoDB catalog (active only unless `all=true`) |
| GET | /api/products/{id} | 404 when missing |
| POST | /api/products | 201 / 409 duplicate SKU / 422 validation |
| PUT | /api/products/{id} | 404 / 409 (catalog only — history untouched) |
| POST | /api/orders | 201 / 404 user-or-products / 422 inactive-or-duplicate |
| GET | /api/orders[?user_id&status&page&size] | PostgreSQL, newest first |
| GET | /api/orders/{id} | canonical PG details, 404 when missing |
| PATCH | /api/orders/{id}/status | PENDING/PROCESSING/SHIPPED, 422 otherwise |
| GET | /api/orders/{id}/sync | PG vs ES sync comparison |
| POST | /api/search/orders | ES admin search, 422 bad filters, 503 ES down |
| POST | /api/admin/reindex | rebuild Level 3 ES index from Level 3 PG |

Validation failures return `422` with a `{detail}` body (same as Level 2 FastAPI).

## Data flow

```text
MongoDB (ecom_level3.products)  -> Product Catalog + checkout validation (snapshot at order time)
PostgreSQL (level3_users/orders/items) -> Orders & Users (source of truth)
PostgreSQL -> RabbitMQ (level3_order_sync) -> Java listener -> Elasticsearch (level3_orders)
Elasticsearch -> Admin Search
Scheduler (~15s poll, 180s lookback) -> bulk safety-net sync
```

Order items carry the title + unit-price snapshot; catalog renames never rewrite history.

## Seed & reindex (Java only, Level 3 databases only)

- Seed runs in-app when `RUN_SEED_ON_START=true` (set for the docker API service):
  8 users + 42 orders (14/14/14 by status) in PG, 26 products (25 active + 1 inactive)
  in MongoDB, ES rebuilt from PG, then the snapshot test renames
  `Wireless Mouse` -> `Wireless Mouse Pro` (catalog only).
- Manual reindex (never touches Level 2, never duplicates business orders):

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8003/api/admin/reindex"
```

## Tests

```powershell
cd backend
mvn test      # 19 tests: H2 + mocks, no Docker needed
cd ..\frontend
npm install
npm run build
```

## Configuration

See `.env.example` (Level 3 names/ports/index/queue only, no secrets).

