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

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.utils.Limit;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

import org.apache.james.util.FluentFutureStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CassandraMessageDAOTest {
    private static final int BODY_START = 16;
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final String CONTENT = "Subject: Test7 \n\nBody7\n.\n";
    private static final MessageUid messageUid = MessageUid.of(1);

    private CassandraCluster cassandra;

    private CassandraMessageDAO testee;
    private CassandraMessageId.Factory messageIdFactory;

    private SimpleMailboxMessage messageWith1Attachment;
    private CassandraMessageId messageId;
    private CassandraMessageId messageId2;
    private CassandraMessageId messageId3;
    private Attachment attachment;
    private ComposedMessageId composedMessageId;
    private List<ComposedMessageIdWithMetaData> messageIds;

    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(new CassandraMessageModule());
        cassandra.ensureAllTables();

        messageIdFactory = new CassandraMessageId.Factory();
        messageId = messageIdFactory.generate();
        messageId2 = messageIdFactory.generate();
        messageId3 = messageIdFactory.generate();
        testee = new CassandraMessageDAO(cassandra.getConf(), cassandra.getTypesProvider());

        attachment = Attachment.builder()
                .attachmentId(AttachmentId.from("123"))
                .bytes("attachment".getBytes())
                .type("content")
                .build();

        composedMessageId = new ComposedMessageId(MAILBOX_ID, messageId, messageUid);

        messageIds = ImmutableList.of(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(1)
                .build());
    }

    @After
    public void tearDown() {
        cassandra.clearAllTables();
        cassandra.close();
    }

    @Test
    public void saveShouldStoreMessageWithAttachmentAndCid() throws Exception {
        messageWith1Attachment = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(),
                ImmutableList.of(MessageAttachment.builder()
                        .attachment(attachment)
                        .cid(Cid.from("<cid>"))
                        .isInline(true)
                        .build()));

        testee.save(messageWith1Attachment).join();

        List<Optional<MessageAttachmentRepresentation>> attachmentRepresentation =
            testee.retrieveMessages(messageIds, MessageMapper.FetchType.Body, Limit.unlimited())
                .get()
                .map(pair -> pair.getRight())
                .map(streamAttachemnt -> streamAttachemnt.findFirst())
                .collect(Guavate.toImmutableList());

        Cid expectedCid = Cid.from("cid");

        assertThat(attachmentRepresentation).hasSize(1);
        assertThat(attachmentRepresentation.get(0).get().getCid().get()).isEqualTo(expectedCid);
    }

    @Test
    public void scanMessageShouldReturnAllMessageInTheTable() throws Exception {
        SimpleMailboxMessage message1 = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(),
            ImmutableList.of());

        SimpleMailboxMessage message2 = createMessage(messageId2, CONTENT, BODY_START, new PropertyBuilder(),
            ImmutableList.of());

        SimpleMailboxMessage message3 = createMessage(messageId3, CONTENT, BODY_START, new PropertyBuilder(),
            ImmutableList.of());

        FluentFutureStream.ofFutures(testee.save(message1), testee.save(message2), testee.save(message3)).join();

        List<MessageId> mails =
            testee.scanAllMessage()
                .get()
                .map(pair -> pair.getLeft().getMessageId())
                .collect(Guavate.toImmutableList());


        assertThat(mails).contains(message1.getMessageId(), message2.getMessageId(), message3.getMessageId());
    }

    @Test
    public void saveShouldStoreMessageWithAttachmentButNoCid() throws Exception {
        messageWith1Attachment = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(),
                ImmutableList.of(MessageAttachment.builder()
                        .attachment(attachment)
                        .isInline(true)
                        .build()));

        testee.save(messageWith1Attachment).join();

        List<Optional<MessageAttachmentRepresentation>> attachmentRepresentation =
            testee.retrieveMessages(messageIds, MessageMapper.FetchType.Body, Limit.unlimited())
                .get()
                .map(pair -> pair.getRight())
                .map(streamAttachemnt -> streamAttachemnt.findFirst())
                .collect(Guavate.toImmutableList());

        assertThat(attachmentRepresentation).hasSize(1);
        assertThat(attachmentRepresentation.get(0).get().getCid().isPresent()).isFalse();
    }

    private SimpleMailboxMessage createMessage(
            MessageId messageId,
            String content,
            int bodyStart,
            PropertyBuilder propertyBuilder,
            List<MessageAttachment> attachments) {

        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .mailboxId(MAILBOX_ID)
            .uid(messageUid)
            .internalDate(new Date())
            .bodyStartOctet(bodyStart)
            .size(content.length())
            .content(new SharedByteArrayInputStream(content.getBytes(Charsets.UTF_8)))
            .flags(new Flags())
            .propertyBuilder(propertyBuilder)
            .addAttachments(attachments)
            .build();
    }

}