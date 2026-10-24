/*******************************************************************************
The MIT License (MIT)

Copyright (c) 2024 KILLCODING.COM

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*****************************************************************************/
package com.killcoding.file;

public interface RemoteFileSQL {

	public static final String SQL_INSERT_INTO_FILE = "INSERT INTO %s (id,source_hostname,synced_on_hostnames,deleted_on_hostnames,file_id,file_name,file_parent_path,file_type,file_part_index,file_part_size,file_part_data_ds,file_part_data_table,file_part_last_index,file_size,file_modify_time,file_deleted,created_user_id,updated_user_id,deleted_user_id,created_at,updated_at,deleted_at,deleted) VALUES (:id,:source_hostname,:synced_on_hostnames,:deleted_on_hostnames,:file_id,:file_name,:file_parent_path,:file_type,:file_part_index,:file_part_size,:file_part_data_ds,:file_part_data_table,:file_part_last_index,:file_size,:file_modify_time,:file_deleted,:created_user_id,:updated_user_id,NULL,:created_at,:updated_at,NULL,0)";

	public static final String SQL_INSERT_INTO_FILE_DATA = "INSERT INTO %s (id,file_id,remote_file_id,file_part_data,created_at,updated_at,deleted_at,deleted) VALUES (:id,:file_id,:remote_file_id,:file_part_data,:created_at,:updated_at,NULL,0)";

	public static final String SQL_SELECT_FOR_FIRST_FILE = "SELECT * FROM %s WHERE file_parent_path = :file_parent_path AND file_name = :file_name AND file_part_index = 0 AND deleted = 0 AND file_deleted = 0";

	public static final String SQL_SELECT_FOR_COUNT_ALL_SUB_FILES = "SELECT COUNT(id) AS as_count FROM %s WHERE file_parent_path = :file_parent_path AND file_name = :file_name AND file_part_index = 0";

	public static final String SQL_SELECT_FOR_LAST_FILE_PART = "SELECT * FROM %s WHERE file_id = :file_id AND file_parent_path = :file_parent_path AND file_name = :file_name ORDER BY file_part_index DESC";

	public static final String SQL_UPDATE_FOR_DELETE_ALL = "UPDATE %s SET file_deleted = :file_deleted,deleted = :deleted,deleted_user_id = :deleted_user_id,deleted_at = :deleted_at,updated_user_id = :updated_user_id,updated_at = :updated_at WHERE deleted = 0 AND ((file_parent_path = :file_parent_path AND file_name = :file_name) OR file_parent_path = :root_folder OR file_parent_path LIKE :like_subfolder)";

	public static final String SQL_UPDATE_FOR_DELETE_FILE_LINK = "UPDATE %s SET file_deleted = :file_deleted,deleted = :deleted,deleted_user_id = :deleted_user_id,deleted_at = :deleted_at,updated_user_id = :updated_user_id,updated_at = :updated_at  WHERE file_parent_path = :file_parent_path AND file_name = :file_name AND file_type = :file_type AND deleted = 0";

	public static final String SQL_UPDATE_BY_FILE_ID_FOR_CLEAR_FILE_DATA = "UPDATE %s SET file_part_data = NULL,deleted = 1,deleted_at = :deleted_at WHERE file_id = :file_id";

	public static final String SQL_DELETE_BY_RF_ID_FROM_FILE_DATA = "DELETE FROM %s WHERE deleted = 1 AND deleted_at <= :deleted_at";

	public static final String SQL_DELETE_BY_FILE_ID_FROM_FILE = "DELETE FROM %s WHERE file_id = :file_id AND deleted = 1";

	public static final String SQL_SELECT_BY_PATH_FOR_CHECK_EXISTS = "SELECT * FROM %s WHERE file_part_index = 0 AND file_parent_path = :file_parent_path AND file_name = :file_name ORDER BY created_at DESC";

