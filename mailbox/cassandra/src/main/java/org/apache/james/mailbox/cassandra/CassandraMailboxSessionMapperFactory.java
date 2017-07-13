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

package org.apache.james.mailbox.cassandra;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.mail.CassandraAnnotationMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraApplicableFlagDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraFirstUnseenDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraIndexTableHandler;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.cassandra.mail.*;
import org.apache.james.mailbox.cassandra.mail.migration.MigrationTracking;
import org.apache.james.mailbox.cassandra.mail.migration.V1ToV2Migration;
import org.apache.james.mailbox.cassandra.user.CassandraSubscriptionMapper;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.user.SubscriptionMapper;

import com.datastax.driver.core.Session;

/**
 * Cassandra implementation of {@link MailboxSessionMapperFactory}
 * 
 */
public class CassandraMailboxSessionMapperFactory extends MailboxSessionMapperFactory {
    private final Session session;
    private final CassandraUidProvider uidProvider;
    private final CassandraModSeqProvider modSeqProvider;
    private final CassandraMessageDAO messageDAO;
    private final CassandraMessageDAOV2 messageDAOV2;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMailboxCounterDAO mailboxCounterDAO;
    private final CassandraMailboxRecentsDAO mailboxRecentsDAO;
    private final CassandraIndexTableHandler indexTableHandler;
    private final CassandraMailboxDAO mailboxDAO;
    private final CassandraMailboxPathDAO mailboxPathDAO;
    private final CassandraFirstUnseenDAO firstUnseenDAO;
    private final CassandraApplicableFlagDAO applicableFlagDAO;
    private final V1ToV2Migration v1ToV2Migration;
    private CassandraUtils cassandraUtils;
    private CassandraConfiguration cassandraConfiguration;
    private final CassandraDeletedMessageDAO deletedMessageDAO;

    @Inject
    public CassandraMailboxSessionMapperFactory(CassandraUidProvider uidProvider, CassandraModSeqProvider modSeqProvider, Session session,
                                                CassandraMessageDAO messageDAO, CassandraMessageDAOV2 messageDAOV2,
                                                CassandraMessageIdDAO messageIdDAO, CassandraMessageIdToImapUidDAO imapUidDAO,
                                                CassandraMailboxCounterDAO mailboxCounterDAO, CassandraMailboxRecentsDAO mailboxRecentsDAO, CassandraMailboxDAO mailboxDAO,
                                                CassandraMailboxPathDAO mailboxPathDAO, CassandraFirstUnseenDAO firstUnseenDAO, CassandraApplicableFlagDAO applicableFlagDAO,
                                                CassandraDeletedMessageDAO deletedMessageDAO, V1ToV2Migration v1ToV2Migration, CassandraUtils cassandraUtils, CassandraConfiguration cassandraConfiguration) {
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
        this.session = session;
        this.messageDAO = messageDAO;
        this.messageDAOV2 = messageDAOV2;
        this.messageIdDAO = messageIdDAO;
        this.imapUidDAO = imapUidDAO;
        this.mailboxCounterDAO = mailboxCounterDAO;
        this.mailboxRecentsDAO = mailboxRecentsDAO;
        this.mailboxDAO = mailboxDAO;
        this.mailboxPathDAO = mailboxPathDAO;
        this.firstUnseenDAO = firstUnseenDAO;
        this.deletedMessageDAO = deletedMessageDAO;
        this.applicableFlagDAO = applicableFlagDAO;
        this.cassandraUtils = cassandraUtils;
        this.cassandraConfiguration = cassandraConfiguration;
        this.indexTableHandler = new CassandraIndexTableHandler(
            mailboxRecentsDAO,
            mailboxCounterDAO,
            firstUnseenDAO,
            applicableFlagDAO,
            deletedMessageDAO);
        this.v1ToV2Migration = v1ToV2Migration;
    }

    public CassandraMailboxSessionMapperFactory(
        CassandraUidProvider uidProvider,
        CassandraModSeqProvider modSeqProvider,
        Session session,
        CassandraMessageDAO messageDAO,
        CassandraMessageDAOV2 messageDAOV2,
        CassandraMessageIdDAO messageIdDAO,
        CassandraMessageIdToImapUidDAO imapUidDAO,
        CassandraMailboxCounterDAO mailboxCounterDAO,
        CassandraMailboxRecentsDAO mailboxRecentsDAO,
        CassandraMailboxDAO mailboxDAO,
        CassandraMailboxPathDAO mailboxPathDAO,
        CassandraFirstUnseenDAO firstUnseenDAO,
        CassandraApplicableFlagDAO applicableFlagDAO,
        CassandraDeletedMessageDAO deletedMesageDAO) {

        this(uidProvider,
            modSeqProvider,
            session,
            messageDAO,
            messageDAOV2,
            messageIdDAO,
            imapUidDAO,
            mailboxCounterDAO,
            mailboxRecentsDAO,
            mailboxDAO,
            mailboxPathDAO,
            firstUnseenDAO,
            applicableFlagDAO,
            deletedMesageDAO,
            new V1ToV2Migration(
                messageDAO,
                messageDAOV2,
                new CassandraAttachmentMapper(session),
                CassandraConfiguration.DEFAULT_CONFIGURATION,
                new MigrationTracking()),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION,
            CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    @Override
    public CassandraMessageMapper createMessageMapper(MailboxSession mailboxSession) {
        return new CassandraMessageMapper(
                                          uidProvider,
                                          modSeqProvider,
                                          null,
                                          (CassandraAttachmentMapper) createAttachmentMapper(mailboxSession),
                                          messageDAOV2,
                                          messageIdDAO,
                                          imapUidDAO,
                                          mailboxCounterDAO,
                                          mailboxRecentsDAO,
                                          applicableFlagDAO,
                                          indexTableHandler,
                                          firstUnseenDAO,
                                          deletedMessageDAO,
                                          v1ToV2Migration,
                                          cassandraConfiguration);
    }

    @Override
    public MessageIdMapper createMessageIdMapper(MailboxSession mailboxSession) throws MailboxException {
        return new CassandraMessageIdMapper(getMailboxMapper(mailboxSession), mailboxDAO,
                (CassandraAttachmentMapper) getAttachmentMapper(mailboxSession),
                imapUidDAO, messageIdDAO, messageDAO, messageDAOV2, indexTableHandler, modSeqProvider, mailboxSession,
                v1ToV2Migration, cassandraConfiguration);
    }

    @Override
    public MailboxMapper createMailboxMapper(MailboxSession mailboxSession) {
        return new CassandraMailboxMapper(session, mailboxDAO, mailboxPathDAO, cassandraConfiguration);
    }

    @Override
    public AttachmentMapper createAttachmentMapper(MailboxSession mailboxSession) {
        return new CassandraAttachmentMapper(session);
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession mailboxSession) {
        return new CassandraSubscriptionMapper(session, cassandraUtils);
    }

    public ModSeqProvider getModSeqProvider() {
        return modSeqProvider;
    }

    public UidProvider getUidProvider() {
        return uidProvider;
    }

    Session getSession() {
        return session;
    }

    @Override
    public AnnotationMapper createAnnotationMapper(MailboxSession mailboxSession)
            throws MailboxException {
        return new CassandraAnnotationMapper(session, cassandraUtils);
    }
}
