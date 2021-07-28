// SPDX-License-Identifier: LGPL-2.1-or-later
// Copyright (c) 2012-2014 Monty Program Ab
// Copyright (c) 2015-2021 MariaDB Corporation Ab

package org.mariadb.jdbc.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.sql.*;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.Common;

public class LocalInfileTest extends Common {
  @BeforeAll
  public static void beforeAll2() throws SQLException {
    drop();
    Statement stmt = sharedConn.createStatement();
    stmt.execute("CREATE TABLE LocalInfileInputStreamTest(id int, test varchar(100))");
    stmt.execute("CREATE TABLE LocalInfileInputStreamTest2(id int, test varchar(100))");
    stmt.execute("CREATE TABLE ttlocal(id int, test varchar(100))");
    stmt.execute("CREATE TABLE ldinfile(a varchar(10))");
    stmt.execute(
        "CREATE TABLE `infile`(`a` varchar(50) DEFAULT NULL, `b` varchar(50) DEFAULT NULL) ENGINE=InnoDB DEFAULT CHARSET=latin1");
    stmt.execute("FLUSH TABLES");
  }

  @AfterAll
  public static void drop() throws SQLException {
    Statement stmt = sharedConn.createStatement();
    stmt.execute("DROP TABLE IF EXISTS AllowMultiQueriesTest");
    stmt.execute("DROP TABLE IF EXISTS LocalInfileInputStreamTest");
    stmt.execute("DROP TABLE IF EXISTS LocalInfileInputStreamTest2");
    stmt.execute("DROP TABLE IF EXISTS ttlocal");
    stmt.execute("DROP TABLE IF EXISTS ldinfile");
    stmt.execute("DROP TABLE IF EXISTS `infile`");
  }

  @Test
  public void defaultThrowExceptions() throws Exception {
    Assumptions.assumeTrue(
        (isMariaDBServer() || !minVersion(8, 0, 3))
            && !"skysql".equals(System.getenv("srv"))
            && !"skysql-ha".equals(System.getenv("srv")));

    try (Connection con = createCon()) {
      Statement stmt = con.createStatement();
      assertThrowsContains(
          SQLException.class,
          () ->
              stmt.execute(
                  "LOAD DATA LOCAL INFILE 'someFile' INTO TABLE LocalInfileInputStreamTest2 (id, test)"),
          "The used command is not allowed");
      stmt.addBatch(
          "LOAD DATA LOCAL INFILE 'someFile' INTO TABLE LocalInfileInputStreamTest2 (id, test)");
      stmt.addBatch("SET UNIQUE_CHECKS=1");
      assertThrowsContains(
          BatchUpdateException.class, stmt::executeBatch, "The used command is not allowed");
    }
  }

  @Test
  public void wrongFile() throws Exception {
    Assumptions.assumeTrue(
        (isMariaDBServer() || !minVersion(8, 0, 3))
            && !"skysql".equals(System.getenv("srv"))
            && !"skysql-ha".equals(System.getenv("srv")));

    try (Connection con = createCon("allowLocalInfile")) {
      Statement stmt = con.createStatement();
      assertThrowsContains(
          SQLException.class,
          () ->
              stmt.execute(
                  "LOAD DATA LOCAL INFILE 'someFile' INTO TABLE LocalInfileInputStreamTest2 (id, test)"),
          "Could not send file : someFile");
      assertTrue(con.isValid(1));
    }
  }

  @Test
  public void unReadableFile() throws Exception {
    Assumptions.assumeTrue(
        (isMariaDBServer() || !minVersion(8, 0, 3))
            && !"skysql".equals(System.getenv("srv"))
            && !"skysql-ha".equals(System.getenv("srv"))
            && !System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));

