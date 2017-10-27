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

package org.apache.james.mailbox.elasticsearch;

import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaContainer;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.junit.ClassRule;

public class ElasticSearchIntegrationTestWithAttachment extends AbstractElasticIntegrationTest {


    @ClassRule
    public static TikaContainer tika = new TikaContainer();
    private TikaTextExtractor textExtractor;

    @Override
    public void setUp() throws Exception {
        textExtractor = new TikaTextExtractor(new TikaHttpClientImpl(TikaConfiguration.builder()
            .host(tika.getIp())
            .port(tika.getPort())
            .timeoutInMillis(tika.getTimeoutInMillis())
            .build()));
        super.setUp();
    }

    @Override
    protected TextExtractor getTextExtractor() {
        return textExtractor;
    }

    @Override
    protected IndexAttachments handleAttachment() {
        return IndexAttachments.YES;
    }
}