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

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.internet.SharedInputStream;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.MessageUtil;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.quota.QuotaChecker;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

public class StoreMessageIdManager implements MessageIdManager {

    private static final Function<MailboxMessage, MetadataWithMailboxId> EXTRACT_METADATA_FUNCTION = new Function<MailboxMessage, MetadataWithMailboxId>() {
        @Override
        public MetadataWithMailboxId apply(MailboxMessage mailboxMessage) {
            return new MetadataWithMailboxId(new SimpleMessageMetaData(mailboxMessage), mailboxMessage.getMailboxId());
        }
    };

    private static final Function<MailboxMessage, MailboxId> EXTRACT_MAILBOX_ID_FUNCTION = new Function<MailboxMessage, MailboxId>() {
        @Override
        public MailboxId apply(MailboxMessage input) {
            return input.getMailboxId();
        }
    };

    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final MailboxEventDispatcher dispatcher;
    private final MessageId.Factory messageIdFactory;
    private final QuotaManager quotaManager;
    private final QuotaRootResolver quotaRootResolver;

    @Inject
    public StoreMessageIdManager(MailboxSessionMapperFactory mailboxSessionMapperFactory, MailboxEventDispatcher dispatcher,
                                 MessageId.Factory messageIdFactory,
                                 QuotaManager quotaManager, QuotaRootResolver quotaRootResolver) {
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.dispatcher = dispatcher;
        this.messageIdFactory = messageIdFactory;
        this.quotaManager = quotaManager;
        this.quotaRootResolver = quotaRootResolver;
    }

    @Override
    public void setFlags(Flags newState, MessageManager.FlagsUpdateMode replace, MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        allowOnMailboxSession(mailboxIds, mailboxSession, mailboxMapper);

        Map<MailboxId, UpdatedFlags> updatedFlags = messageIdMapper.setFlags(messageId, mailboxIds, newState, replace);
        for (Map.Entry<MailboxId, UpdatedFlags> entry : updatedFlags.entrySet()) {
            dispatchFlagsChange(mailboxSession, entry.getKey(), entry.getValue());
        }
    }

