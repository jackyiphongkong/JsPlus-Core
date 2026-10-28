-- ============================================================================
-- JsPlus-Core database initialization script (English-annotated)
-- Main DB: testdb (jdbc:h2:tcp://localhost:{H2_PORT}/testdb)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Application DB account (created if not exists)
-- sample_user / sample_password are the shipped sample credentials.
-- ----------------------------------------------------------------------------
CREATE USER IF NOT EXISTS sample_user PASSWORD 'sample_password';

-- Grant all privileges on PUBLIC schema to sample_user
GRANT ALL ON SCHEMA PUBLIC TO sample_user;
-- Promote sample_user to ADMIN
ALTER USER sample_user ADMIN TRUE;

-- ============================================================================
-- Table 1: ghost_remote_command (ghost remote command queue)
-- Purpose: holds remote command rows for ghost consoles (headless runtimes),
--          used for cross-process / remote control.
-- NOTE: IF NOT EXISTS on purpose. This table belongs to the runtime
--       (GhostConsole); tests must NEVER drop/recreate it (a running
--       GhostConsole would hit "table not found" mid-run). init only
--       ensures it exists.
-- ============================================================================
CREATE TABLE IF NOT EXISTS ghost_remote_command (
    id CHAR(16) PRIMARY KEY,        -- PK: unique command record id
    ghost_name VARCHAR(200),        -- Ghost name: which ghost console owns the command
    request_script VARCHAR(500),    -- Script to be executed by the ghost
    response_message VARCHAR(500),  -- Text returned by the ghost after execution
    processed INT,                  -- Processed flag: 0=pending, 1=processed
    requested INT,                  -- Request counter
    responsed INT,                  -- Response counter
    created_at TIMESTAMP,           -- Creation time
    updated_at TIMESTAMP,           -- Last update time
    deleted_at TIMESTAMP,           -- Soft-delete time (NULL when active)
    deleted SMALLINT                -- Soft-delete flag: 0=active, 1=deleted
);

-- ============================================================================
-- Table 2: test_user (generic user test table)
-- Purpose: conventional test table for CRUD / user-oriented test scenarios.
-- ============================================================================
CREATE TABLE IF NOT EXISTS test_user (
    id              CHAR(16) PRIMARY KEY,  -- PK: unique user record id
    username        VARCHAR(200),          -- Login name
    email           VARCHAR(300),          -- E-mail address
    age             INT,                   -- Age (nullable)
    status          VARCHAR(20),           -- Status: active / disabled / pending
    created_at      TIMESTAMP,             -- Creation time
    updated_at      TIMESTAMP,             -- Last update time
    deleted_at      TIMESTAMP,             -- Soft-delete time (NULL when active)
    deleted         BOOLEAN                -- Soft-delete flag: false=active, true=deleted
);
CREATE INDEX IF NOT EXISTS idx_tu_username ON test_user (username);
CREATE INDEX IF NOT EXISTS idx_tu_status   ON test_user (status);
CREATE INDEX IF NOT EXISTS idx_tu_deleted  ON test_user (deleted);

-- ============================================================================
-- Table 3: test_product (generic product test table)
-- Purpose: conventional test table for product / inventory-oriented tests.
-- ============================================================================
CREATE TABLE IF NOT EXISTS test_product (
    id              CHAR(16) PRIMARY KEY,  -- PK: unique product record id
    product_name    VARCHAR(200),          -- Product display name
    category        VARCHAR(100),          -- Category (nullable)
    price           DECIMAL(18,2),         -- Unit price (2 decimal places)
    stock           INT,                   -- Stock quantity
    created_at      TIMESTAMP,             -- Creation time
    updated_at      TIMESTAMP,             -- Last update time
    deleted_at      TIMESTAMP,             -- Soft-delete time (NULL when active)
    deleted         BOOLEAN                -- Soft-delete flag: false=active, true=deleted
);
CREATE INDEX IF NOT EXISTS idx_tp_name    ON test_product (product_name);
CREATE INDEX IF NOT EXISTS idx_tp_deleted ON test_product (deleted);

-- ============================================================================
-- Table 4: test_order (generic order test table)
-- Purpose: conventional test table for order / transaction-oriented tests.
-- Logical FK: user_id -> test_user.id, product_id -> test_product.id
-- (no physical FK constraint).
-- ============================================================================
CREATE TABLE IF NOT EXISTS test_order (
    id              CHAR(16) PRIMARY KEY,  -- PK: unique order record id
    order_no        VARCHAR(50),           -- Business order number
    user_id         CHAR(16),              -- FK (logical): owning test_user.id
    product_id      CHAR(16),              -- FK (logical): owning test_product.id
    quantity        INT,                   -- Purchased quantity
    amount          DECIMAL(18,2),         -- Order total amount (2 decimal places)
    status          VARCHAR(20),           -- Order status: pending / paid / shipped / done / cancelled
    created_at      TIMESTAMP,             -- Creation time
    updated_at      TIMESTAMP,             -- Last update time
    deleted_at      TIMESTAMP,             -- Soft-delete time (NULL when active)
    deleted         BOOLEAN                -- Soft-delete flag: false=active, true=deleted
);
CREATE INDEX IF NOT EXISTS idx_to_order_no ON test_order (order_no);
CREATE INDEX IF NOT EXISTS idx_to_user_id  ON test_order (user_id);
CREATE INDEX IF NOT EXISTS idx_to_status   ON test_order (status);
CREATE INDEX IF NOT EXISTS idx_to_deleted  ON test_order (deleted);

-- ============================================================================
-- Table 5: test_log (generic log test table)
-- Purpose: conventional test table for logging / event-oriented tests.
-- ============================================================================
CREATE TABLE IF NOT EXISTS test_log (
    id              CHAR(16) PRIMARY KEY,  -- PK: unique log record id
    level           VARCHAR(10),           -- Log level: DEBUG / INFO / WARN / ERROR
    source          VARCHAR(200),          -- Log source (class / script / component)
    message         VARCHAR(2000),         -- Log message body
    log_time        TIMESTAMP,             -- Log occurrence time
    created_at      TIMESTAMP,             -- Creation time (same as log_time)
    updated_at      TIMESTAMP,             -- Last update time
    deleted_at      TIMESTAMP,             -- Soft-delete time (NULL when active)
    deleted         BOOLEAN                -- Soft-delete flag: false=active, true=deleted
);
CREATE INDEX IF NOT EXISTS idx_tl_level ON test_log (level);
CREATE INDEX IF NOT EXISTS idx_tl_time  ON test_log (log_time);
CREATE INDEX IF NOT EXISTS idx_tl_deleted ON test_log (deleted);

-- ============================================================================
-- Table 6: test_kv (generic key-value test table)
-- Purpose: conventional test table for config / dictionary / cache-like tests.
-- kv_key is unique while active; a soft-deleted row may reuse the key.
-- ============================================================================
CREATE TABLE IF NOT EXISTS test_kv (
    id              CHAR(16) PRIMARY KEY,  -- PK: unique record id
    kv_key          VARCHAR(200),          -- Configuration key
    kv_value        VARCHAR(2000),         -- Configuration value
    remark          VARCHAR(500),          -- Remark / description (nullable)
    created_at      TIMESTAMP,             -- Creation time
    updated_at      TIMESTAMP,             -- Last update time
    deleted_at      TIMESTAMP,             -- Soft-delete time (NULL when active)
    deleted         BOOLEAN                -- Soft-delete flag: false=active, true=deleted
);
CREATE INDEX IF NOT EXISTS idx_tk_key    ON test_kv (kv_key);
CREATE INDEX IF NOT EXISTS idx_tk_deleted ON test_kv (deleted);

-- ============================================================================
-- Table 7: remote_file (RemoteFile file metadata)
-- Purpose: DB-backed remote file system metadata rows. Each file part is one
--          row; a single-part file has one row (file_part_index=0), a
--          multi-part file has file_part_index=0..file_part_last_index.
--          This is an H2 port of the reference MySQL DDL
--          (gen_mysql_rf_script.sh): same columns/types; engine/partition
--          clauses and index length prefixes dropped (H2).
-- Owner:   TestRemoteFile.js exercises this table; the RemoteFile engine
--          (com.killcoding.file.RemoteFile) reads/writes it at runtime.
-- ============================================================================
CREATE TABLE IF NOT EXISTS remote_file (
    id                      CHAR(16) PRIMARY KEY,  -- PK: unique part record id
    file_id                 CHAR(16),              -- Logical file id (shared by all parts)
    file_name               VARCHAR(200),          -- File name
    file_parent_path        VARCHAR(200),          -- Parent directory path (forward-slash)
    file_type               CHAR(1),               -- F=file, D=directory, L=link
    file_part_index         INT,                   -- Part ordinal (0-based)
    file_part_size          BIGINT,                -- Size of this part
    file_part_data_ds       VARCHAR(100),          -- Datasource that stores the part BLOB
    file_part_data_table    INT,                   -- Data table index (0/1 -> remote_file_data_0/1)
    file_part_last_index    INT,                   -- -1=pending; last part index once completed
    file_size               BIGINT,                -- Total file size (all parts)
    file_modify_time        TIMESTAMP,             -- Last modification time
    file_deleted            TINYINT,               -- Hard-delete flag: 0=active, 1=deleted
    source_hostname         VARCHAR(50),           -- Host that wrote the row
    synced_on_hostnames     VARCHAR(200),          -- Hosts that synced the row
    deleted_on_hostnames    VARCHAR(200),          -- Hosts that deleted the row
    created_user_id         CHAR(16),              -- Creator user id
    updated_user_id         CHAR(16),              -- Last updater user id
    deleted_user_id         CHAR(16),              -- Deleter user id
    created_at              TIMESTAMP,             -- Creation time
    updated_at              TIMESTAMP,             -- Last update time
    deleted_at              TIMESTAMP,             -- Soft-delete time (NULL when active)
    deleted                 TINYINT                -- Soft-delete flag: 0=active, 1=deleted
);
CREATE INDEX IF NOT EXISTS idx_rf_0 ON remote_file (file_parent_path);
CREATE INDEX IF NOT EXISTS idx_rf_1 ON remote_file (file_name);
CREATE INDEX IF NOT EXISTS idx_rf_4 ON remote_file (deleted);
CREATE INDEX IF NOT EXISTS idx_rf_5 ON remote_file (file_deleted);
CREATE INDEX IF NOT EXISTS idx_rf_6 ON remote_file (file_id);
CREATE INDEX IF NOT EXISTS idx_rf_7 ON remote_file (file_part_index);
CREATE INDEX IF NOT EXISTS idx_rf_8 ON remote_file (file_type);

-- ============================================================================
-- Table 8/9: remote_file_data_0 / remote_file_data_1 (RemoteFile part BLOBs)
-- Purpose: chunked content tables. DataMapping[0].TableRange=0-1 maps each
--          file part to remote_file_data_<n>; remote_file.file_part_data_table
--          records which table holds the BLOB. Encrypted when the writer
--          config sets EncryptEnable=true.
-- ============================================================================
CREATE TABLE IF NOT EXISTS remote_file_data_0 (
    id                  CHAR(16) PRIMARY KEY,  -- PK: unique chunk record id
    remote_file_id      CHAR(16),              -- FK (logical): remote_file.id
    file_id             CHAR(16),              -- Logical file id (matches remote_file.file_id)
    file_part_data      BLOB,                  -- Part content (encrypted BLOB when enabled)
    created_at          TIMESTAMP,             -- Creation time
    updated_at          TIMESTAMP,             -- Last update time
    deleted_at          TIMESTAMP,             -- Soft-delete time (NULL when active)
    deleted             TINYINT                -- Soft-delete flag: 0=active, 1=deleted
);
CREATE INDEX IF NOT EXISTS idx_rfd_0_0 ON remote_file_data_0 (file_id);
CREATE INDEX IF NOT EXISTS idx_rfd_1_0 ON remote_file_data_0 (remote_file_id);
CREATE INDEX IF NOT EXISTS idx_rfd_2_0 ON remote_file_data_0 (deleted);

CREATE TABLE IF NOT EXISTS remote_file_data_1 (
    id                  CHAR(16) PRIMARY KEY,  -- PK: unique chunk record id
    remote_file_id      CHAR(16),              -- FK (logical): remote_file.id
    file_id             CHAR(16),              -- Logical file id (matches remote_file.file_id)
    file_part_data      BLOB,                  -- Part content (encrypted BLOB when enabled)
    created_at          TIMESTAMP,             -- Creation time
    updated_at          TIMESTAMP,             -- Last update time
    deleted_at          TIMESTAMP,             -- Soft-delete time (NULL when active)
    deleted             TINYINT                -- Soft-delete flag: 0=active, 1=deleted
);
CREATE INDEX IF NOT EXISTS idx_rfd_0_1 ON remote_file_data_1 (file_id);
CREATE INDEX IF NOT EXISTS idx_rfd_1_1 ON remote_file_data_1 (remote_file_id);
CREATE INDEX IF NOT EXISTS idx_rfd_2_1 ON remote_file_data_1 (deleted);
