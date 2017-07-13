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

package org.apache.james.mailbox.store.mail.model.impl;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.HasMailboxContext;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import javax.mail.Flags;


public class MailboxContextBuilder {
    private Optional<MailboxId> mailboxId;
    private Optional<MessageUid> uid;
    private Optional<Long> modSeq;
    private Optional<Flags> flags;

    MailboxContextBuilder() {
        this.mailboxId = Optional.absent();
        this.uid = Optional.absent();
        this.modSeq = Optional.absent();
        this.flags = Optional.absent();
    }

    public MailboxContextBuilder mailboxId(MailboxId mailboxId) {
        this.mailboxId = Optional.of(mailboxId);
        return this;
    }

    public MailboxContextBuilder uid(MessageUid uid) {
        this.uid = Optional.of(uid);
        return this;
    }

    public MailboxContextBuilder modSeq(long modSeq) {
        this.modSeq = Optional.of(modSeq);
        return this;
    }

    public MailboxContextBuilder flags(Flags flags) {
        this.flags = Optional.of(flags);
        return this;
    }

    public MailboxContextBuilder idWithMetatData(ComposedMessageIdWithMetaData idWithMetaData) {
        mailboxId(idWithMetaData.getComposedMessageId().getMailboxId());
        uid(idWithMetaData.getComposedMessageId().getUid());
        modSeq(idWithMetaData.getModSeq());
        flags(idWithMetaData.getFlags());
        return this;
    }

    public HasMailboxContext build() {
        Preconditions.checkState(mailboxId.isPresent(), "mailboxId is required");
        Preconditions.checkState(flags.isPresent(), "flags is required");

        return new HasMailboxContextImpl(mailboxId.get(), uid.orNull(), modSeq.or(0l), flags.get());
    }

    public MailboxContextBuilder mailboxContext(HasMailboxContext origin) {
        this.mailboxId(origin.getMailboxId());
        if (origin.getUid() != null) {
            this.uid(origin.getUid());
        }
        this.flags(origin.createFlags());
        this.modSeq(origin.getModSeq());

        return this;
    }
}
