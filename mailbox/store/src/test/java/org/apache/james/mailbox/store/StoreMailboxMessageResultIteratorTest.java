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

package org.apache.james.mailbox.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MutableMailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.MessageUtil;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.assertj.core.api.iterable.Extractor;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class StoreMailboxMessageResultIteratorTest {

    private final class TestFetchGroup implements FetchGroup {
        @Override
        public Set<PartContentDescriptor> getPartContentDescriptors() {
            return new HashSet<PartContentDescriptor>();
        }

        @Override
        public int content() {
            return FetchGroup.MINIMAL;
        }
    }

    private final class TestMessageMapper implements MessageMapper {


        private final MessageRange messageRange;

        public TestMessageMapper(MessageRange messageRange) {
            this.messageRange = messageRange;
        }

        @Override
        public Iterator<MessageUid> listAllMessageUids(Mailbox mailbox) throws MailboxException {
            return messageRange.iterator();
        }

        @Override
        public void endRequest() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T execute(Transaction<T> transaction) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public MailboxCounters getMailboxCounters(Mailbox mailbox) throws MailboxException {
            return MailboxCounters.builder()
                .count(countMessagesInMailbox(mailbox))
                .unseen(countUnseenMessagesInMailbox(mailbox))
                .build();
        }

        @Override
        public Iterator<MutableMailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set,
                                                      org.apache.james.mailbox.store.mail.MessageMapper.FetchType type, int limit)
            throws MailboxException {

            List<MutableMailboxMessage> messages = new ArrayList<MutableMailboxMessage>();
            for (MessageUid uid: Iterables.limit(set, limit)) {
                if (messageRange.includes(uid)) {
                    messages.add(createMessage(uid));
                }
            }
            return messages.iterator();
        }

        private MutableMailboxMessage createMessage(MessageUid uid) {
            MutableMailboxMessage message = MessageUtil.buildMutableMailboxMessage()
                .messageId(new DefaultMessageId())
                .internalDate(new Date())
                .bodyStartOctet(0)
                .size(0)
                .content(new SharedByteArrayInputStream("".getBytes(Charsets.UTF_8)))
                .flags(new Flags())
                .propertyBuilder(new PropertyBuilder())
                .mailboxId(TestId.of(1L))
                .uid(uid)
                .attachments(ImmutableList.<MessageAttachment>of())
                .build();

            return message;
        }

        @Override
        public Map<MessageUid, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox mailbox, MessageRange set)
            throws MailboxException {
            throw new UnsupportedOperationException();

        }

        @Override
        public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
            throw new UnsupportedOperationException();

        }

        @Override
        public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(Mailbox mailbox, MailboxMessage message) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {
            throw new UnsupportedOperationException();

        }

        @Override
        public MessageMetaData add(Mailbox mailbox, MutableMailboxMessage message) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator calculator, MessageRange set) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageMetaData copy(Mailbox mailbox, MailboxMessage original) throws MailboxException {
            throw new UnsupportedOperationException();

        }

        @Override
        public Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getHighestModSeq(Mailbox mailbox) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageMetaData move(Mailbox mailbox, MailboxMessage original) throws MailboxException {
            throw new UnsupportedOperationException();

        }

        @Override
        public Flags getApplicableFlag(Mailbox mailbox) throws MailboxException {
            throw new NotImplementedException();
        }
    }

    @Test
    public void testBatching() {
        MessageRange range = MessageRange.range(MessageUid.of(1), MessageUid.of(10));
        BatchSizes batchSize = BatchSizes.uniqueBatchSize(3);
        StoreMessageResultIterator it = new StoreMessageResultIterator(new TestMessageMapper(MessageRange.all()), null, range, batchSize, new TestFetchGroup());

        assertThat(it).extracting(new Extractor<MessageResult, Long>(){
            @Override
            public Long extract(MessageResult input) {
                return input.getUid().asLong();
            }
        }).containsExactly(1l, 2l, 3l, 4l, 5l, 6l, 7l, 8l, 9l, 10l);
    }

    @Test
    public void nextShouldReturnFirstElement() {
        MessageRange range = MessageUid.of(1).toRange();
        BatchSizes batchSize = BatchSizes.uniqueBatchSize(42);
        StoreMessageResultIterator iterator = new StoreMessageResultIterator(new TestMessageMapper(range), null, range, batchSize, new TestFetchGroup());
        assertThat(iterator.next()).isNotNull();
    }

    @Test(expected=NoSuchElementException.class)
    public void nextShouldThrowWhenNoElement() {
        MessageRange messages = MessageUid.of(1).toRange();
        MessageRange findRange = MessageUid.of(2).toRange();
        BatchSizes batchSize = BatchSizes.uniqueBatchSize(42);
        StoreMessageResultIterator iterator = new StoreMessageResultIterator(new TestMessageMapper(messages), null, findRange, batchSize, new TestFetchGroup());
        iterator.next();
    }

    @Test
    public void hasNextShouldReturnFalseWhenNoElement() {
        MessageRange messages = MessageUid.of(1).toRange();
        MessageRange findRange = MessageUid.of(2).toRange();
        BatchSizes batchSize = BatchSizes.uniqueBatchSize(42);
        StoreMessageResultIterator iterator = new StoreMessageResultIterator(new TestMessageMapper(messages), null, findRange, batchSize, new TestFetchGroup());
        assertThat(iterator.hasNext()).isFalse();
    }
}
