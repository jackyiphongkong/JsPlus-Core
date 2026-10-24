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
package com.killcoding.datasource;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.ShardingKey;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * Implement and enhance the JDBC connection class
 * */
public final class DriverConnection implements Connection {
    
    /**
     * Original connection 
     * */
    private Connection originConnection;
    
    /**
     * DriverDataSource Object
     * */
    private DriverDataSource driverDataSource;

    /**
     * New a DriverConnection object
     * */
	public DriverConnection() {
		super();
	}
	
	/**
	 * New a DriverConnection object
	 * @param driverDataSource 
	 * @param originConnection
	 * */
	public DriverConnection(DriverDataSource driverDataSource,Connection originConnection){
	    super();
	    this.driverDataSource = driverDataSource;
	    this.originConnection = originConnection;
	}
	
	/**
	 * Get origin connection hash code
	 * @return int - Connection hash code
	 * */
	public int getOriginConnectionHashCode(){
	    return this.originConnection.hashCode();
	}
	
	/**
	 * Get current DriverDataSource
	 * @return DriverDataSource
	 * */
	public DriverDataSource getDriverDataSource(){
	    return driverDataSource;
	}
	
	@Override
	public Object unwrap(java.lang.Class arg0) throws SQLException {
	    return originConnection.unwrap(arg0);
	}

    @Override	
	public boolean isWrapperFor(java.lang.Class arg0) throws SQLException{
	    return originConnection.isWrapperFor(arg0);
	}

    @Override
	public SQLXML createSQLXML() throws SQLException {
		return originConnection.createSQLXML();
	}

    @Override
	public NClob createNClob() throws SQLException {
		return originConnection.createNClob();
	}

    @Override
	public Blob createBlob() throws SQLException {
		return originConnection.createBlob();
	}

    @Override
	public Clob createClob() throws SQLException {
		return originConnection.createClob();
	}
    
    @Override
	public PreparedStatement prepareStatement(String arg0, String[] arg1) throws SQLException {
		return originConnection.prepareStatement(arg0,arg1);
	}

    @Override
	public Array createArrayOf(String arg0, Object[] arg1) throws SQLException {
		return originConnection.createArrayOf(arg0,arg1);
	}

    @Override
	public Properties getClientInfo() throws SQLException {
		return originConnection.getClientInfo();
	}

    @Override
	public String getClientInfo(String arg0) throws SQLException {
		return originConnection.getClientInfo(arg0);
	}

    @Override
	public void setClientInfo(Properties arg0) throws SQLClientInfoException {
	    originConnection.setClientInfo(arg0);
	}

    @Override
	public void setClientInfo(String arg0, String arg1) throws SQLClientInfoException {
	    originConnection.setClientInfo(arg0,arg1);
	}

    @Override
	public boolean isValid(int arg0) throws SQLException {
		return originConnection.isValid(arg0);
	}

    @Override
	public void abort(Executor arg0) throws SQLException {
	    if(originConnection != null)
	        driverDataSource.abort(originConnection.hashCode());
	}

    @Override
	public String getSchema() throws SQLException {
		return originConnection.getSchema();
	}
	
    @Override
	public void setSchema(String arg0) throws SQLException {
	    originConnection.setSchema(arg0);
	}

    @Override
	public Struct createStruct(String arg0, Object[] arg1) throws SQLException {
		return originConnection.createStruct(arg0,arg1);
	}

    @Override
	public boolean isReadOnly() throws SQLException {
		return originConnection.isReadOnly();
	}

    @Override
	public void setShardingKey(ShardingKey arg0, ShardingKey arg1) throws SQLException {
	    originConnection.setShardingKey(arg0,arg1);
	}

    @Override
	public void setReadOnly(boolean arg0) throws SQLException {
	    originConnection.setReadOnly(arg0);
	}

    @Override
	public boolean setShardingKeyIfValid(ShardingKey arg0, int arg1) throws SQLException {
		return originConnection.setShardingKeyIfValid(arg0,arg1);
	}

    @Override
	public DatabaseMetaData getMetaData() throws SQLException {
		return originConnection.getMetaData();
	}

    @Override
	public boolean setShardingKeyIfValid(ShardingKey arg0, ShardingKey arg1, int arg2) throws SQLException {
		return originConnection.setShardingKeyIfValid( arg0, arg1, arg2);
	}

    @Override
	public void endRequest() throws SQLException {
	    originConnection.endRequest();
	}

    @Override
	public void beginRequest() throws SQLException {
	    originConnection.beginRequest();
	}

    @Override
	public int getNetworkTimeout() throws SQLException {
		return originConnection.getNetworkTimeout();
	}

