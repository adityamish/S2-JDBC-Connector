// SPDX-License-Identifier: LGPL-2.1-or-later
// Copyright (c) 2012-2014 Monty Program Ab
// Copyright (c) 2015-2021 MariaDB Corporation Ab

package org.mariadb.jdbc.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.sql.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.*;
import org.mariadb.jdbc.Connection;
import org.mariadb.jdbc.Statement;
import org.mariadb.jdbc.integration.tools.TcpProxy;

public class MultiHostTest extends Common {

  @Test
  public void failoverReadonlyToMaster() throws Exception {
    Assumptions.assumeTrue(
        !"skysql".equals(System.getenv("srv")) && !"skysql-ha".equals(System.getenv("srv")));
    try (Connection con = createProxyConKeep("waitReconnectTimeout=300&deniedListTimeout=300")) {
      long primaryThreadId = con.getThreadId();
      con.setReadOnly(true);
      long replicaThreadId = con.getThreadId();
      assertTrue(primaryThreadId != replicaThreadId);

      con.setReadOnly(false);
      assertEquals(primaryThreadId, con.getThreadId());
      con.setReadOnly(true);
      assertEquals(replicaThreadId, con.getThreadId());
      proxy.restart(250);

      con.isValid(1);
      assertEquals(primaryThreadId, con.getThreadId());
    }
  }

  @Test
  public void readOnly() throws SQLException {
    Assumptions.assumeTrue(
        !"skysql".equals(System.getenv("srv")) && !"skysql-ha".equals(System.getenv("srv")));
    try (Connection con = createProxyConKeep("waitReconnectTimeout=300&deniedListTimeout=300")) {
      Statement stmt = con.createStatement();
      stmt.execute("DROP TABLE IF EXISTS testReadOnly");
      stmt.execute("CREATE TABLE testReadOnly(id int)");
      con.setAutoCommit(false);
      con.setReadOnly(true);
      assertThrowsContains(
          SQLException.class,
          () -> stmt.execute("INSERT INTO testReadOnly values (2)"),
          "Cannot execute statement in a READ ONLY transaction");
      con.setReadOnly(false);
      stmt.execute("DROP TABLE testReadOnly");
    }
  }

  @Test
  public void syncState() throws Exception {
    Assumptions.assumeTrue(
        !"skysql".equals(System.getenv("srv")) && !"skysql-ha".equals(System.getenv("srv")));
    try (Connection con = createProxyConKeep("")) {
      Statement stmt = con.createStatement();
      stmt.execute("CREATE DATABASE IF NOT EXISTS sync");
      con.setCatalog("sync");
      con.setTransactionIsolation(java.sql.Connection.TRANSACTION_SERIALIZABLE);
      con.setReadOnly(true);
      assertEquals("sync", con.getCatalog());
      assertEquals(java.sql.Connection.TRANSACTION_SERIALIZABLE, con.getTransactionIsolation());
      con.setReadOnly(true);
      con.setReadOnly(false);
      assertEquals(java.sql.Connection.TRANSACTION_SERIALIZABLE, con.getTransactionIsolation());
      con.setTransactionIsolation(java.sql.Connection.TRANSACTION_READ_COMMITTED);
      con.setReadOnly(true);
      assertEquals(java.sql.Connection.TRANSACTION_READ_COMMITTED, con.getTransactionIsolation());
      con.setTransactionIsolation(java.sql.Connection.TRANSACTION_READ_UNCOMMITTED);
      con.setReadOnly(false);
      assertEquals(java.sql.Connection.TRANSACTION_READ_UNCOMMITTED, con.getTransactionIsolation());
      con.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
      con.setReadOnly(true);
      assertEquals(java.sql.Connection.TRANSACTION_REPEATABLE_READ, con.getTransactionIsolation());
    } finally {
      sharedConn.createStatement().execute("DROP DATABASE IF EXISTS sync");
    }
  }

