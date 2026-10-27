-- ============================================================================
-- JsPlus-Core database reset script (fallback)
-- Drops every TEST table so the schema can be recreated cleanly by
-- change.sql. DROP TABLE IF EXISTS keeps this script re-runnable on both a
-- fresh database (first-time CI) and an existing one (local reset).
-- ghost_remote_command is deliberately NOT dropped: it is the runtime
-- GhostConsole's queue and tests must not touch it (a running ghost would
-- hit "table not found" mid-run). init only ensures it exists (IF NOT
-- EXISTS in change.sql).
-- remote_file / remote_file_data_0/1 are the RemoteFile test tables; they
-- are dropped here and recreated by change.sql like the test_* tables.
-- Run order by the launcher: fallback.sql -> change.sql.
-- ============================================================================

DROP TABLE IF EXISTS test_user;
DROP TABLE IF EXISTS test_product;
DROP TABLE IF EXISTS test_order;
DROP TABLE IF EXISTS test_log;
DROP TABLE IF EXISTS test_kv;
DROP TABLE IF EXISTS remote_file_data_1;
DROP TABLE IF EXISTS remote_file_data_0;
DROP TABLE IF EXISTS remote_file;