    try (Connection con = createCon("allowLocalInfile")) {
      File tempFile = File.createTempFile("hello", ".tmp");
      tempFile.deleteOnExit();
      tempFile.setReadable(false);
      Statement stmt = con.createStatement();
      assertThrowsContains(
          SQLException.class,
          () ->
              stmt.execute(
                  "LOAD DATA LOCAL INFILE '"
                      + tempFile.getCanonicalPath().replace("\\", "/")
                      + "' INTO TABLE LocalInfileInputStreamTest2 (id, test)"),
          "Could not send file");
      assertTrue(con.isValid(1));
    }
  }

  @Test
  public void loadDataBasic() throws Exception {
    Assumptions.assumeTrue(
        (isMariaDBServer() || !minVersion(8, 0, 3))
            && !"skysql".equals(System.getenv("srv"))
            && !"skysql-ha".equals(System.getenv("srv")));
    File temp = File.createTempFile("dummy", ".txt");
    try (BufferedWriter bw = new BufferedWriter(new FileWriter(temp))) {
      bw.write("1\thello\n2\tworld\n");
    }

    try (Connection con = createCon("allowLocalInfile")) {
      Statement stmt = con.createStatement();
      stmt.execute(
          "LOAD DATA LOCAL INFILE '"
              + temp.getCanonicalPath().replace("\\", "/")
              + "' INTO TABLE LocalInfileInputStreamTest2 (id, test)");
      ResultSet rs = stmt.executeQuery("SELECT * FROM LocalInfileInputStreamTest2");
      assertTrue(rs.next());
      assertEquals(1, rs.getInt(1));
      assertEquals("hello", rs.getString(2));
      assertTrue(rs.next());
      assertEquals(2, rs.getInt(1));
      assertEquals("world", rs.getString(2));
      assertFalse(rs.next());

      stmt.execute("TRUNCATE LocalInfileInputStreamTest2");
      stmt.addBatch(
          "LOAD DATA LOCAL INFILE '"
              + temp.getCanonicalPath().replace("\\", "/")
              + "' INTO TABLE LocalInfileInputStreamTest2 (id, test)");
      stmt.addBatch("SET UNIQUE_CHECKS=1");
      stmt.executeBatch();

      rs = stmt.executeQuery("SELECT * FROM LocalInfileInputStreamTest2");
      assertTrue(rs.next());
      assertEquals(1, rs.getInt(1));
      assertEquals("hello", rs.getString(2));
      assertTrue(rs.next());
      assertEquals(2, rs.getInt(1));
      assertEquals("world", rs.getString(2));
      assertFalse(rs.next());
    } finally {
      temp.delete();
    }
  }

  @Test
  public void loadDataInfileEmpty() throws SQLException, IOException {
    Assumptions.assumeTrue(
        (isMariaDBServer() || !minVersion(8, 0, 3))
            && !"skysql".equals(System.getenv("srv"))
            && !"skysql-ha".equals(System.getenv("srv")));
    // Create temp file.
    File temp = File.createTempFile("validateInfile", ".tmp");
    try (Connection connection = createCon("&allowLocalInfile=true")) {
      Statement st = connection.createStatement();
      st.execute(
          "LOAD DATA LOCAL INFILE '"
              + temp.getAbsolutePath().replace('\\', '/')
              + "' INTO TABLE ldinfile");
      try (ResultSet rs = st.executeQuery("SELECT * FROM ldinfile")) {
        assertFalse(rs.next());
      }
    } finally {
      temp.delete();
    }
  }

  private File createTmpData(long recordNumber) throws Exception {
    File file = File.createTempFile("infile" + recordNumber, ".tmp");

    // write it
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      // Every row is 8 bytes to make counting easier
      for (long i = 0; i < recordNumber; i++) {
        writer.write("\"a\",\"b\"");
        writer.write("\n");
      }
    }

    return file;
  }

  private void checkBigLocalInfile(long fileSize) throws Exception {
    long recordNumber = fileSize / 8;
    try (Connection connection = createCon("allowLocalInfile")) {
      Statement stmt = connection.createStatement();
      stmt.execute("truncate `infile`");
      File file = createTmpData(recordNumber);
      int insertNumber =
          stmt.executeUpdate(
              "LOAD DATA LOCAL INFILE '"
                  + file.getCanonicalPath().replace("\\", "/")
                  + "' "
                  + "INTO TABLE `infile` "
                  + "COLUMNS TERMINATED BY ',' ENCLOSED BY '\\\"' ESCAPED BY '\\\\' "
                  + "LINES TERMINATED BY '\\n' (`a`, `b`)");
      assertEquals(insertNumber, recordNumber);
      file.delete();
      stmt.setFetchSize(1000); // to avoid using too much memory for tests
      try (ResultSet rs = stmt.executeQuery("SELECT * FROM `infile`")) {
        for (int i = 0; i < recordNumber; i++) {
          assertTrue(rs.next());
          assertEquals("a", rs.getString(1));
          assertEquals("b", rs.getString(2));
        }
        assertFalse(rs.next());
      }
    }
  }

  @Test
  public void testSmallBigLocalInfileInputStream() throws Exception {
    Assumptions.assumeTrue(
        (isMariaDBServer() || !minVersion(8, 0, 3))
            && !"skysql".equals(System.getenv("srv"))
            && !"skysql-ha".equals(System.getenv("srv")));
    checkBigLocalInfile(256);
  }

  @Test
  public void test2xBigLocalInfileInputStream() throws Exception {
    Assumptions.assumeTrue(
        (isMariaDBServer() || !minVersion(8, 0, 3))
            && !"skysql".equals(System.getenv("srv"))
            && !"skysql-ha".equals(System.getenv("srv")));
    checkBigLocalInfile(16777216 * 2);
  }

  @Test
  public void testMoreThanMaxAllowedPacketLocalInfileInputStream() throws Exception {
    Assumptions.assumeTrue(
        (isMariaDBServer() || !minVersion(8, 0, 3))
            && !"skysql".equals(System.getenv("srv"))
            && !"skysql-ha".equals(System.getenv("srv")));
    Statement stmt = sharedConn.createStatement();
    ResultSet rs = stmt.executeQuery("select @@max_allowed_packet");
    assertTrue(rs.next());
    long maxAllowedPacket = rs.getLong(1);
    Assumptions.assumeTrue(maxAllowedPacket < 100_000_000);
    checkBigLocalInfile(maxAllowedPacket + 1024);
  }
}
