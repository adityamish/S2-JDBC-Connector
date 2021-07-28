// SPDX-License-Identifier: LGPL-2.1-or-later
// Copyright (c) 2012-2014 Monty Program Ab
// Copyright (c) 2015-2021 MariaDB Corporation Ab

package org.mariadb.jdbc.codec.list;

import java.io.IOException;
import java.sql.SQLDataException;
import java.util.Calendar;
import org.mariadb.jdbc.client.ReadableByteBuf;
import org.mariadb.jdbc.client.context.Context;
import org.mariadb.jdbc.client.socket.PacketWriter;
import org.mariadb.jdbc.codec.DataType;
import org.mariadb.jdbc.message.server.ColumnDefinitionPacket;
import org.mariadb.jdbc.plugin.Codec;
import org.mariadb.jdbc.type.Geometry;
import org.mariadb.jdbc.type.MultiPoint;
import org.mariadb.jdbc.type.Point;

public class MultiPointCodec implements Codec<MultiPoint> {

  public static final MultiPointCodec INSTANCE = new MultiPointCodec();

  public String className() {
    return MultiPoint.class.getName();
  }

  public boolean canDecode(ColumnDefinitionPacket column, Class<?> type) {
    return column.getType() == DataType.GEOMETRY && type.isAssignableFrom(MultiPoint.class);
  }

  public boolean canEncode(Object value) {
    return value instanceof MultiPoint;
  }

  @Override
  public MultiPoint decodeText(
      ReadableByteBuf buf, int length, ColumnDefinitionPacket column, Calendar cal)
      throws SQLDataException {
    return decodeBinary(buf, length, column, cal);
  }

  @Override
  public MultiPoint decodeBinary(
      ReadableByteBuf buf, int length, ColumnDefinitionPacket column, Calendar cal)
      throws SQLDataException {
    if (column.getType() == DataType.GEOMETRY) {
      buf.skip(4); // SRID
      Geometry geo = Geometry.getGeometry(buf, length - 4, column);
      if (geo instanceof MultiPoint) return (MultiPoint) geo;
      throw new SQLDataException(
          String.format(
              "Geometric type %s cannot be decoded as MultiPoint", geo.getClass().getName()));
    }
    buf.skip(length);
    throw new SQLDataException(
        String.format("Data type %s cannot be decoded as MultiPoint", column.getType()));
  }

  @Override
  public void encodeText(
      PacketWriter encoder, Context context, Object value, Calendar cal, Long maxLength)
      throws IOException {
    encoder.writeBytes(("ST_MPointFromText('" + value.toString() + "')").getBytes());
  }

  @Override
  public void encodeBinary(PacketWriter encoder, Object value, Calendar cal, Long maxLength)
      throws IOException {
    MultiPoint mp = (MultiPoint) value;
    encoder.writeLength(13 + mp.getPoints().length * 21L);
    encoder.writeInt(0); // SRID
    encoder.writeByte(0x01); // LITTLE ENDIAN
    encoder.writeInt(4); // wkbMultiPoint
    encoder.writeInt(mp.getPoints().length);
    for (Point pt : mp.getPoints()) {
      encoder.writeByte(0x01); // LITTLE ENDIAN
      encoder.writeInt(1); // wkbPoint
      encoder.writeDouble(pt.getX());
      encoder.writeDouble(pt.getY());
    }
  }

  public int getBinaryEncodeType() {
    return DataType.BLOB.get();
  }
}
