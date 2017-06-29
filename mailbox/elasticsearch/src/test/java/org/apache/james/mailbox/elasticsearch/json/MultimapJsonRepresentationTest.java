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

package org.apache.james.mailbox.elasticsearch.json;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;

public class MultimapJsonRepresentationTest {

    @Test
    public void constructorShouldWorkWithEmptyMultiMap() {
        Multimap<String, String> emptyMultimap = ImmutableMultimap
            .<String, String>builder()
            .build();

        MultimapJsonRepresentation testee = new MultimapJsonRepresentation(emptyMultimap);

        assertThat(testee.getPairs()).isEmpty();
    }

    @Test
    public void constructorShouldCorrectlyTransformMultiMapIntoAListOfKeyValue() {
        Multimap<String, String> emptyMultimap = ImmutableMultimap
            .<String, String>builder()
            .putAll("key", "value1", "value2")
            .putAll("key2", "value")
            .build();

        MultimapJsonRepresentation testee = new MultimapJsonRepresentation(emptyMultimap);

        assertThat(testee.getPairs())
            .containsExactly(
                new MultimapJsonRepresentation.KeyValue<>("key", ImmutableList.of("value1", "value2")),
                new MultimapJsonRepresentation.KeyValue<>("key2", ImmutableList.of("value")));
    }
}