	public static final String SQL_UPDATE_FOR_COMPLETED = "UPDATE %s SET file_part_last_index = :file_part_last_index,file_size = :file_size,file_modify_time = :file_modify_time,updated_at = :updated_at,updated_user_id = :updated_user_id WHERE deleted = 0 AND file_part_index = 0 AND file_id = :file_id";

	public static final String SQL_UPDATE_FOR_SYNCED_TO_DISK = "UPDATE %s SET synced_on_hostnames = :synced_on_hostnames,updated_at = :updated_at,updated_user_id = :updated_user_id WHERE file_parent_path = :file_parent_path AND file_name = :file_name AND file_part_index = 0 AND file_part_last_index > -1";

    public static final String SQL_UPDATE_FOR_DELETED_TO_DISK = "UPDATE %s SET deleted_on_hostnames = :deleted_on_hostnames,updated_at = :updated_at,updated_user_id = :updated_user_id WHERE file_deleted = 1 AND file_parent_path = :file_parent_path AND file_name = :file_name AND file_part_index = 0 AND file_part_last_index > -1";

	public static final String SQL_UPDATE_FOR_DELETED_TO_DISK_SUB_FILES = "UPDATE %s SET deleted_on_hostnames = :deleted_on_hostnames,updated_at = :updated_at,updated_user_id = :updated_user_id WHERE file_deleted = 1 AND (file_parent_path = :file_parent_path OR file_parent_path LIKE :like_file_parent_path)";

	public static final String SQL_SELECT_FOR_SUM_FILE_PART_SIZE_INDEX = "SELECT SUM(file_part_size) AS file_part_size,MAX(file_part_index) AS file_part_index,MAX(created_at) AS created_at FROM %s WHERE deleted = 0 AND file_id = :file_id AND file_parent_path = :file_parent_path AND file_name = :file_name GROUP BY file_id,file_parent_path,file_name";

	public static final String SQL_SELECT_FILE = "SELECT * FROM %s WHERE deleted = 0 AND file_id = :file_id ORDER BY file_part_index ASC";

	public static final String SQL_SELECT_FILE_HAVE_DATA = "SELECT * FROM %s WHERE deleted = 0 AND file_id = :file_id AND file_type = 'F' ORDER BY file_part_index ASC";

	public static final String SQL_SELECT_BY_RF_ID_FROM_FILE_DATA = "SELECT * FROM %s WHERE deleted = 0 AND remote_file_id = :remote_file_id";

	public static final String SQL_SELECT_FOR_COMPLETED = "SELECT * FROM %s WHERE deleted = 0 AND file_deleted = 0 AND file_part_last_index > -1 AND file_part_index = 0 AND file_parent_path = :file_parent_path AND file_name = :file_name ORDER BY updated_at DESC";

	public static final String SQL_SELECT_GET_RECORD_BY_ID = "SELECT * FROM %s WHERE id = :id";

	public static final String SQL_SELECT_GET_RECORD_BY_FILE_ID = "SELECT * FROM %s WHERE file_id = :file_id AND file_part_index = 0";

	public static final String SQL_SELECT_CHECK_COMPLETED = "SELECT * FROM %s WHERE file_deleted = 0 AND deleted = 0 AND file_part_index = 0 AND file_part_last_index > -1 AND file_id = :file_id";

	public static final String SQL_SELECT_FOR_TIMEOUT = "SELECT * FROM %s WHERE deleted = 0 AND file_type = 'F' AND file_part_last_index = -1 AND file_parent_path = :file_parent_path AND file_name = :file_name ORDER BY updated_at DESC";

    public static final String SQL_SELECT_FOR_ALL_TIMEOUT = "SELECT * FROM %s WHERE deleted = 0 AND file_type = 'F' AND file_part_last_index = -1 AND file_part_index = 0 AND updated_at < :before_updated_at AND (file_parent_path = :file_parent_path OR (file_parent_path LIKE :like_file_parent_path)) ORDER BY created_at ASC";

    public static final String SQL_UPDATE_FOR_TIMEOUT_DELETE = "UPDATE %s SET deleted = 1,deleted_at = :deleted_at,deleted_user_id = :deleted_user_id WHERE file_id = :file_id AND deleted = 0";
    
