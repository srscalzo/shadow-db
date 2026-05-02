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
    ├── V2__add_orders.sql
    └── V3__add_users.sql   ← treated as the new migration (highest version)
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

**Example output:**

```
Detected new migration: V3__add_users.sql
Existing migrations:    2

Connecting to jdbc:mysql://localhost:3306 ...
Connected.

Shadow DB Migration Preview
============================
New migration: V3__add_users.sql

TABLES
  + users

COLUMNS in 'users'
  + id            INT NOT NULL
  + username      VARCHAR(100) NOT NULL
  + email         VARCHAR(255) NOT NULL
  + created_at    DATETIME NOT NULL

INDEXES in 'users'
  + PRIMARY (id)
  + uk_users_email (email) UNIQUE

No tables removed.
No columns removed or modified.
No indexes removed.
```

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
