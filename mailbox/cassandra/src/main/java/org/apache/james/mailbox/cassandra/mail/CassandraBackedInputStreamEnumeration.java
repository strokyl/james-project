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

import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.BlobParts;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.datastax.driver.core.Row;
import com.google.common.base.Preconditions;

public class CassandraBackedInputStreamEnumeration implements Enumeration<InputStream> {
    private final Function<Integer, CompletableFuture<Optional<Row>>> partFetcher;
    private int nextPartOffset;
    private CompletableFuture<Optional<Row>> preFetch;
    private Optional<Row> nextRow;

    public CassandraBackedInputStreamEnumeration(Function<Integer, CompletableFuture<Optional<Row>>> partFetcher) {
        this.partFetcher = partFetcher;
        this.nextPartOffset = 0;
        this.nextRow = Optional.empty();
        fetch();
    }

    private void fetch() {
        preFetch = partFetcher.apply(nextPartOffset);
        nextPartOffset++;
    }

    @Override
    public boolean hasMoreElements() {
        CompletableFuture<Optional<Row>> nextRowFuture = preFetch;
        fetch();
        nextRow = nextRowFuture.join();
        return nextRow.isPresent();
    }

    @Override
    public InputStream nextElement() {
        Preconditions.checkState(nextRow.isPresent(), "Expecting content while calling nextElement. Have you called 'hasMoreElements' before?");
        return new ByteArrayInputStream(partFetcher(nextRow.get()));
    }

    private byte[] partFetcher(Row row) {
        byte[] data = new byte[row.getBytes(BlobParts.DATA).remaining()];
        row.getBytes(BlobParts.DATA).get(data);
        return data;
    }
}
