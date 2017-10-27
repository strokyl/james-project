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
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.junit.Ignore;

//This test does not bring any value compare to ElasticSearchIntegrationTestWithTika except
//that it is faster. Therefore, it does not need to be run by the CI because the CI will also
//run ElasticSearchIntegrationTestWithAttachment. It's aim is just to provide to developper fast test
//when working on the ES implementation
@Ignore
public class ElasticSearchIntegrationTestWithoutAttachment extends AbstractElasticIntegrationTest {
    @Override
    protected TextExtractor getTextExtractor() {
        return new DefaultTextExtractor();
    }

    protected IndexAttachments handleAttachment() {
        return IndexAttachments.NO;
    }
}
