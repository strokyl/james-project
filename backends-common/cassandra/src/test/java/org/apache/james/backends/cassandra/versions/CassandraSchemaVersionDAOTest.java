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

package org.apache.james.backends.cassandra.versions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CassandraSchemaVersionDAOTest {

    private CassandraCluster cassandra;

    private CassandraSchemaVersionDAO testee;

    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(new CassandraSchemaVersionModule());
        cassandra.ensureAllTables();

        testee = new CassandraSchemaVersionDAO(cassandra.getConf());
    }

    @After
    public void tearDown() {
        cassandra.clearAllTables();
        cassandra.close();
    }

    @Test
    public void getCurrentSchemaVersionShouldReturn1WhenTableIsEmpty() {
        assertThat(testee.getCurrentSchemaVersion().join().isPresent()).isFalse();
    }

    @Test
    public void getCurrentSchemaVersionShouldReturnVersionPresentInTheTable() {
        int version = 42;

        testee.updateVersion(version).join();

        assertThat(testee.getCurrentSchemaVersion().join()).contains(version);
    }

    @Test
    public void getCurrentSchemaVersionShouldBeIdempotent() {
        int version = 42;

        testee.updateVersion(version + 1).join();
        testee.updateVersion(version).join();

        assertThat(testee.getCurrentSchemaVersion().join()).contains(version + 1);
    }
}