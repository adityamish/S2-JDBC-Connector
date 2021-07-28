// SPDX-License-Identifier: LGPL-2.1-or-later
// Copyright (c) 2012-2014 Monty Program Ab
// Copyright (c) 2015-2021 MariaDB Corporation Ab

package org.mariadb.jdbc.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.Common;
import org.mariadb.jdbc.MariaDbDataSource;

public class DataSourceTest extends Common {

  @Test
  public void basic() throws SQLException {
    DataSource ds = new MariaDbDataSource(mDefUrl);
    try (Connection con1 = ds.getConnection()) {
      try (Connection con2 = ds.getConnection()) {

        ResultSet rs1 = con1.createStatement().executeQuery("SELECT 1");
        ResultSet rs2 = con2.createStatement().executeQuery("SELECT 2");
        while (rs1.next()) {
          assertEquals(1, rs1.getInt(1));
        }
        while (rs2.next()) {
          assertEquals(2, rs2.getInt(1));
        }
      }
    }
  }

  @Test
  public void switchUser() throws SQLException {
    Assumptions.assumeTrue(
        !"maxscale".equals(System.getenv("srv"))
            && !"skysql".equals(System.getenv("srv"))
            && !"skysql-ha".equals(System.getenv("srv")));
    Statement stmt = sharedConn.createStatement();
    stmt.execute("DROP USER IF EXISTS 'dsUser'");
    if (minVersion(8, 0, 0)) {
      if (isMariaDBServer()) {
        stmt.execute("CREATE USER 'dsUser'@'%' IDENTIFIED BY 'MySup8%rPassw@ord'");
        stmt.execute("GRANT SELECT ON " + sharedConn.getCatalog() + ".* TO 'dsUser'@'%'");
      } else {
        stmt.execute(
            "CREATE USER 'dsUser'@'%' IDENTIFIED WITH mysql_native_password BY 'MySup8%rPassw@ord'");
        stmt.execute("GRANT SELECT ON " + sharedConn.getCatalog() + ".* TO 'dsUser'@'%'");
      }
    } else {
      stmt.execute("CREATE USER 'dsUser'@'%'");
      stmt.execute(
          "GRANT SELECT ON "
              + sharedConn.getCatalog()
              + ".* TO 'dsUser'@'%' IDENTIFIED BY 'MySup8%rPassw@ord'");
    }
    stmt.execute("FLUSH PRIVILEGES");

    DataSource ds = new MariaDbDataSource(mDefUrl + "allowPublicKeyRetrieval");
    try (Connection con1 = ds.getConnection()) {
      try (Connection con2 = ds.getConnection("dsUser", "MySup8%rPassw@ord")) {
        ResultSet rs1 = con1.createStatement().executeQuery("SELECT 1");
        ResultSet rs2 = con2.createStatement().executeQuery("SELECT 2");
        while (rs1.next()) {
          assertEquals(1, rs1.getInt(1));
        }
        while (rs2.next()) {
          assertEquals(2, rs2.getInt(1));
        }
      }
    } finally {
      stmt.execute("DROP USER IF EXISTS 'dsUser'");
    }

    // mysql has issue when creating new user with native password
    if (haveSsl() && !isMariaDBServer() && minVersion(8, 0, 0)) {
      try (Connection con = createCon("sslMode=trust")) {
        con.createStatement().execute("DO 1");
      }
    }
  }

  @Test
  public void exceptions() throws SQLException {
    DataSource ds = new MariaDbDataSource(mDefUrl);
    ds.unwrap(javax.sql.DataSource.class);
    ds.unwrap(MariaDbDataSource.class);
    assertThrowsContains(
        SQLException.class,
        () -> ds.unwrap(String.class),
        "Datasource is not a wrapper for java.lang.String");

    assertTrue(ds.isWrapperFor(javax.sql.DataSource.class));
    assertTrue(ds.isWrapperFor(MariaDbDataSource.class));
    assertFalse(ds.isWrapperFor(String.class));
    assertThrowsContains(
        SQLException.class,
        () -> new MariaDbDataSource("jdbc:wrongUrl"),
        "Wrong mariaDB url: jdbc:wrongUrl");
    assertNull(ds.getLogWriter());
    assertNull(ds.getParentLogger());
    ds.setLogWriter(null);

    assertEquals(30, ds.getLoginTimeout());
    ds.setLoginTimeout(60);
    assertEquals(60, ds.getLoginTimeout());
  }
}