  @Test
  public void replicaNotSet() throws Exception {
    Assumptions.assumeTrue(
        !"skysql".equals(System.getenv("srv")) && !"skysql-ha".equals(System.getenv("srv")));

    String url = mDefUrl.replaceAll("jdbc:mariadb:", "jdbc:mariadb:replication:");
    try (java.sql.Connection con = DriverManager.getConnection(url + "&waitReconnectTimeout=20")) {
      con.isValid(1);
      con.setReadOnly(true);
      con.isValid(1);
      // force reconnection try
      Thread.sleep(50);
      con.isValid(1);
    }
  }

  @Test
  public void masterFailover() throws Exception {
    Assumptions.assumeTrue(
        !"skysql".equals(System.getenv("srv")) && !"skysql-ha".equals(System.getenv("srv")));

    Configuration conf = Configuration.parse(mDefUrl);
    HostAddress hostAddress = conf.addresses().get(0);
    try {
      proxy = new TcpProxy(hostAddress.host, hostAddress.port);
    } catch (IOException i) {
      throw new SQLException("proxy error", i);
    }

    String url =
        mDefUrl.replaceAll(
            "//([^/]*)/",
            String.format(
                "//address=(host=localhost)(port=9999)(type=master),address=(host=localhost)(port=%s)(type=master),address=(host=%s)(port=%s)(type=master)/",
                proxy.getLocalPort(), hostAddress.host, hostAddress.port));
    url = url.replaceAll("jdbc:mariadb:", "jdbc:mariadb:sequential:");
    if (conf.sslMode() == SslMode.VERIFY_FULL) {
      url = url.replaceAll("sslMode=verify-full", "sslMode=verify-ca");
    }

    try (Connection con =
        (Connection)
            DriverManager.getConnection(
                url
                    + "waitReconnectTimeout=300&deniedListTimeout=300&retriesAllDown=4&connectTimeout=500")) {
      Statement stmt = con.createStatement();
      stmt.execute("SET @con=1");
      proxy.restart(50);

      ResultSet rs = stmt.executeQuery("SELECT @con");
      rs.next();
      assertNull(rs.getString(1));
    }
    Thread.sleep(50);

    // with transaction replay
    try (Connection con =
        (Connection)
            DriverManager.getConnection(
                url
                    + "transactionReplay=true&waitReconnectTimeout=300&deniedListTimeout=300&retriesAllDown=4&connectTimeout=500")) {
      Statement stmt = con.createStatement();
      stmt.execute("DROP TABLE IF EXISTS testReplay");
      stmt.execute("CREATE TABLE testReplay(id INT)");
      stmt.execute("INSERT INTO testReplay VALUE (1)");
      stmt.execute("START TRANSACTION");
      stmt.execute("INSERT INTO testReplay VALUE (2)");
      try (PreparedStatement prep = con.prepareStatement("INSERT INTO testReplay VALUE (?)")) {
        prep.setInt(1, 3);
        prep.execute();
      }

      try (PreparedStatement prep = con.prepareStatement("INSERT INTO testReplay VALUE (?)")) {
        prep.setInt(1, 4);
        prep.execute();
        proxy.restart(50);
        prep.setInt(1, 5);
        prep.execute();
      }

      ResultSet rs = stmt.executeQuery("SELECT * from testReplay");
      rs.next();
      assertEquals(1, rs.getInt(1));
      rs.next();
      assertEquals(2, rs.getInt(1));
      rs.next();
      assertEquals(3, rs.getInt(1));
      rs.next();
      assertEquals(4, rs.getInt(1));
      rs.next();
      assertEquals(5, rs.getInt(1));
      assertFalse(rs.next());
      stmt.execute("DROP TABLE IF EXISTS testReplay");
    }
  }

