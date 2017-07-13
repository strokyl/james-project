/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra.mail.migration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraConfiguration;
import org.apache.james.mailbox.cassandra.mail.AttachmentLoader;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV2;
import org.apache.james.mailbox.cassandra.mail.MessageAttachmentRepresentation;
import org.apache.james.mailbox.cassandra.mail.MessageWithoutAttachment;
import org.apache.james.mailbox.cassandra.mail.RawMessageWithoutAttachment;
import org.apache.james.mailbox.cassandra.mail.utils.Limit;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class V1ToV2Migration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V1ToV2MigrationThread.class);

    private final CassandraMessageDAO messageDAOV1;
    private final AttachmentLoader attachmentLoader;
    private final CassandraConfiguration cassandraConfiguration;
    private final ExecutorService migrationExecutor;
    private final ArrayBlockingQueue<Pair<RawMessageWithoutAttachment, Stream<MessageAttachmentRepresentation>>> messagesToBeMigrated;
    private final MigrationTracking migrationTracking;

    @Inject
    public V1ToV2Migration(CassandraMessageDAO messageDAOV1, CassandraMessageDAOV2 messageDAOV2,
                           CassandraAttachmentMapper attachmentMapper, CassandraConfiguration cassandraConfiguration,
                           MigrationTracking migrationTracking) {
        this.messageDAOV1 = messageDAOV1;
        this.attachmentLoader = new AttachmentLoader(attachmentMapper);
        this.cassandraConfiguration = cassandraConfiguration;
        this.migrationExecutor = Executors.newFixedThreadPool(cassandraConfiguration.getV1ToV2ThreadCount());
        boolean ensureFifoOrder = false;
        this.messagesToBeMigrated = new ArrayBlockingQueue<>(cassandraConfiguration.getV1ToV2QueueLength(), ensureFifoOrder);
        this.migrationTracking = migrationTracking;
        executeMigrationThread(messageDAOV1, messageDAOV2, cassandraConfiguration);
    }

    private void executeMigrationThread(CassandraMessageDAO messageDAOV1, CassandraMessageDAOV2 messageDAOV2, CassandraConfiguration cassandraConfiguration) {
        if (cassandraConfiguration.isOnTheFlyV1ToV2Migration()) {
            IntStream.range(0, cassandraConfiguration.getV1ToV2ThreadCount())
                .mapToObj(i -> new V1ToV2MigrationThread(messagesToBeMigrated, messageDAOV1, messageDAOV2, attachmentLoader, migrationTracking))
                .forEach(migrationExecutor::execute);
        }
    }

    @PreDestroy
    public void stop() {
        migrationExecutor.shutdownNow();
    }

    public CompletableFuture<Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>>>
            getFromV2orElseFromV1AfterMigration(CassandraMessageDAOV2.MessageResult result) {

        if (result.isFound()) {
            return CompletableFuture.completedFuture(result.message());
        }

        return messageDAOV1.retrieveMessages(ImmutableList.of(result.getMetadata()), MessageMapper.FetchType.Full, Limit.unlimited())
            .thenApply(
                Throwing.function(results -> results.findAny()
                    .orElseThrow(() -> new IllegalArgumentException("Message not found in DAO V1" + result.getMetadata()))))
            .thenApply(message -> {
                this.submitMigration(Pair.of(message.getLeft().toRawMessageWithoutAttachment(), message.getRight()));

                return message;
            });
    }

    private void submitMigration(
        Pair<RawMessageWithoutAttachment, Stream<MessageAttachmentRepresentation>> messageV1
    ) {
        if (cassandraConfiguration.isOnTheFlyV1ToV2Migration()) {
            synchronized (messagesToBeMigrated) {
                if (!messagesToBeMigrated.offer(messageV1)) {
                    LOGGER.info("Migration queue is full message {} is ignored", messageV1.getLeft().getMessageId());
                }
            }
        }
    }

    public void runFullMigration() {
        messageDAOV1.scanAllMessage().thenAccept(pairStream -> pairStream.forEach(this::submitMigration));
    }
}
