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

package org.apache.james.mailbox.jpa.mail;

import java.util.List;
import java.util.Random;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import com.google.common.base.Preconditions;
import org.apache.commons.lang.NotImplementedException;
import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.JPAMailboxFixture;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MapperProvider;

import com.google.common.collect.ImmutableList;

public class JPAMapperProvider implements MapperProvider {

    private JpaTestCluster persistence;
    private Exception e;

    private JpaTestCluster createPersistence() {
        //Preconditions.checkState(persistence == null);
        if (persistence != null) {
            return persistence;
        }
        persistence = JpaTestCluster.create(JPAMailboxFixture.MAILBOX_PERSISTANCE_CLASSES);
        e = new Exception();
        return persistence;
    }

    @Override
    public MailboxMapper createMailboxMapper() throws MailboxException {
        return new TransactionalMailboxMapper(new JPAMailboxMapper(createPersistence().getEntityManagerFactory()));
    }

    @Override
    public MessageMapper createMessageMapper() throws MailboxException {
        EntityManagerFactory entityManagerFactory = createPersistence().getEntityManagerFactory();
        JVMMailboxPathLocker locker = new JVMMailboxPathLocker();

        JPAMessageMapper messageMapper = new JPAMessageMapper(new MockMailboxSession("benwa"), 
            new JPAUidProvider(locker, entityManagerFactory), 
            new JPAModSeqProvider(locker, entityManagerFactory), 
            entityManagerFactory);

        return new TransactionalMessageMapper(messageMapper);
    }

    @Override
    public AttachmentMapper createAttachmentMapper() throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public AnnotationMapper createAnnotationMapper() throws MailboxException {
        return new TransactionalAnnotationMapper(new JPAAnnotationMapper(createPersistence().getEntityManagerFactory()));
    }

    @Override
    public MailboxId generateId() {
        return JPAId.of(Math.abs(new Random().nextInt()));
    }

    @Override
    public MessageId generateMessageId() {
        return new DefaultMessageId.Factory().generate();
    }

    @Override
    public void clearMapper() throws MailboxException {
        persistence.clear();
        try {
            persistence.getEntityManagerFactory().close();
        } catch (Throwable e) {

        }
        persistence = null;
        e = null;

    }

    @Override
    public void ensureMapperPrepared() throws MailboxException {

    }

    @Override
    public boolean supportPartialAttachmentFetch() {
        return false;
    }

    @Override
    public List<Capabilities> getSupportedCapabilities() {
        return ImmutableList.of(Capabilities.ANNOTATION, Capabilities.MAILBOX, Capabilities.MESSAGE);
    }

    @Override
    public MessageIdMapper createMessageIdMapper() throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public MessageUid generateMessageUid() {
        throw new NotImplementedException();
    }

    @Override
    public long generateModSeq(Mailbox mailbox) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public long highestModSeq(Mailbox mailbox) throws MailboxException {
        throw new NotImplementedException();
    }
}