  @Test
  public void masterStreamingFailover() throws Exception {
    Assumptions.assumeTrue(
        isMariaDBServer()
            && !"skysql".equals(System.getenv("srv"))
            && !"skysql-ha".equals(System.getenv("srv")));

    Configuration conf = Configuration.parse(mDefUrl);
    HostAddress hostAddress = conf.addresses().get(0);
    try {
      proxy = new TcpProxy(hostAddress.host, hostAddress.port);
    } catch (IOException i) {
      throw new SQLException("proxy error", i);
    }

    String url =
        mDefUrl.replaceAll(
            "//([^/]*)/",
            String.format(
                "//address=(host=localhost)(port=%s)(type=master)/", proxy.getLocalPort()));
    url = url.replaceAll("jdbc:mariadb:", "jdbc:mariadb:sequential:");
    if (conf.sslMode() == SslMode.VERIFY_FULL) {
      url = url.replaceAll("sslMode=verify-full", "sslMode=verify-ca");
    }

    Connection con =
        (Connection)
            DriverManager.getConnection(
                url
                    + "allowMultiQueries&transactionReplay=true&waitReconnectTimeout=300&deniedListTimeout=300&retriesAllDown=40&connectTimeout=500&useReadAheadInput=false");
    long threadId = con.getThreadId();
    Statement stmt = con.createStatement();
    stmt.setFetchSize(2);
    ResultSet rs = stmt.executeQuery("SELECT * FROM seq_1_to_50; SELECT * FROM seq_1_to_50000");
    rs.next();
    assertEquals(1, rs.getInt(1));
    proxy.restart(50);
    Statement stmt2 = con.createStatement();
    assertThrowsContains(
        SQLException.class,
        () -> stmt2.executeQuery("SELECT * from mysql.user"),
        "Socket error during result streaming");
    assertNotEquals(threadId, con.getThreadId());

    // additional small test
    assertEquals(0, con.getNetworkTimeout());
    con.setNetworkTimeout(Runnable::run, 10);
    assertEquals(10, con.getNetworkTimeout());

    con.setReadOnly(true);
    con.close();
    assertThrowsContains(
        SQLNonTransientConnectionException.class,
        () -> con.setReadOnly(false),
        "Connection is closed");
    assertThrowsContains(
        SQLNonTransientConnectionException.class,
        () -> con.abort(Runnable::run),
        "Connection is closed");

    Connection con2 =
        (Connection)
            DriverManager.getConnection(
                url
                    + "allowMultiQueries&transactionReplay=true&waitReconnectTimeout=300&deniedListTimeout=300&retriesAllDown=40&connectTimeout=500&useReadAheadInput=false");
    con2.abort(Runnable::run);
  }

  @Test
  public void masterReplicationFailover() throws Exception {
    Assumptions.assumeTrue(
        !"skysql".equals(System.getenv("srv")) && !"skysql-ha".equals(System.getenv("srv")));

    Configuration conf = Configuration.parse(mDefUrl);
    HostAddress hostAddress = conf.addresses().get(0);
    try {
      proxy = new TcpProxy(hostAddress.host, hostAddress.port);
    } catch (IOException i) {
      throw new SQLException("proxy error", i);
    }

    String url =
        mDefUrl.replaceAll(
            "//([^/]*)/",
            String.format(
                "//localhost:%s,%s:%s/", proxy.getLocalPort(), hostAddress.host, hostAddress.port));
    url = url.replaceAll("jdbc:mariadb:", "jdbc:mariadb:replication:");
    if (conf.sslMode() == SslMode.VERIFY_FULL) {
      url = url.replaceAll("sslMode=verify-full", "sslMode=verify-ca");
    }

    try (Connection con =
        (Connection)
            DriverManager.getConnection(
                url
                    + "waitReconnectTimeout=300&deniedListTimeout=300&retriesAllDown=4&connectTimeout=500")) {
      Statement stmt = con.createStatement();
      stmt.execute("SET @con=1");
      con.setReadOnly(true);
      con.isValid(1);
      proxy.restart(50);
      Thread.sleep(20);
      con.setReadOnly(false);

      ResultSet rs = stmt.executeQuery("SELECT @con");
      rs.next();
      assertNull(rs.getString(1));
    }

    // never reconnect
    try (Connection con =
        (Connection)
            DriverManager.getConnection(
                url
                    + "waitReconnectTimeout=300&deniedListTimeout=300&retriesAllDown=4&connectTimeout=500")) {
      Statement stmt = con.createStatement();
      stmt.execute("SET @con=1");
      con.setReadOnly(true);
      con.isValid(1);
      proxy.stop();
      Thread.sleep(20);
      con.setReadOnly(false);
      assertFalse(con.isValid(1));
      assertThrows(SQLException.class, () -> stmt.execute("SELECT 1"));
    }
  }

