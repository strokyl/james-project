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

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.BlobParts;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.Blobs;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.util.FluentFutureStream;
import org.apache.commons.lang3.tuple.Pair;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.utils.UUIDs;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableMap;

public class CassandraBlobsDAO {

    public static final int CHUNK_SIZE = 1024;
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insert;
    private final PreparedStatement insertPart;
    private final PreparedStatement delete;
    private final PreparedStatement select;
    private final PreparedStatement selectPart;

    @Inject
    public CassandraBlobsDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.insert = prepareInsert(session);
        this.delete = prepareDelete(session);
        this.select = prepareSelect(session);

        this.insertPart = prepareInsertPart(session);
        this.selectPart = prepareSelectPart(session);
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select()
            .from(Blobs.TABLE_NAME)
            .where(eq(Blobs.ID, bindMarker(Blobs.ID))));
    }

    private PreparedStatement prepareSelectPart(Session session) {
        return session.prepare(select()
                .from(BlobParts.TABLE_NAME)
                .where(eq(BlobParts.ID, bindMarker(BlobParts.ID))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(Blobs.TABLE_NAME)
                .value(Blobs.ID, bindMarker(Blobs.ID))
                .value(Blobs.POSITION, bindMarker(Blobs.POSITION))
                .value(Blobs.PART, bindMarker(Blobs.PART)));
    }

    private PreparedStatement prepareInsertPart(Session session) {
        return session.prepare(insertInto(BlobParts.TABLE_NAME)
                .value(BlobParts.ID, bindMarker(BlobParts.ID))
                .value(BlobParts.DATA, bindMarker(BlobParts.DATA)));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
                .from(Blobs.TABLE_NAME)
                .where(eq(Blobs.ID, bindMarker(Blobs.ID))));
    }

    public CompletableFuture<Optional<UUID>> save(byte[] data) {
        if (data == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        UUID uuid = UUIDs.timeBased();
        return FluentFutureStream.of(
            saveBlobParts(data)
                .map(pair ->
                    cassandraAsyncExecutor.executeVoid(insert.bind()
                        .setUUID(Blobs.ID, uuid)
                        .setLong(Blobs.POSITION, pair.getKey())
                        .setUUID(Blobs.PART, pair.getValue()))))
            .completableFuture()
            .thenApply(any -> Optional.of(uuid));
    }

    public InputStream read(UUID blobId) {
        ImmutableMap<Long, UUID> partIds = CassandraUtils.convertToStream(readBlob(blobId)
            .join())
            .map(row -> Pair.of(row.getLong(Blobs.POSITION), row.getUUID(Blobs.PART)))
            .collect(Guavate.toImmutableMap(Pair::getKey, Pair::getValue));
        return new SequenceInputStream(
                        new CassandraBackedInputStreamEnumeration(i -> readPart(partIds.get(Long.valueOf(i)))));
    }


    public CompletableFuture<ResultSet> readBlob(UUID blobId) {
        return cassandraAsyncExecutor.execute(select.bind()
                .setUUID(Blobs.ID, blobId));
    }

    private CompletableFuture<Optional<Row>> readPart(UUID partId) {
        if (partId == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return cassandraAsyncExecutor.executeSingleRow(selectPart.bind()
                .setUUID(BlobParts.ID, partId));
    }

    private UUID writePart(ByteBuffer data) {
        UUID partId = UUIDs.timeBased();
        cassandraAsyncExecutor.executeVoid(insertPart.bind()
                .setUUID(BlobParts.ID, partId)
                .setBytes(BlobParts.DATA, data));
        return partId;
    }

    private Stream<Pair<Integer, UUID>> saveBlobParts(byte[] data) {
        int size = data.length;
        int fullChunkCount = size / CHUNK_SIZE;

        return Stream.concat(
                IntStream.range(0, fullChunkCount)
                        .mapToObj(i -> Pair.of(i, writePart(getWrap(data, i * CHUNK_SIZE, CHUNK_SIZE)))),
                saveFinalByteBuffer(data, fullChunkCount * CHUNK_SIZE, fullChunkCount));
    }

    private Stream<Pair<Integer, UUID>> saveFinalByteBuffer(byte[] data, int offset, int index) {
        if (offset == data.length) {
            return Stream.of();
        }
        return Stream.of(Pair.of(index, writePart(getWrap(data, offset, data.length - offset))));
    }

    private ByteBuffer getWrap(byte[] data, int offset, int length) {
        return ByteBuffer.wrap(data, offset, length);
    }
}
