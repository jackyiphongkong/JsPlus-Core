/* ************************************************************************
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
	public <T> T unwrap(java.lang.Class<T> arg0) throws SQLException {
	    return originConnection.unwrap(arg0);
	}

    @Override
	public boolean isWrapperFor(java.lang.Class<?> arg0) throws SQLException{
	    return originConnection.isWrapperFor(arg0);
	}

    @Override

	/*
	* Performs the createSQLXML operation
	* @return the createSQLXML result
	* */
	public SQLXML createSQLXML() throws SQLException {
		return originConnection.createSQLXML();
	}

    @Override

	/*
	* Performs the createNClob operation
	* @return the createNClob result
	* */
	public NClob createNClob() throws SQLException {
		return originConnection.createNClob();
	}

    @Override

	/*
	* Performs the createBlob operation
	* @return the createBlob result
	* */
	public Blob createBlob() throws SQLException {
		return originConnection.createBlob();
	}

    @Override

	/*
	* Performs the createClob operation
	* @return the createClob result
	* */
	public Clob createClob() throws SQLException {
		return originConnection.createClob();
	}

    @Override

	/*
	* Performs the prepareStatement operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @return the prepareStatement result
	* */
	public PreparedStatement prepareStatement(String arg0, String[] arg1) throws SQLException {
		return originConnection.prepareStatement(arg0,arg1);
	}

    @Override

	/*
	* Performs the createArrayOf operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @return the createArrayOf result
	* */
	public Array createArrayOf(String arg0, Object[] arg1) throws SQLException {
		return originConnection.createArrayOf(arg0,arg1);
	}

    @Override

	/*
	* Performs the getClientInfo operation
	* @return the getClientInfo result
	* */
	public Properties getClientInfo() throws SQLException {
		return originConnection.getClientInfo();
	}

    @Override

	/*
	* Performs the getClientInfo operation
	* @param arg0 - the arg0
	* @return the getClientInfo result
	* */
	public String getClientInfo(String arg0) throws SQLException {
		return originConnection.getClientInfo(arg0);
	}

    @Override

	/*
	* Performs the setClientInfo operation
	* @param arg0 - the arg0
	* */
	public void setClientInfo(Properties arg0) throws SQLClientInfoException {
	    originConnection.setClientInfo(arg0);
	}

    @Override

	/*
	* Performs the setClientInfo operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* */
	public void setClientInfo(String arg0, String arg1) throws SQLClientInfoException {
	    originConnection.setClientInfo(arg0,arg1);
	}

    @Override

	/*
	* Performs the isValid operation
	* @param arg0 - the arg0
	* @return the isValid result
	* */
	public boolean isValid(int arg0) throws SQLException {
		return originConnection.isValid(arg0);
	}

    @Override

	/*
	* Performs the abort operation
	* @param arg0 - the arg0
	* */
	public void abort(Executor arg0) throws SQLException {
	    if(originConnection != null)
	        driverDataSource.abort(originConnection.hashCode());
	}

    @Override

	/*
	* Performs the getSchema operation
	* @return the getSchema result
	* */
	public String getSchema() throws SQLException {
		return originConnection.getSchema();
	}

    @Override

	/*
	* Performs the setSchema operation
	* @param arg0 - the arg0
	* */
	public void setSchema(String arg0) throws SQLException {
	    originConnection.setSchema(arg0);
	}

    @Override

	/*
	* Performs the createStruct operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @return the createStruct result
	* */
	public Struct createStruct(String arg0, Object[] arg1) throws SQLException {
		return originConnection.createStruct(arg0,arg1);
	}

    @Override

	/*
	* Performs the isReadOnly operation
	* @return the isReadOnly result
	* */
	public boolean isReadOnly() throws SQLException {
		return originConnection.isReadOnly();
	}

    @Override

	/*
	* Performs the setShardingKey operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* */
	public void setShardingKey(ShardingKey arg0, ShardingKey arg1) throws SQLException {
	    originConnection.setShardingKey(arg0,arg1);
	}

    @Override

	/*
	* Performs the setReadOnly operation
	* @param arg0 - the arg0
	* */
	public void setReadOnly(boolean arg0) throws SQLException {
	    originConnection.setReadOnly(arg0);
	}

    @Override

	/*
	* Performs the setShardingKeyIfValid operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @return the setShardingKeyIfValid result
	* */
	public boolean setShardingKeyIfValid(ShardingKey arg0, int arg1) throws SQLException {
		return originConnection.setShardingKeyIfValid(arg0,arg1);
	}

    @Override

	/*
	* Performs the getMetaData operation
	* @return the getMetaData result
	* */
	public DatabaseMetaData getMetaData() throws SQLException {
		return originConnection.getMetaData();
	}

    @Override

	/*
	* Performs the setShardingKeyIfValid operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @param arg2 - the arg2
	* @return the setShardingKeyIfValid result
	* */
	public boolean setShardingKeyIfValid(ShardingKey arg0, ShardingKey arg1, int arg2) throws SQLException {
		return originConnection.setShardingKeyIfValid( arg0, arg1, arg2);
	}

    @Override

	/*
	* Performs the endRequest operation
	* */
	public void endRequest() throws SQLException {
	    originConnection.endRequest();
	}

    @Override

	/*
	* Performs the beginRequest operation
	* */
	public void beginRequest() throws SQLException {
	    originConnection.beginRequest();
	}

    @Override

	/*
	* Performs the getNetworkTimeout operation
	* @return the getNetworkTimeout result
	* */
	public int getNetworkTimeout() throws SQLException {
		return originConnection.getNetworkTimeout();
	}

    @Override

	/*
	* Performs the setNetworkTimeout operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* */
	public void setNetworkTimeout(Executor arg0, int arg1) throws SQLException {
	    originConnection.setNetworkTimeout(arg0,arg1);
	}

    @Override

	/*
	* Performs the nativeSQL operation
	* @param arg0 - the arg0
	* @return the nativeSQL result
	* */
	public String nativeSQL(String arg0) throws SQLException {
		return originConnection.nativeSQL(arg0);
	}

    @Override

	/*
	* Performs the prepareCall operation
	* @param arg0 - the arg0
	* @return the prepareCall result
	* */
	public CallableStatement prepareCall(String arg0) throws SQLException {
		return originConnection.prepareCall(arg0);
	}

    @Override

	/*
	* Performs the getAutoCommit operation
	* @return the getAutoCommit result
	* */
	public boolean getAutoCommit() throws SQLException {
		return originConnection.getAutoCommit();
	}

    @Override

	/*
	* Performs the setAutoCommit operation
	* @param arg0 - the arg0
	* */
	public void setAutoCommit(boolean arg0) throws SQLException {
	    originConnection.setAutoCommit(arg0);
	}

    @Override

	/*
	* Performs the prepareStatement operation
	* @param arg0 - the arg0
	* @return the prepareStatement result
	* */
	public PreparedStatement prepareStatement(String arg0) throws SQLException {
		return originConnection.prepareStatement(arg0);
	}

    @Override

	/*
	* Performs the createStatement operation
	* @return the createStatement result
	* */
	public Statement createStatement() throws SQLException {
		return originConnection.createStatement();
	}

    @Override

	/*
	* Performs the createStatement operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @return the createStatement result
	* */
	public Statement createStatement(int arg0, int arg1) throws SQLException {
		return originConnection.createStatement(arg0,arg1);
	}

    @Override

	/*
	* Performs the clearWarnings operation
	* */
	public void clearWarnings() throws SQLException {
	    originConnection.clearWarnings();
	}

    @Override

	/*
	* Performs the getWarnings operation
	* @return the getWarnings result
	* */
	public SQLWarning getWarnings() throws SQLException {
		return originConnection.getWarnings();
	}

    @Override

	/*
	* Performs the rollback operation
	* */
	public void rollback() throws SQLException {
	    originConnection.rollback();
	}

    @Override

	/*
	* Performs the getTransactionIsolation operation
	* @return the getTransactionIsolation result
	* */
	public int getTransactionIsolation() throws SQLException {
		return originConnection.getTransactionIsolation();
	}

    @Override

	/*
	* Performs the commit operation
	* */
	public void commit() throws SQLException {
	    originConnection.commit();
	}

    @Override

	/*
	* Performs the setTransactionIsolation operation
	* @param arg0 - the arg0
	* */
	public void setTransactionIsolation(int arg0) throws SQLException {
	    originConnection.setTransactionIsolation(arg0);
	}

    @Override

	/*
	* Performs the isClosed operation
	* @return the isClosed result
	* */
	public boolean isClosed() throws SQLException {
		return originConnection.isClosed();
	}

    @Override

	/*
	* Performs the getCatalog operation
	* @return the getCatalog result
	* */
	public String getCatalog() throws SQLException {
		return originConnection.getCatalog();
	}

    @Override

	/*
	* Performs the close operation
	* */
	public void close() throws SQLException {
	    driverDataSource.closeConnection(originConnection);
	}

    @Override

	/*
	* Performs the setCatalog operation
	* @param arg0 - the arg0
	* */
	public void setCatalog(String arg0) throws SQLException {
	    originConnection.setCatalog(arg0);
	}

    @Override

	/*
	* Performs the setShardingKey operation
	* @param arg0 - the arg0
	* */
	public void setShardingKey(ShardingKey arg0) throws SQLException {
	    originConnection.setShardingKey(arg0);
	}

    @Override

	/*
	* Performs the setTypeMap operation
	* @param arg0 - the arg0
	* */
	public void setTypeMap(Map<String, Class<?>> arg0) throws SQLException {
	    originConnection.setTypeMap(arg0);
	}

    @Override

	/*
	* Performs the getTypeMap operation
	* @return the getTypeMap result
	* */
	public Map<String, Class<?>> getTypeMap() throws SQLException {
		return originConnection.getTypeMap();
	}

    @Override

	/*
	* Performs the prepareCall operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @param arg2 - the arg2
	* @return the prepareCall result
	* */
	public CallableStatement prepareCall(String arg0, int arg1, int arg2) throws SQLException {
		return originConnection.prepareCall(arg0, arg1, arg2);
	}

    @Override

	/*
	* Performs the prepareStatement operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @param arg2 - the arg2
	* @return the prepareStatement result
	* */
	public PreparedStatement prepareStatement(String arg0, int arg1, int arg2) throws SQLException {
		return originConnection.prepareStatement(arg0,arg1,arg2);
	}

    @Override

	/*
	* Performs the releaseSavepoint operation
	* @param arg0 - the arg0
	* */
	public void releaseSavepoint(Savepoint arg0) throws SQLException {
	    originConnection.releaseSavepoint(arg0);
	}

    @Override

	/*
	* Performs the rollback operation
	* @param arg0 - the arg0
	* */
	public void rollback(Savepoint arg0) throws SQLException {
	    originConnection.rollback(arg0);
	}

    @Override

	/*
	* Performs the setSavepoint operation
	* @param arg0 - the arg0
	* @return the setSavepoint result
	* */
	public Savepoint setSavepoint(String arg0) throws SQLException {
		return originConnection.setSavepoint(arg0);
	}

    @Override

	/*
	* Performs the setSavepoint operation
	* @return the setSavepoint result
	* */
	public Savepoint setSavepoint() throws SQLException {
		return originConnection.setSavepoint();
	}

    @Override

	/*
	* Performs the getHoldability operation
	* @return the getHoldability result
	* */
	public int getHoldability() throws SQLException {
		return originConnection.getHoldability();
	}

    @Override

	/*
	* Performs the setHoldability operation
	* @param arg0 - the arg0
	* */
	public void setHoldability(int arg0) throws SQLException {
	    originConnection.setHoldability(arg0);
	}

    @Override

	/*
	* Performs the prepareStatement operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @return the prepareStatement result
	* */
	public PreparedStatement prepareStatement(String arg0, int[] arg1) throws SQLException {
		return originConnection.prepareStatement(arg0,arg1);
	}

    @Override

	/*
	* Performs the prepareStatement operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @return the prepareStatement result
	* */
	public PreparedStatement prepareStatement(String arg0, int arg1) throws SQLException {
		return originConnection.prepareStatement(arg0,arg1);
	}

	/**
	* Performs the prepareCall operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @param arg2 - the arg2
	* @param arg3 - the arg3
	* @return the prepareCall result
	* */
	public CallableStatement prepareCall(String arg0, int arg1, int arg2, int arg3) throws SQLException {
		return originConnection.prepareCall(arg0,arg1,arg2,arg3);
	}

    @Override

	/*
	* Performs the prepareStatement operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @param arg2 - the arg2
	* @param arg3 - the arg3
	* @return the prepareStatement result
	* */
	public PreparedStatement prepareStatement(String arg0, int arg1, int arg2, int arg3) throws SQLException {
		return originConnection.prepareStatement(arg0,arg1,arg2,arg3);
	}

	@Override

	/*
	* Performs the createStatement operation
	* @param arg0 - the arg0
	* @param arg1 - the arg1
	* @param arg2 - the arg2
	* @return the createStatement result
	* */
	public Statement createStatement(int arg0, int arg1, int arg2) throws SQLException {
		return originConnection.createStatement(arg0, arg1, arg2);
	}

}