  @Test
  public void masterReplicationStreamingFailover() throws Exception {
    Assumptions.assumeTrue(
        isMariaDBServer()
            && !"skysql".equals(System.getenv("srv"))
            && !"skysql-ha".equals(System.getenv("srv")));

    Configuration conf = Configuration.parse(mDefUrl);
    HostAddress hostAddress = conf.addresses().get(0);
    try {
      proxy = new TcpProxy(hostAddress.host, hostAddress.port);
    } catch (IOException i) {
      throw new SQLException("proxy error", i);
    }

    String url =
        mDefUrl.replaceAll(
            "//([^/]*)/",
            String.format(
                "//address=(host=localhost)(port=%s)(type=primary),address=(host=%s)(port=%s)(type=replica)/",
                proxy.getLocalPort(), hostAddress.host, hostAddress.port));
    url = url.replaceAll("jdbc:mariadb:", "jdbc:mariadb:replication:");
    if (conf.sslMode() == SslMode.VERIFY_FULL) {
      url = url.replaceAll("sslMode=verify-full", "sslMode=verify-ca");
    }

    Connection con =
        (Connection)
            DriverManager.getConnection(
                url
                    + "allowMultiQueries&transactionReplay=true&waitReconnectTimeout=300&deniedListTimeout=300&retriesAllDown=40&connectTimeout=500&useReadAheadInput=false");
    long threadId = con.getThreadId();
    Statement stmt = con.createStatement();
    stmt.setFetchSize(2);
    ResultSet rs = stmt.executeQuery("SELECT * FROM seq_1_to_50; SELECT * FROM seq_1_to_50000");
    rs.next();
    assertEquals(1, rs.getInt(1));
    proxy.restart(50);
    Statement stmt2 = con.createStatement();
    assertThrowsContains(
        SQLException.class,
        () -> stmt2.executeQuery("SELECT * from mysql.user"),
        "Socket error during result streaming");
    assertNotEquals(threadId, con.getThreadId());

    // additional small test
    assertEquals(0, con.getNetworkTimeout());
    con.setNetworkTimeout(Runnable::run, 10);
    assertEquals(10, con.getNetworkTimeout());

    con.setReadOnly(true);
    con.close();
    assertThrowsContains(
        SQLNonTransientConnectionException.class,
        () -> con.setReadOnly(false),
        "Connection is closed");
    assertThrowsContains(
        SQLNonTransientConnectionException.class,
        () -> con.abort(Runnable::run),
        "Connection is closed");

    Connection con2 =
        (Connection)
            DriverManager.getConnection(
                url
                    + "allowMultiQueries&transactionReplay=true&waitReconnectTimeout=300&deniedListTimeout=300&retriesAllDown=40&connectTimeout=500&useReadAheadInput=false");
    con2.abort(Runnable::run);
  }

  public Connection createProxyConKeep(String opts) throws SQLException {
    Configuration conf = Configuration.parse(mDefUrl);
    HostAddress hostAddress = conf.addresses().get(0);
    try {
      proxy = new TcpProxy(hostAddress.host, hostAddress.port);
    } catch (IOException i) {
      throw new SQLException("proxy error", i);
    }

    String url =
        mDefUrl.replaceAll(
            "//([^/]*)/",
            String.format(
                "//%s:%s,localhost:%s/", hostAddress.host, hostAddress.port, proxy.getLocalPort()));
    url = url.replaceAll("jdbc:mariadb:", "jdbc:mariadb:replication:");
    if (conf.sslMode() == SslMode.VERIFY_FULL) {
      url = url.replaceAll("sslMode=verify-full", "sslMode=verify-ca");
    }

    return (Connection) DriverManager.getConnection(url + opts);
  }
}
