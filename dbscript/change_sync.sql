-- ============================================================================
-- JsPlus-Core DBSync database initialization script (change_sync.sql)
-- Creates every DBSync TEST table. Applies to BOTH dedicated dbsync file
-- databases: Database/dbsync_source (DBSyncSource.properties, the sync
-- SOURCE) and Database/dbsync_target (DBSyncTarget.properties, the sync
-- TARGET). Both sides are structurally identical so TestDBSync.js can
-- verify a sync by comparing the same schema on each side. init owns the
-- table lifecycle: the test script only DELETEs rows, never drops/creates.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Application DB account (created if not exists) - same sample credentials as
-- the main change.sql, so both DBSyncSource.properties and
-- DBSyncTarget.properties (sample_user / sample_password) can log in.
-- ----------------------------------------------------------------------------
CREATE USER IF NOT EXISTS sample_user PASSWORD 'sample_password';

GRANT ALL ON SCHEMA PUBLIC TO sample_user;
ALTER USER sample_user ADMIN TRUE;

-- ============================================================================
-- Table 1: dbsync_test_a - generic dbsync CRUD / quote table (TestDBSync C4/C5)
-- ============================================================================
CREATE TABLE IF NOT EXISTS dbsync_test_a (
    id          INT PRIMARY KEY,     -- PK: numeric id (quote/CRUD coverage)
    name        VARCHAR(50),         -- Display name
    created_at  TIMESTAMP            -- Creation time
);

-- ============================================================================
-- Table 2: dbsync_sync_a - real source->target replication table (C9-C11)
-- ============================================================================
CREATE TABLE IF NOT EXISTS dbsync_sync_a (
    id          VARCHAR(50) PRIMARY KEY,  -- PK: string id (inserter key compare)
    name        VARCHAR(50),              -- Display name
    created_at  TIMESTAMP                 -- Creation time
);

-- ============================================================================
-- Table 3: dbsync_ent_a - Enter.start(configFile) entry table (C12)
-- ============================================================================
CREATE TABLE IF NOT EXISTS dbsync_ent_a (
    id          VARCHAR(50) PRIMARY KEY,  -- PK
    name        VARCHAR(50),              -- Display name
    created_at  TIMESTAMP                 -- Creation time
);

-- ============================================================================
-- Table 4: dbsync_load_i - bulk inserter load table (C13)
-- ============================================================================
CREATE TABLE IF NOT EXISTS dbsync_load_i (
    id          VARCHAR(50) PRIMARY KEY,  -- PK
    name        VARCHAR(50),              -- Display name
    created_at  TIMESTAMP                 -- Creation time
);

-- ============================================================================
-- Table 5: dbsync_load_u - bulk updater load table (C14)
-- ============================================================================
CREATE TABLE IF NOT EXISTS dbsync_load_u (
    id          VARCHAR(50) PRIMARY KEY,  -- PK
    name        VARCHAR(50),              -- Display name
    created_at  TIMESTAMP,                -- Creation time
    updated_at  TIMESTAMP                 -- Last update time (updater key)
);

-- ============================================================================
-- Table 6: dbsync_load_d - bulk deleter load table (C15)
-- ============================================================================
CREATE TABLE IF NOT EXISTS dbsync_load_d (
    id          VARCHAR(50) PRIMARY KEY,  -- PK
    name        VARCHAR(50),              -- Display name
    created_at  TIMESTAMP,                -- Creation time
    deleted     INT,                      -- Soft-delete flag: 0=active, 1=deleted
    deleted_at  TIMESTAMP                 -- Soft-delete time (deleter key)
);

-- ============================================================================
-- Table 7: dbsync_load_r - bulk remover load table (C16)
-- ============================================================================
CREATE TABLE IF NOT EXISTS dbsync_load_r (
    id          VARCHAR(50) PRIMARY KEY,  -- PK
    name        VARCHAR(50),              -- Display name
    deleted     INT,                      -- Soft-delete flag: 0=active, 1=deleted
    deleted_at  TIMESTAMP                 -- Soft-delete time (remover key)
);

-- ============================================================================
-- Table 8: dbsync_rev_a - bidirectional (Reverse=true) replication table (C17)
-- ============================================================================
CREATE TABLE IF NOT EXISTS dbsync_rev_a (
    id          VARCHAR(50) PRIMARY KEY,  -- PK
    name        VARCHAR(50),              -- Display name
    created_at  TIMESTAMP                 -- Creation time
);
