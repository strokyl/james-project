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

package org.apache.james.mailbox.inmemory.mail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.mail.AbstractMessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MutableMailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.MessageUtil;
import org.apache.james.mailbox.store.mail.utils.ApplicableFlagCalculator;

public class InMemoryMessageMapper extends AbstractMessageMapper {
    private final Map<InMemoryId, Map<MessageUid, MutableMailboxMessage>> mailboxByUid;
    private static final int INITIAL_SIZE = 256;

    public InMemoryMessageMapper(MailboxSession session, UidProvider uidProvider,
            ModSeqProvider modSeqProvider) {
        super(session, uidProvider, modSeqProvider);
        this.mailboxByUid = new ConcurrentHashMap<InMemoryId, Map<MessageUid, MutableMailboxMessage>>(INITIAL_SIZE);
    }

    private Map<MessageUid, MutableMailboxMessage> getMembershipByUidForMailbox(Mailbox mailbox) {
        return getMembershipByUidForId((InMemoryId) mailbox.getMailboxId());
    }

    private Map<MessageUid, MutableMailboxMessage> getMembershipByUidForId(InMemoryId id) {
        Map<MessageUid, MutableMailboxMessage> membershipByUid = mailboxByUid.get(id);
        if (membershipByUid == null) {
            membershipByUid = new ConcurrentHashMap<MessageUid, MutableMailboxMessage>(INITIAL_SIZE);
            mailboxByUid.put(id, membershipByUid);
        }
        return membershipByUid;
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        return getMembershipByUidForMailbox(mailbox).size();
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        long count = 0;
        for (MailboxMessage member : getMembershipByUidForMailbox(mailbox).values()) {
            if (!member.isSeen()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void delete(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        getMembershipByUidForMailbox(mailbox).remove(message.getUid());
    }

    @Override
    public MessageMetaData move(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        InMemoryId originalMailboxId = (InMemoryId) original.getMailboxId();
        MessageUid uid = original.getUid();
        MessageMetaData messageMetaData = copy(mailbox, original);
        getMembershipByUidForId(originalMailboxId).remove(uid);
        return messageMetaData;
    }

    @Override
    public Iterator<MutableMailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType ftype, int max)
            throws MailboxException {
        List<MutableMailboxMessage> results = new ArrayList<MutableMailboxMessage>(getMembershipByUidForMailbox(mailbox).values());
        for (Iterator<MutableMailboxMessage> it = results.iterator(); it.hasNext();) {
            if (!set.includes(it.next().getUid())) {
                it.remove();
            }
        }
        
        Collections.sort(results);

        if (max > 0 && results.size() > max) {
            results = results.subList(0, max);
        }
        return results.iterator();
    }

    @Override
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {
        final List<MessageUid> results = new ArrayList<MessageUid>();
        for (MailboxMessage member : getMembershipByUidForMailbox(mailbox).values()) {
            if (member.isRecent()) {
                results.add(member.getUid());
            }
        }
        Collections.sort(results);

        return results;
    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
        List<MutableMailboxMessage> memberships = new ArrayList<MutableMailboxMessage>(getMembershipByUidForMailbox(mailbox).values());
        Collections.sort(memberships);
        for (MailboxMessage m : memberships) {
            if (m.isSeen() == false) {
                return m.getUid();
            }
        }
        return null;
    }

    @Override
    public Map<MessageUid, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox mailbox, MessageRange set)
            throws MailboxException {
        final Map<MessageUid, MessageMetaData> filteredResult = new HashMap<MessageUid, MessageMetaData>();

        Iterator<MutableMailboxMessage> it = findInMailbox(mailbox, set, FetchType.Metadata, -1);
        while (it.hasNext()) {
            MailboxMessage member = it.next();
            if (member.isDeleted()) {
                filteredResult.put(member.getUid(), new SimpleMessageMetaData(member));

                delete(mailbox, member);
            }
        }
        return filteredResult;
    }

    @Override
    public Flags getApplicableFlag(Mailbox mailbox) throws MailboxException {
        return new ApplicableFlagCalculator(getMembershipByUidForId((InMemoryId) mailbox.getMailboxId()).values())
            .computeApplicableFlags();
    }

    public void deleteAll() {
        mailboxByUid.clear();
    }

    @Override
    public void endRequest() {
        // Do nothing
    }

    @Override
    protected MessageMetaData copy(Mailbox mailbox, MessageUid uid, long modSeq, MailboxMessage original)
            throws MailboxException {
        MutableMailboxMessage message = MessageUtil.copyToMutable(original, mailbox.getMailboxId());
        message.setUid(uid);
        message.setModSeq(modSeq);
        Flags flags = original.createFlags();

        // Mark message as recent as it is a copy
        flags.add(Flag.RECENT);
        message.setFlags(flags);
        return save(mailbox, message);
    }

    @Override
    protected MessageMetaData save(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        MutableMailboxMessage copy = MessageUtil.copyToMutable(message, mailbox.getMailboxId());
        copy.setUid(message.getUid());
        copy.setModSeq(message.getModSeq());
        getMembershipByUidForMailbox(mailbox).put(message.getUid(), copy);

        return new SimpleMessageMetaData(message);
    }

    @Override
    protected void begin() throws MailboxException {

    }

    @Override
    protected void commit() throws MailboxException {

    }

    @Override
    protected void rollback() throws MailboxException {
    }
}
