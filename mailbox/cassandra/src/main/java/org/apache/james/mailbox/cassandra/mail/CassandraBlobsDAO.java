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
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.BlobId;
import org.apache.james.mailbox.cassandra.mail.utils.DataChunker;
import org.apache.james.mailbox.cassandra.table.BlobTable;
import org.apache.james.mailbox.cassandra.table.BlobTable.BlobParts;
import org.apache.james.util.FluentFutureStream;
import org.apache.james.util.OptionalConverter;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;


public class CassandraBlobsDAO {

    public static final int CHUNK_SIZE = 1024 * 100;
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insert;
    private final PreparedStatement insertPart;
    private final PreparedStatement select;
    private final PreparedStatement selectPart;
    private final DataChunker dataChunker;

    @Inject
    public CassandraBlobsDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.dataChunker = new DataChunker();
        this.insert = prepareInsert(session);
        this.select = prepareSelect(session);

        this.insertPart = prepareInsertPart(session);
        this.selectPart = prepareSelectPart(session);
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select()
            .from(BlobTable.TABLE_NAME)
            .where(eq(BlobTable.ID, bindMarker(BlobTable.ID))));
    }

    private PreparedStatement prepareSelectPart(Session session) {
        return session.prepare(select()
            .from(BlobParts.TABLE_NAME)
            .where(eq(BlobTable.ID, bindMarker(BlobTable.ID)))
            .and(eq(BlobParts.CHUNK_NUMBER, bindMarker(BlobParts.CHUNK_NUMBER))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(BlobTable.TABLE_NAME)
            .value(BlobTable.ID, bindMarker(BlobTable.ID))
            .value(BlobTable.NUMBER_OF_CHUNK, bindMarker(BlobTable.NUMBER_OF_CHUNK)));
    }

    private PreparedStatement prepareInsertPart(Session session) {
        return session.prepare(insertInto(BlobParts.TABLE_NAME)
            .value(BlobTable.ID, bindMarker(BlobTable.ID))
            .value(BlobParts.CHUNK_NUMBER, bindMarker(BlobParts.CHUNK_NUMBER))
            .value(BlobParts.DATA, bindMarker(BlobParts.DATA)));
    }

    public CompletableFuture<Optional<BlobId>> save(byte[] data) {
        if (data == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        BlobId blobId = BlobId.forPayload(data);
        return saveBlobParts(data, blobId)
            .thenCompose(numberOfChunk-> saveBlobPartsReferences(blobId, numberOfChunk))
            .thenApply(any -> Optional.of(blobId));
    }

    private CompletableFuture<Integer> saveBlobParts(byte[] data, BlobId blobId) {
        return FluentFutureStream.of(
            dataChunker.chunk(data, CHUNK_SIZE)
                .map(pair -> writePart(pair.getRight(), blobId, pair.getKey())
                    .thenApply(partId -> Pair.of(pair.getKey(), partId))))
            .completableFuture()
            .thenApply(stream ->
                getLastOfStream(stream)
                    .map(numOfChunkAndPartId -> numOfChunkAndPartId.getLeft() + 1)
                    .orElse(0));
    }

    private static <T> Optional<T> getLastOfStream(Stream<T> stream) {
        return stream.reduce((first, second) -> second);
    }

    private CompletableFuture<Void> writePart(ByteBuffer data, BlobId blobId, int position) {
        return cassandraAsyncExecutor.executeVoid(
            insertPart.bind()
                .setString(BlobTable.ID, blobId.getId())
                .setInt(BlobParts.CHUNK_NUMBER, position)
                .setBytes(BlobParts.DATA, data));
    }

    private CompletableFuture<Void> saveBlobPartsReferences(BlobId blobId, int numberOfChunk) {
        return cassandraAsyncExecutor.executeVoid(insert.bind()
            .setString(BlobTable.ID, blobId.getId())
            .setInt(BlobTable.NUMBER_OF_CHUNK, numberOfChunk));
    }

    public CompletableFuture<byte[]> read(BlobId blobId) {
        return cassandraAsyncExecutor.executeSingleRow(
                select.bind()
                        .setString(BlobTable.ID, blobId.getId()))
                .thenApply(optionalRow ->
                        optionalRow
                                .map(row ->
                                        toDataParts(BlobId.from(row.getString(BlobTable.ID)),
                                                    row.getInt(BlobTable.NUMBER_OF_CHUNK))))
                .thenCompose(optionalRow ->
                        optionalRow.orElse(CompletableFuture.completedFuture(Stream.empty())))
                .thenApply(this::concatenateDataParts);
    }


    private CompletableFuture<Stream<Optional<Row>>> toDataParts(BlobId blobId, int numOfChunk) {
        return FluentFutureStream.of(
            IntStream.range(0, numOfChunk)
            .mapToObj( position -> readPart(blobId, position)))
        .completableFuture();
    }

    private byte[] concatenateDataParts(Stream<Optional<Row>> rows) {
        ImmutableList<byte[]> parts = rows.flatMap(OptionalConverter::toStream)
            .map(this::rowToData)
            .collect(Guavate.toImmutableList());

        return Bytes.concat(parts.toArray(new byte[parts.size()][]));
    }

    private byte[] rowToData(Row row) {
        byte[] data = new byte[row.getBytes(BlobParts.DATA).remaining()];
        row.getBytes(BlobParts.DATA).get(data);
        return data;
    }

    private CompletableFuture<Optional<Row>> readPart(BlobId blobId, int position) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectPart.bind()
                .setString(BlobTable.ID, blobId.getId())
                .setInt(BlobParts.CHUNK_NUMBER, position));
    }
}
