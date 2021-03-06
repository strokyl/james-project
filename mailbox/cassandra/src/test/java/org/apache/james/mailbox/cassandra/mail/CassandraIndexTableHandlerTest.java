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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraApplicableFlagsModule;
import org.apache.james.mailbox.cassandra.modules.CassandraFirstUnseenModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxRecentsModule;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CassandraIndexTableHandlerTest {

    public static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    public static final MessageUid MESSAGE_UID = MessageUid.of(18L);
    public static final CassandraMessageId CASSANDRA_MESSAGE_ID = new CassandraMessageId.Factory().generate();
    public static final int UID_VALIDITY = 15;
    public static final long MODSEQ = 17;

    private CassandraCluster cassandra;
    private CassandraMailboxCounterDAO mailboxCounterDAO;
    private CassandraMailboxRecentsDAO mailboxRecentsDAO;
    private CassandraApplicableFlagDAO applicableFlagDAO;
    private CassandraFirstUnseenDAO firstUnseenDAO;
    private CassandraIndexTableHandler testee;
    private Mailbox mailbox;

    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(
            new CassandraModuleComposite(
                new CassandraMailboxCounterModule(),
                new CassandraMailboxRecentsModule(),
                new CassandraFirstUnseenModule(),
                new CassandraApplicableFlagsModule()));
        cassandra.ensureAllTables();

        mailboxCounterDAO = new CassandraMailboxCounterDAO(cassandra.getConf());
        mailboxRecentsDAO = new CassandraMailboxRecentsDAO(cassandra.getConf());
        firstUnseenDAO = new CassandraFirstUnseenDAO(cassandra.getConf());
        applicableFlagDAO = new CassandraApplicableFlagDAO(cassandra.getConf());

        testee = new CassandraIndexTableHandler(mailboxRecentsDAO, mailboxCounterDAO, firstUnseenDAO, applicableFlagDAO);

        mailbox = new SimpleMailbox(new MailboxPath("#private", "user", "name"),
            UID_VALIDITY,
            MAILBOX_ID);
    }

    @After
    public void tearDown() {
        cassandra.clearAllTables();
    }

    @Test
    public void updateIndexOnAddShouldIncrementMessageCount() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);

        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        Optional<Long> actual = mailboxCounterDAO.countMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(1);
    }

    @Test
    public void updateIndexOnAddShouldIncrementUnseenMessageCountWhenUnseen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);

        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(1);
    }

    @Test
    public void updateIndexOnAddShouldNotIncrementUnseenMessageCountWhenSeen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.SEEN));
        when(message.getUid()).thenReturn(MESSAGE_UID);

        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(0);
    }

    @Test
    public void updateIndexOnAddShouldNotAddRecentWhenNoRecent() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);

        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        assertThat(mailboxRecentsDAO.getRecentMessageUidsInMailbox(MAILBOX_ID).join())
            .isEmpty();
    }

    @Test
    public void updateIndexOnAddShouldAddRecentWhenRecent() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.RECENT));
        when(message.getUid()).thenReturn(MESSAGE_UID);

        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        assertThat(mailboxRecentsDAO.getRecentMessageUidsInMailbox(MAILBOX_ID).join())
            .containsOnly(MESSAGE_UID);
    }

    @Test
    public void updateIndexOnDeleteShouldDecrementMessageCount() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnDelete(new ComposedMessageIdWithMetaData(
                new ComposedMessageId(MAILBOX_ID, CASSANDRA_MESSAGE_ID, MESSAGE_UID),
                new Flags(Flags.Flag.RECENT),
                MODSEQ),
            MAILBOX_ID).join();

        Optional<Long> actual = mailboxCounterDAO.countMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(0);
    }

    @Test
    public void updateIndexOnDeleteShouldDecrementUnseenMessageCountWhenUnseen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnDelete(new ComposedMessageIdWithMetaData(
                new ComposedMessageId(MAILBOX_ID, CASSANDRA_MESSAGE_ID, MESSAGE_UID),
                new Flags(),
                MODSEQ),
            MAILBOX_ID).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(0);
    }

    @Test
    public void updateIndexOnDeleteShouldNotDecrementUnseenMessageCountWhenSeen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnDelete(new ComposedMessageIdWithMetaData(
                new ComposedMessageId(MAILBOX_ID, CASSANDRA_MESSAGE_ID, MESSAGE_UID),
                new Flags(Flags.Flag.SEEN),
                MODSEQ),
            MAILBOX_ID).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(1);
    }

    @Test
    public void updateIndexOnDeleteShouldRemoveRecentWhenRecent() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.RECENT));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnDelete(new ComposedMessageIdWithMetaData(
                new ComposedMessageId(MAILBOX_ID, CASSANDRA_MESSAGE_ID, MESSAGE_UID),
                new Flags(Flags.Flag.RECENT),
                MODSEQ),
            MAILBOX_ID).join();

        assertThat(mailboxRecentsDAO.getRecentMessageUidsInMailbox(MAILBOX_ID).join())
            .isEmpty();
    }

    @Test
    public void updateIndexOnDeleteShouldRemoveUidFromRecentAnyway() throws Exception {
        // Clean up strategy if some flags updates missed
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.RECENT));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnDelete(new ComposedMessageIdWithMetaData(
                new ComposedMessageId(MAILBOX_ID, CASSANDRA_MESSAGE_ID, MESSAGE_UID),
                new Flags(),
                MODSEQ),
            MAILBOX_ID).join();

        assertThat(mailboxRecentsDAO.getRecentMessageUidsInMailbox(MAILBOX_ID).join())
            .isEmpty();
    }

    @Test
    public void updateIndexOnFlagsUpdateShouldNotChangeMessageCount() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        Optional<Long> actual = mailboxCounterDAO.countMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(1);
    }

    @Test
    public void updateIndexOnFlagsUpdateShouldDecrementUnseenMessageCountWhenSeenIsSet() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.SEEN))
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(0);
    }

    @Test
    public void updateIndexOnFlagsUpdateShouldIncrementUnseenMessageCountWhenSeenIsUnset() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.SEEN));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.SEEN))
            .modSeq(MODSEQ)
            .build()).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(1);
    }

    @Test
    public void updateIndexOnFlagsUpdateShouldNotChangeUnseenCountWhenBothSeen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.SEEN));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.SEEN))
            .oldFlags(new Flags(Flags.Flag.SEEN))
            .modSeq(MODSEQ)
            .build()).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(0);
    }

    @Test
    public void updateIndexOnFlagsUpdateShouldNotChangeUnseenCountWhenBothUnSeen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(1);
    }

    @Test
    public void updateIndexOnFlagsUpdateShouldAddRecentOnSettingRecentFlag() throws Exception {
        // Clean up strategy if some flags updates missed
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        assertThat(mailboxRecentsDAO.getRecentMessageUidsInMailbox(MAILBOX_ID).join())
            .containsOnly(MESSAGE_UID);
    }

    @Test
    public void updateIndexOnFlagsUpdateShouldRemoveRecentOnUnsettingRecentFlag() throws Exception {
        // Clean up strategy if some flags updates missed
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.RECENT));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .modSeq(MODSEQ)
            .build()).join();

        assertThat(mailboxRecentsDAO.getRecentMessageUidsInMailbox(MAILBOX_ID).join())
            .isEmpty();
    }

    @Test
    public void updateIndexOnAddShouldUpdateFirstUnseenWhenUnseen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(MESSAGE_UID);
    }

    @Test
    public void updateIndexOnAddShouldNotUpdateFirstUnseenWhenSeen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.SEEN));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    public void updateIndexOnFlagsUpdateShouldUpdateLastUnseenWhenSetToSeen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.SEEN))
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    public void updateIndexOnFlagsUpdateShouldUpdateLastUnseenWhenSetToUnseen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.SEEN));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.SEEN))
            .modSeq(MODSEQ)
            .build()).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(MESSAGE_UID);
    }

    @Test
    public void updateIndexOnFlagsUpdateShouldNotUpdateLastUnseenWhenKeepUnseen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(MESSAGE_UID);
    }

    @Test
    public void updateIndexOnFlagsUpdateShouldNotUpdateLastUnseenWhenKeepSeen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.SEEN));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.SEEN))
            .oldFlags(new Flags(Flags.Flag.SEEN))
            .modSeq(MODSEQ)
            .build()).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    public void updateIndexOnDeleteShouldUpdateFirstUnseenWhenUnseen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnDelete(new ComposedMessageIdWithMetaData(
            new ComposedMessageId(MAILBOX_ID, CASSANDRA_MESSAGE_ID, MESSAGE_UID),
            new Flags(),
            MODSEQ), MAILBOX_ID).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    public void updateIndexOnAddShouldUpdateApplicableFlag() throws Exception {
        Flags answeredFlag = new Flags(Flag.ANSWERED);
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(answeredFlag);
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        Flags applicableFlag = applicableFlagDAO.retrieveApplicableFlag(MAILBOX_ID).join().get();

        assertThat(applicableFlag).isEqualTo(answeredFlag);
    }

    @Test
    public void updateIndexOnFlagsUpdateShouldUnionApplicableFlag() throws Exception {
        Flags answeredFlag = new Flags(Flag.ANSWERED);
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(answeredFlag);
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flag.DELETED))
            .oldFlags(answeredFlag)
            .modSeq(MODSEQ)
            .build()).join();

        Flags applicableFlag = applicableFlagDAO.retrieveApplicableFlag(MAILBOX_ID).join().get();

        assertThat(applicableFlag).isEqualTo(new FlagsBuilder().add(Flag.ANSWERED, Flag.DELETED).build());
    }

    @Test
    public void applicableFlagShouldKeepAllFlagsEvenTheMessageRemovesFlag() throws Exception {
        Flags messageFlags = new Flags(Flag.ANSWERED);
        messageFlags.add(Flag.DELETED);
        messageFlags.add(Flag.DRAFT);

        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(messageFlags);
        when(message.getUid()).thenReturn(MESSAGE_UID);

        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(messageFlags)
            .modSeq(MODSEQ)
            .build()).join();

        Flags applicableFlag = applicableFlagDAO.retrieveApplicableFlag(MAILBOX_ID).join().get();
        assertThat(applicableFlag).isEqualTo(new FlagsBuilder().add(Flag.ANSWERED, Flag.DRAFT, Flag.DELETED).build());
    }
}
