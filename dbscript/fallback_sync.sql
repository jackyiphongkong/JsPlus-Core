-- ============================================================================
-- JsPlus-Core DBSync database reset script (fallback_sync)
-- Drops every DBSync TEST table so the schema can be recreated cleanly by
-- change_sync.sql. Applies to BOTH dedicated dbsync file databases:
--   Database/dbsync_source (DBSyncSource.properties)
--   Database/dbsync_target (DBSyncTarget.properties)
-- These databases are owned entirely by the dbsync test suite
-- (test/TestDBSync.js); the shared testdb is never touched.
-- Run order by InitDBSyncDB: fallback_sync.sql -> change_sync.sql.
-- ============================================================================

DROP TABLE IF EXISTS dbsync_rev_a;
DROP TABLE IF EXISTS dbsync_load_r;
DROP TABLE IF EXISTS dbsync_load_d;
DROP TABLE IF EXISTS dbsync_load_u;
DROP TABLE IF EXISTS dbsync_load_i;
DROP TABLE IF EXISTS dbsync_ent_a;
DROP TABLE IF EXISTS dbsync_sync_a;
DROP TABLE IF EXISTS dbsync_test_a;