	public static final String SQL_SELECT_FOR_LIST_ALL = "SELECT * FROM %s WHERE deleted = 0 AND file_part_index = 0 AND file_part_last_index > -1 AND file_parent_path LIKE :file_parent_path AND file_name LIKE :file_name ORDER BY created_at ASC";

	public static final String SQL_SELECT_FOR_LIST_ALL_SYNC = "SELECT * FROM %s WHERE file_part_index = 0 AND file_part_last_index > -1 AND file_parent_path LIKE :file_parent_path AND file_name LIKE :file_name AND deleted = 0 AND file_deleted = 0 AND file_modify_time >= :begin_time AND file_modify_time <= :end_time AND created_at <= :created_at AND synced_on_hostnames NOT LIKE :synced_on_hostnames ORDER BY created_at ASC";

	public static final String SQL_SELECT_FOR_LIST_ALL_DELETE = "SELECT * FROM %s WHERE file_part_index = 0 AND file_part_last_index > -1 AND file_parent_path LIKE :file_parent_path AND file_name LIKE :file_name AND file_deleted = 1 AND deleted = 0 AND file_modify_time >= :begin_time AND file_modify_time <= :end_time AND created_at <= :created_at AND deleted_on_hostnames NOT LIKE :deleted_on_hostnames ORDER BY file_type,deleted_at ASC";

	public static final String SQL_SELECT_FOR_LAST_UPDATE = "SELECT * FROM %s WHERE file_part_index = 0 AND file_parent_path = :file_parent_path AND file_name = :file_name ORDER BY updated_at DESC";

	public static final String SQL_SELECT_FOR_FIRST_CREATED_AT = "SELECT * FROM %s WHERE deleted = 0 AND file_part_index = 0 AND file_part_last_index > -1 AND (file_parent_path = :file_parent_path OR (file_parent_path LIKE :like_file_parent_path)) AND deleted_on_hostnames NOT LIKE :deleted_on_hostnames ORDER BY created_at ASC";

	public static final String SQL_SELECT_FOR_MAX_DATA_TABLE = "SELECT * FROM %s WHERE deleted = 0 AND file_part_index = 0 AND file_part_last_index > -1 AND (file_parent_path = :file_parent_path OR (file_parent_path LIKE :like_file_parent_path)) ORDER BY file_part_data_table DESC";

	public static final String SQL_SELECT_FOR_LIST_ALL_SCAN_DELETE = "SELECT * FROM %s WHERE deleted = 0 AND file_part_index = 0 AND file_part_last_index > -1 AND file_part_data_table = :file_part_data_table AND file_modify_time >= :begin_time AND file_modify_time <= :end_time AND created_at <= :created_at AND (file_parent_path = :file_parent_path OR (file_parent_path LIKE :like_file_parent_path)) AND deleted_on_hostnames NOT LIKE :deleted_on_hostnames ORDER BY created_at ASC";

	public static final String SQL_SELECT_FOR_ARCHIVE_FILE_DELETED = "SELECT file_part_data_ds,file_part_data_table,file_id,file_type FROM %s WHERE file_deleted = 1 AND deleted = 0 AND deleted_at <= :before_deleted_at AND file_type IN ('F','L') AND (file_parent_path = :file_parent_path OR file_parent_path LIKE :like_file_parent_path) GROUP BY file_part_data_ds,file_part_data_table,file_id,file_type ORDER BY file_part_data_table ASC";

	public static final String SQL_SELECT_FOR_ARCHIVE_DELETED = "SELECT file_part_data_ds,file_part_data_table,file_id,file_type FROM %s WHERE deleted = 1 AND deleted_at <= :before_deleted_at AND (file_parent_path = :file_parent_path OR file_parent_path LIKE :like_file_parent_path) GROUP BY file_part_data_ds,file_part_data_table,file_id,file_type ORDER BY file_part_data_table ASC";

}