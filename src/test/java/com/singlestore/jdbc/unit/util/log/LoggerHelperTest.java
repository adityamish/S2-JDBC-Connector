// SPDX-License-Identifier: LGPL-2.1-or-later
// Copyright (c) 2012-2014 Monty Program Ab
// Copyright (c) 2015-2021 MariaDB Corporation Ab
// Copyright (c) 2021 SingleStore, Inc.

package com.singlestore.jdbc.unit.util.log;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.singlestore.jdbc.util.log.LoggerHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LoggerHelperTest {

  @Test
  void hex() {
    byte[] bb =
        new byte[] {
          0x4A, 0x00, 0x00, 0x00, 0x03, 0x53, 0x45, 0x4C, 0x45,
          0x43, 0x54, 0x20, 0x40, 0x40, 0x6D, 0x61, 0x78, 0x5F,
          0x61, 0x6C, 0x6C, 0x6F, 0x77, 0x65, 0x64, 0x5F, 0x70,
          0x61, 0x63, 0x6B, 0x65, 0x74, 0x20, 0x2C, 0x20, 0x40,
          0x40, 0x73, 0x79, 0x73, 0x74, 0x65, 0x6D, 0x5F, 0x74,
          0x69, 0x6D, 0x65, 0x5F, 0x7A, 0x6F, 0x6E, 0x65, 0x2C,
          0x20, 0x40, 0x40, 0x74, 0x69, 0x6D, 0x65, 0x5F, 0x7A,
          0x6F, 0x6E, 0x65, 0x2C, 0x20, 0x40, 0x40, 0x73, 0x71,
          0x6C, 0x5F, 0x6D, 0x6F, 0x64, 0x65, 0x64, 0x65
        };

    Assertions.assertEquals("", LoggerHelper.hex(null, 0, 2));
    Assertions.assertEquals("", LoggerHelper.hex(new byte[0], 0, 2));

    Assertions.assertEquals(
        "+--------------------------------------------------+\n"
            + "|  0  1  2  3  4  5  6  7   8  9  a  b  c  d  e  f |\n"
            + "+--------------------------------------------------+------------------+\n"
            + "| 4A 00 00 00 03 53 45 4C  45 43 54 20 40 40 6D 61 | J....SELECT @@ma |\n"
            + "| 78 5F 61 6C 6C 6F 77 65  64 5F 70 61 63 6B 65 74 | x_allowed_packet |\n"
            + "| 20 2C 20 40 40 73 79 73  74 65 6D 5F 74 69 6D 65 |  , @@system_time |\n"
            + "| 5F 7A 6F 6E 65 2C 20 40  40 74 69 6D 65 5F 7A 6F | _zone, @@time_zo |\n"
            + "| 6E 65 2C 20 40 40 73 71  6C 5F 6D 6F 64 65       | ne, @@sql_mode   |\n"
            + "+--------------------------------------------------+------------------+\n",
        LoggerHelper.hex(bb, 0, bb.length - 2));
    Assertions.assertEquals(
        "+--------------------------------------------------+\n"
            + "|  0  1  2  3  4  5  6  7   8  9  a  b  c  d  e  f |\n"
            + "+--------------------------------------------------+------------------+\n"
            + "| 4A 00 00 00 03 53 45 4C  45 43 54 20 40 40 6D 61 | J....SELECT @@ma |\n"
            + "+-------------------truncated----------------------+------------------+\n",
        LoggerHelper.hex(bb, 0, bb.length - 2, 16));
    assertEquals(
        "+--------------------------------------------------+\n"
            + "|  0  1  2  3  4  5  6  7   8  9  a  b  c  d  e  f |\n"
            + "+--------------------------------------------------+------------------+\n"
            + "| 4A 00 00 00 03 53 45 4C  45 43 54 20 40 40 6D 61 | J....SELECT @@ma |\n"
            + "| 78 5F 61 6C 6C 6F 77 65  64 5F 70 61 63 6B 65 74 | x_allowed_packet |\n"
            + "| 20 2C 20 40 40 73 79 73  74 65 6D 5F 74 69 6D 65 |  , @@system_time |\n"
            + "| 5F 7A 6F 6E 65 2C 20 40  40 74 69 6D 65 5F 7A 6F | _zone, @@time_zo |\n"
            + "| 6E 65 2C 20 40 40 73 71  6C 5F 6D 6F 64 65 64 65 | ne, @@sql_modede |\n"
            + "+--------------------------------------------------+------------------+\n",
        LoggerHelper.hex(bb, 0, bb.length));

    assertEquals(
        "+--------------------------------------------------+\n"
            + "|  0  1  2  3  4  5  6  7   8  9  a  b  c  d  e  f |\n"
            + "+--------------------------------------------------+------------------+\n"
            + "| 00 00 00 03 53 45 4C 45  43 54 20 40 40 6D 61 78 | ....SELECT @@max |\n"
            + "+--------------------------------------------------+------------------+\n",
        LoggerHelper.hex(bb, 1, 16));
    assertEquals(
        "+--------------------------------------------------+\n"
            + "|  0  1  2  3  4  5  6  7   8  9  a  b  c  d  e  f |\n"
            + "+--------------------------------------------------+------------------+\n"
            + "| 00 00 00 03 53 45 4C 45  43 54 20 40 40 6D 61 78 | ....SELECT @@max |\n"
            + "| 5F                                               | _                |\n"
            + "+--------------------------------------------------+------------------+\n",
        LoggerHelper.hex(bb, 1, 17));

    byte[] header = new byte[] {1, 2, 3, 4};
    assertEquals(
        "+--------------------------------------------------+\n"
            + "|  0  1  2  3  4  5  6  7   8  9  a  b  c  d  e  f |\n"
            + "+--------------------------------------------------+------------------+\n"
            + "| 01 02 03 04 00 00 00 03  53 45 4C 45 43 54 20 40 | ........SELECT @ |\n"
            + "| 40 6D 61 78                                      | @max             |\n"
            + "+--------------------------------------------------+------------------+\n",
        LoggerHelper.hex(header, bb, 1, 16, Integer.MAX_VALUE));

    assertEquals(
        "+--------------------------------------------------+\n"
            + "|  0  1  2  3  4  5  6  7   8  9  a  b  c  d  e  f |\n"
            + "+--------------------------------------------------+------------------+\n"
            + "| 01 02 03 04 00 00 00 03  53 45 4C 45 43 54 20 40 | ........SELECT @ |\n"
            + "| 40                                               | @                |\n"
            + "+-------------------truncated----------------------+------------------+\n",
        LoggerHelper.hex(header, bb, 1, 16, 17));
  }
}
