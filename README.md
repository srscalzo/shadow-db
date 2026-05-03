# shadowdb

Preview Flyway database migration changes before they are applied.

`shadowdb diff` connects to your existing MySQL instance, creates two temporary shadow databases, applies your migrations to each, and diffs the schemas — showing exactly what tables, columns, and indexes will change.

---

## Requirements

- Java 17+
- A running MySQL instance (local, Docker, or remote)

---

## Installation

1. Download `shadow-db-<version>-dist.zip` from the [Releases](../../releases) page
2. Unzip it anywhere:
   ```
   shadowdb-1.0.0/
   ├── shadow-db-1.0.0.jar
   ├── shadowdb          (Mac/Linux)
   └── shadowdb.bat      (Windows)
   ```
3. Add the folder to your PATH:

   **Mac/Linux** — add to `~/.bashrc` or `~/.zshrc`:
   ```bash
   export PATH="$PATH:/path/to/shadowdb-1.0.0"
   ```

   **Windows** — add the folder via System Properties → Environment Variables → Path

---

## Usage

Navigate to your Flyway project root and run:

```bash
shadowdb diff --db-url jdbc:mysql://localhost:3306 --db-user root --db-password secret
```

shadowdb assumes the standard Flyway migrations layout:
```
your-project/
└── src/main/resources/db/migration/
    ├── V1__create_products.sql
    ├── V2__create_customers.sql
    ├── V3__create_orders.sql
    └── V4__add_reviews.sql   ← treated as the new migration (highest version)
```

### Options

| Option | Default | Description |
|--------|---------|-------------|
| `--db-url` | `jdbc:mysql://localhost:3306` | JDBC base URL (no database name) |
| `--db-user` | `root` | Database username |
| `--db-password` | _(empty)_ | Database password |

Options can also be set via environment variables:

```bash
export SHADOWDB_DB_URL=jdbc:mysql://localhost:3306
export SHADOWDB_DB_USER=root
export SHADOWDB_DB_PASSWORD=secret
shadowdb diff
```

> **Note:** Flyway migration progress logs (`INFO: Migrating schema...`) are printed during the run. These are informational — the diff report always appears at the end.

**Example output:**

```
Detected new migration: V4__add_reviews.sql
Existing migrations:    3

Connecting to jdbc:mysql://localhost:3306 ...
Connected.

Shadow DB Migration Preview
============================
New migration: V4__add_reviews.sql

TABLES
  + reviews  (added)

COLUMNS in 'reviews'
  + id                   INT NOT NULL
  + product_id           INT NOT NULL
  + customer_id          INT NOT NULL
  + rating               TINYINT NOT NULL
  + title                VARCHAR(255)
  + body                 TEXT(65535)
  + created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP

INDEXES in 'reviews'
  + idx_reviews_customer (customer_id)
  + idx_reviews_product (product_id)
  + PRIMARY (id) UNIQUE

No column changes.

No index changes.
```

Additions are highlighted in green, removals in red, and modifications in yellow in a terminal that supports color.

---

## Exit Codes

| Code | Meaning |
|------|---------|
| `0`  | No schema changes detected |
| `1`  | Schema changes detected |
| `2`  | Error (connection failed, migrations folder not found, etc.) |

Useful in CI pipelines:
```bash
shadowdb diff || echo "Schema changes detected — review before merging"
```

---

## How It Works

shadowdb runs between writing a migration and deploying it:

```
Shadow DB 1  ──  all existing migrations         (current schema)
Shadow DB 2  ──  existing + new migration         (proposed schema)
                           ↓
                     diff the two schemas
                           ↓
                     print the report
```

Both shadow databases are created fresh on your MySQL instance and dropped automatically when the diff completes. The MySQL user must have `CREATE DATABASE` and `DROP DATABASE` privileges.
