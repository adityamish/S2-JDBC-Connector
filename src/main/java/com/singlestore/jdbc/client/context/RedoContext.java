// SPDX-License-Identifier: LGPL-2.1-or-later
// Copyright (c) 2012-2014 Monty Program Ab
// Copyright (c) 2015-2021 MariaDB Corporation Ab

package com.singlestore.jdbc.client.context;

import com.singlestore.jdbc.Configuration;
import com.singlestore.jdbc.client.PrepareCache;
import com.singlestore.jdbc.client.TransactionSaver;
import com.singlestore.jdbc.message.client.ClientMessage;
import com.singlestore.jdbc.message.client.RedoableClientMessage;
import com.singlestore.jdbc.message.server.InitialHandshakePacket;
import com.singlestore.jdbc.util.constants.ServerStatus;
import com.singlestore.jdbc.util.exceptions.ExceptionFactory;

public class RedoContext extends BaseContext {

  private final TransactionSaver transactionSaver;

  public RedoContext(
      InitialHandshakePacket handshake,
      long clientCapabilities,
      Configuration conf,
      ExceptionFactory exceptionFactory,
      PrepareCache prepareCache) {
    super(handshake, clientCapabilities, conf, exceptionFactory, prepareCache);
    this.transactionSaver = new TransactionSaver();
  }

  public void setServerStatus(int serverStatus) {
    this.serverStatus = serverStatus;
    if ((serverStatus & ServerStatus.IN_TRANSACTION) == 0) transactionSaver.clear();
  }

  public void saveRedo(ClientMessage msg) {
    if (msg instanceof RedoableClientMessage) {
      RedoableClientMessage redoMsg = (RedoableClientMessage) msg;
      redoMsg.saveParameters();
      transactionSaver.add(redoMsg);
    }
  }

  public void saveRedo(ClientMessage[] msgs) {
    for (ClientMessage msg : msgs) saveRedo(msg);
  }

  public TransactionSaver getTransactionSaver() {
    return transactionSaver;
  }
}