    @Override
	public void setNetworkTimeout(Executor arg0, int arg1) throws SQLException {
	    originConnection.setNetworkTimeout(arg0,arg1);
	}

    @Override
	public String nativeSQL(String arg0) throws SQLException {
		return originConnection.nativeSQL(arg0);
	}

    @Override
	public CallableStatement prepareCall(String arg0) throws SQLException {
		return originConnection.prepareCall(arg0);
	}

    @Override
	public boolean getAutoCommit() throws SQLException {
		return originConnection.getAutoCommit();
	}

    @Override
	public void setAutoCommit(boolean arg0) throws SQLException {
	    originConnection.setAutoCommit(arg0);
	}

    @Override
	public PreparedStatement prepareStatement(String arg0) throws SQLException {
		return originConnection.prepareStatement(arg0);
	}   

    @Override
	public Statement createStatement() throws SQLException {
		return originConnection.createStatement();
	}

    @Override
	public Statement createStatement(int arg0, int arg1) throws SQLException {
		return originConnection.createStatement(arg0,arg1);
	}

    @Override
	public void clearWarnings() throws SQLException {
	    originConnection.clearWarnings();
	}
    
    @Override
	public SQLWarning getWarnings() throws SQLException {
		return originConnection.getWarnings();
	}

    @Override
	public void rollback() throws SQLException {
	    originConnection.rollback();
	}
	
    @Override
	public int getTransactionIsolation() throws SQLException {
		return originConnection.getTransactionIsolation();
	}

    @Override
	public void commit() throws SQLException {
	    originConnection.commit();
	}

    @Override
	public void setTransactionIsolation(int arg0) throws SQLException {
	    originConnection.setTransactionIsolation(arg0);
	}

    @Override
	public boolean isClosed() throws SQLException {
		return originConnection.isClosed();
	}

    @Override
	public String getCatalog() throws SQLException {
		return originConnection.getCatalog();
	}

    @Override
	public void close() throws SQLException {
	    driverDataSource.closeConnection(originConnection);
	}

    @Override
	public void setCatalog(String arg0) throws SQLException {
	    originConnection.setCatalog(arg0);
	}
    
    @Override
	public void setShardingKey(ShardingKey arg0) throws SQLException {
	    originConnection.setShardingKey(arg0);
	}

    @Override
	public void setTypeMap(Map arg0) throws SQLException {
	    originConnection.setTypeMap(arg0);
	}

    @Override
	public Map getTypeMap() throws SQLException {
		return originConnection.getTypeMap();
	}

    @Override
	public CallableStatement prepareCall(String arg0, int arg1, int arg2) throws SQLException {
		return originConnection.prepareCall(arg0, arg1, arg2);
	}

    @Override
	public PreparedStatement prepareStatement(String arg0, int arg1, int arg2) throws SQLException {
		return originConnection.prepareStatement(arg0,arg1,arg2);
	}
	
    @Override
	public void releaseSavepoint(Savepoint arg0) throws SQLException {
	    originConnection.releaseSavepoint(arg0);
	}

    @Override
	public void rollback(Savepoint arg0) throws SQLException {
	    originConnection.rollback(arg0);
	}

    @Override
	public Savepoint setSavepoint(String arg0) throws SQLException {
		return originConnection.setSavepoint(arg0);
	}

    @Override
	public Savepoint setSavepoint() throws SQLException {
		return originConnection.setSavepoint();
	}

    @Override
	public int getHoldability() throws SQLException {
		return originConnection.getHoldability();
	}

    @Override
	public void setHoldability(int arg0) throws SQLException {
	    originConnection.setHoldability(arg0);
	}

    @Override
	public PreparedStatement prepareStatement(String arg0, int[] arg1) throws SQLException {
		return originConnection.prepareStatement(arg0,arg1);
	}

    @Override
	public PreparedStatement prepareStatement(String arg0, int arg1) throws SQLException {
		return originConnection.prepareStatement(arg0,arg1);
	}

	public CallableStatement prepareCall(String arg0, int arg1, int arg2, int arg3) throws SQLException {
		return originConnection.prepareCall(arg0,arg1,arg2,arg3);
	}

    @Override
	public PreparedStatement prepareStatement(String arg0, int arg1, int arg2, int arg3) throws SQLException {
		return originConnection.prepareStatement(arg0,arg1,arg2,arg3);
	}   
	
	@Override
	public Statement createStatement(int arg0, int arg1, int arg2) throws SQLException {
		return originConnection.createStatement(arg0, arg1, arg2);
	}

}