    @Override
    public List<MessageResult> getMessages(List<MessageId> messageIds, final MessageResult.FetchGroup fetchGroup, final MailboxSession mailboxSession) throws MailboxException {
        try {
            MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
            final MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);
            List<MailboxMessage> messageList = messageIdMapper.find(messageIds, MessageMapper.FetchType.Full);
            ImmutableSet<MailboxId> mailboxIds = FluentIterable.from(messageList)
                .transform(EXTRACT_MAILBOX_ID_FUNCTION)
                .toSet();
            final ImmutableSet<MailboxId> allowedMailboxIds = FluentIterable.from(mailboxIds)
                .filter(mailboxBelongsToUser(mailboxSession, mailboxMapper))
                .toSet();
            return FluentIterable.from(messageList)
                .filter(inMailboxes(allowedMailboxIds))
                .transform(messageResultConverter(fetchGroup))
                .toList();
        } catch (WrappedException wrappedException) {
            throw wrappedException.unwrap();
        }
    }

    @Override
    public void delete(MessageId messageId, final List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        allowOnMailboxSession(mailboxIds, mailboxSession, mailboxMapper);

        Iterable<MetadataWithMailboxId> metadatasWithMailbox = FluentIterable
            .from(messageIdMapper.find(ImmutableList.of(messageId), MessageMapper.FetchType.Metadata))
            .filter(inMailboxes(mailboxIds))
            .transform(EXTRACT_METADATA_FUNCTION);

        messageIdMapper.delete(messageId, mailboxIds);

        for (MetadataWithMailboxId metadataWithMailboxId : metadatasWithMailbox) {
            dispatcher.expunged(mailboxSession, metadataWithMailboxId.messageMetaData, mailboxMapper.findMailboxById(metadataWithMailboxId.mailboxId));
        }
    }

    @Override
    public void setInMailboxes(MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        allowOnMailboxSession(mailboxIds, mailboxSession, mailboxMapper);

        List<MailboxMessage> mailboxMessages = FluentIterable.from(messageIdMapper.find(ImmutableList.of(messageId), MessageMapper.FetchType.Full))
            .filter(messageBelongsToUser(mailboxSession, mailboxMapper))
            .toList();

        if (!mailboxMessages.isEmpty()) {
            ImmutableSet<MailboxId> currentMailboxes = FluentIterable.from(mailboxMessages)
                .transform(EXTRACT_MAILBOX_ID_FUNCTION)
                .toSet();
            HashSet<MailboxId> targetMailboxes = Sets.newHashSet(mailboxIds);
            List<MailboxId> mailboxesToRemove = ImmutableList.copyOf(Sets.difference(currentMailboxes, targetMailboxes));
            SetView<MailboxId> mailboxesToAdd = Sets.difference(targetMailboxes, currentMailboxes);

            MailboxMessage mailboxMessage = mailboxMessages.get(0);
            validateQuota(mailboxesToAdd, mailboxesToRemove, mailboxSession, mailboxMessage);

            if (!mailboxesToAdd.isEmpty()) {
                addMessageToMailboxes(messageIdMapper, mailboxMessage, mailboxesToAdd, mailboxSession);
            }
            if (!mailboxesToRemove.isEmpty()) {
                delete(messageId, mailboxesToRemove, mailboxSession);
            }
        }
    }

    protected MailboxMessage createMessage(Date internalDate, int size, int bodyStartOctet, SharedInputStream content, Flags flags, PropertyBuilder propertyBuilder, List<MessageAttachment> attachments, MailboxId mailboxId) throws MailboxException {
        return MessageUtil.buildMailboxMessage()
            .messageId(messageIdFactory.generate())
            .internalDate(internalDate)
            .size(size)
            .bodyStartOctet(bodyStartOctet)
            .content(content)
            .flags(flags)
            .propertyBuilder(propertyBuilder)
            .mailboxId(mailboxId)
            .attachments(attachments)
            .build();
    }
    
    private void dispatchFlagsChange(MailboxSession mailboxSession, MailboxId mailboxId, UpdatedFlags updatedFlags) throws MailboxException {
        if (updatedFlags.flagsChanged()) {
            Mailbox mailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId);
            dispatcher.flagsUpdated(mailboxSession, updatedFlags.getUid(), mailbox, updatedFlags);
        }
    }

    private void validateQuota(Collection<MailboxId> mailboxIdsToBeAdded, Collection<MailboxId> mailboxIdsToBeRemove, MailboxSession mailboxSession, MailboxMessage mailboxMessage) throws MailboxException {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        Map<QuotaRoot, Integer> messageCountByQuotaRoot = buildMapQuotaRoot(mailboxIdsToBeAdded, mailboxIdsToBeRemove, mailboxMapper);
        for (Map.Entry<QuotaRoot, Integer> entry : messageCountByQuotaRoot.entrySet()) {
            if (entry.getValue() > 0) {
                new QuotaChecker(quotaManager.getMessageQuota(entry.getKey()), quotaManager.getStorageQuota(entry.getKey()), entry.getKey())
                    .tryAddition(entry.getValue(), mailboxMessage.getFullContentOctets());
            }
        }
    }

    private Map<QuotaRoot, Integer> buildMapQuotaRoot(Collection<MailboxId> mailboxIdsToBeAdded, Collection<MailboxId> mailboxIdsToBeRemove, MailboxMapper mailboxMapper) throws MailboxException {
        Map<QuotaRoot, Integer> messageCountByQuotaRoot = new HashMap<QuotaRoot, Integer>();
        for (MailboxId mailboxId : mailboxIdsToBeAdded) {
            QuotaRoot quotaRoot = retrieveQuotaRoot(mailboxMapper, mailboxId);
            int currentCount = Optional.fromNullable(messageCountByQuotaRoot.get(quotaRoot)).or(0);
            messageCountByQuotaRoot.put(quotaRoot, currentCount + 1);
        }
        for (MailboxId mailboxId : mailboxIdsToBeRemove) {
            QuotaRoot quotaRoot = retrieveQuotaRoot(mailboxMapper, mailboxId);
            int currentCount = Optional.fromNullable(messageCountByQuotaRoot.get(quotaRoot)).or(0);
            messageCountByQuotaRoot.put(quotaRoot, currentCount - 1);
        }
        return messageCountByQuotaRoot;
    }

    private QuotaRoot retrieveQuotaRoot(MailboxMapper mailboxMapper, MailboxId mailboxId) throws MailboxException {
        Mailbox mailbox = mailboxMapper.findMailboxById(mailboxId);
        return quotaRootResolver.getQuotaRoot(mailbox.generateAssociatedPath());
    }

    private void addMessageToMailboxes(MessageIdMapper messageIdMapper, MailboxMessage mailboxMessage, SetView<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);
        for (MailboxId mailboxId : mailboxIds) {
            MailboxMessage copy = MessageUtil.copy(mailboxMessage, mailboxId);
            save(mailboxSession, messageIdMapper, copy);
            dispatcher.added(mailboxSession, mailboxMapper.findMailboxById(mailboxId), copy);
        }
    }

    private void save(MailboxSession mailboxSession, MessageIdMapper messageIdMapper, MailboxMessage mailboxMessage) throws MailboxException {
        long modSeq = mailboxSessionMapperFactory.getModSeqProvider().nextModSeq(mailboxSession, mailboxMessage.getMailboxId());
        MessageUid uid = mailboxSessionMapperFactory.getUidProvider().nextUid(mailboxSession, mailboxMessage.getMailboxId());
        mailboxMessage.setModSeq(modSeq);
        mailboxMessage.setUid(uid);
        messageIdMapper.save(mailboxMessage);
    }

    private Function<MailboxMessage, MessageResult> messageResultConverter(final MessageResult.FetchGroup fetchGroup) {
        return new Function<MailboxMessage, MessageResult>() {
            @Override
            public MessageResult apply(MailboxMessage input) {
                try {
                    return ResultUtils.loadMessageResult(input, fetchGroup);
                } catch (MailboxException e) {
                    throw new WrappedException(e);
                }
            }
        };
    }

    private Predicate<MailboxMessage> inMailboxes(final Collection<MailboxId> mailboxIds) {
        return new Predicate<MailboxMessage>() {
            @Override
            public boolean apply(MailboxMessage mailboxMessage) {
                return mailboxIds.contains(mailboxMessage.getMailboxId());
            }
        };
    }

    private Predicate<MailboxId> mailboxBelongsToUser(final MailboxSession mailboxSession, final MailboxMapper mailboxMapper) {
        return new Predicate<MailboxId>() {
            @Override
            public boolean apply(MailboxId mailboxId) {
                try {
                    Mailbox currentMailbox = mailboxMapper.findMailboxById(mailboxId);
                    return belongsToCurrentUser(currentMailbox, mailboxSession);
                } catch (MailboxException e) {
                    mailboxSession.getLog().error(String.format("Can not retrieve mailboxPath associated with %s", mailboxId.serialize()), e);
                    return false;
                }
            }
        };
    }

    private Predicate<MailboxMessage> messageBelongsToUser(MailboxSession mailboxSession, MailboxMapper mailboxMapper) {
        return Predicates.compose(
            mailboxBelongsToUser(mailboxSession, mailboxMapper),
            EXTRACT_MAILBOX_ID_FUNCTION);
    }

    private void allowOnMailboxSession(List<MailboxId> mailboxIds, MailboxSession mailboxSession, MailboxMapper mailboxMapper) throws MailboxNotFoundException {
        Optional<MailboxId> mailboxForbidden = FluentIterable.from(mailboxIds)
            .firstMatch(isMailboxOfOtherUser(mailboxSession, mailboxMapper))
            .or(Optional.<MailboxId>absent());

        if (mailboxForbidden.isPresent()) {
            throw new MailboxNotFoundException("Mailbox with Id " + mailboxForbidden.get() + " does not belong to session");
        }
    }

    private Predicate<MailboxId> isMailboxOfOtherUser(MailboxSession mailboxSession, MailboxMapper mailboxMapper) {
        return Predicates.not(mailboxBelongsToUser(mailboxSession, mailboxMapper));
    }

    private boolean belongsToCurrentUser(Mailbox mailbox, MailboxSession session) {
        return session.getUser().isSameUser(mailbox.getUser());
    }

    private static class MetadataWithMailboxId {
        private final MessageMetaData messageMetaData;
        private final MailboxId mailboxId;

        public MetadataWithMailboxId(MessageMetaData messageMetaData, MailboxId mailboxId) {
            this.messageMetaData = messageMetaData;
            this.mailboxId = mailboxId;
        }
    }

    private static class WrappedException extends RuntimeException {
        private final MailboxException cause;

        public WrappedException(MailboxException cause) {
            this.cause = cause;
        }

        public MailboxException unwrap() throws MailboxException {
            throw cause;
        }
    }
}
