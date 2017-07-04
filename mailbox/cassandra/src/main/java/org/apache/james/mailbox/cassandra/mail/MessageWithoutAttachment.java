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

import java.util.Date;
import java.util.List;
import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;

public class MessageWithoutAttachment {
    private final MessageId messageId;
    private final Date internalDate;
    private final Long size;
    private final Integer boduSize;
    private final SharedByteArrayInputStream content;
    private final Flags flags;
    private final PropertyBuilder propertyBuilder;
    private final MailboxId mailboxId;
    private final MessageUid messageUid;
    private final long modSeq;

    public MessageWithoutAttachment(MessageId messageId, Date internalDate, Long size, Integer boduSize, SharedByteArrayInputStream content,
                                    Flags flags, PropertyBuilder propertyBuilder, MailboxId mailboxId, MessageUid messageUid, long modSeq) {
        this.messageId = messageId;
        this.internalDate = internalDate;
        this.size = size;
        this.boduSize = boduSize;
        this.content = content;
        this.flags = flags;
        this.propertyBuilder = propertyBuilder;
        this.mailboxId = mailboxId;
        this.messageUid = messageUid;
        this.modSeq = modSeq;
    }

    public SimpleMailboxMessage toMailboxMessage(List<MessageAttachment> attachments) {
        SimpleMailboxMessage simpleMailboxMessage = new SimpleMailboxMessage(messageId, internalDate, size, boduSize,
                content, flags, propertyBuilder, mailboxId, attachments);
        simpleMailboxMessage.setUid(messageUid);
        simpleMailboxMessage.setModSeq(modSeq);
        return simpleMailboxMessage;
    }

    public MailboxId getMailboxId() {
        return mailboxId;
    }

    public MessageId getMessageId() {
        return messageId;
    }

    public SharedByteArrayInputStream getContent() {
        return content;
    }
}
