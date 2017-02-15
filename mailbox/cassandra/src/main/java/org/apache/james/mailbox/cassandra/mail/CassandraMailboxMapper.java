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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.util.CompletableFutureUtil;
import org.apache.james.util.OptionalConverter;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;

public class CassandraMailboxMapper implements MailboxMapper {

    public static final String WILDCARD = "%";
    public static final String VALUES_MAY_NOT_BE_LARGER_THAN_64_K = "Index expression values may not be larger than 64K";
    public static final String CLUSTERING_COLUMNS_IS_TOO_LONG = "The sum of all clustering columns is too long";
    public static final int MAX_SIZE_FOR_MAILBOX_NAME = 65536;

    private final int maxRetry;
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final CassandraMailboxPathDAO mailboxPathDAO;
    private final CassandraMailboxDAO mailboxDAO;
    private final Session session;

    public CassandraMailboxMapper(Session session, CassandraMailboxDAO mailboxDAO, CassandraMailboxPathDAO mailboxPathDAO, int maxRetry) {
        this.maxRetry = maxRetry;
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.mailboxDAO = mailboxDAO;
        this.mailboxPathDAO = mailboxPathDAO;
        this.session = session;
    }

    @Override
    public void delete(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        mailboxPathDAO.delete(mailbox.generateAssociatedPath())
            .thenCompose(any -> mailboxDAO.delete(mailboxId))
            .join();
    }

    @Override
    public Mailbox findMailboxByPath(MailboxPath path) throws MailboxException {
        try {
            return mailboxPathDAO.retrieveId(path)
                .thenCompose(cassandraIdOptional ->
                    cassandraIdOptional
                        .map(CassandraMailboxPathDAO.CassandraIdAndPath::getCassandraId)
                        .map(mailboxDAO::retrieveMailbox)
                        .orElse(CompletableFuture.completedFuture(Optional.empty())))
                .join()
                .orElseThrow(() -> new MailboxNotFoundException(path));
        } catch (CompletionException e) {
            if (e.getCause() instanceof InvalidQueryException) {
                if (StringUtils.containsIgnoreCase(e.getCause().getMessage(), VALUES_MAY_NOT_BE_LARGER_THAN_64_K)) {
                    throw new TooLongMailboxNameException("too long mailbox name");
                }
                throw new MailboxException("It has error with cassandra storage", e.getCause());
            }
            throw e;
        }
    }

    @Override
    public Mailbox findMailboxById(MailboxId id) throws MailboxException {
        CassandraId mailboxId = (CassandraId) id;
        return mailboxDAO.retrieveMailbox(mailboxId)
            .join()
            .orElseThrow(() -> new MailboxNotFoundException(id.serialize()));
    }

    @Override
    public List<Mailbox> findMailboxWithPathLike(MailboxPath path) throws MailboxException {
        Pattern regex = Pattern.compile(constructEscapedRegexForMailboxNameMatching(path));
        return mailboxPathDAO.listUserMailboxes(path.getNamespace(), path.getUser())
            .thenApply(stream -> stream.filter(idAndPath -> regex.matcher(idAndPath.getMailboxPath().getName()).matches()))
            .thenApply(stream -> stream.map(CassandraMailboxPathDAO.CassandraIdAndPath::getCassandraId))
            .thenApply(stream -> stream.map(mailboxDAO::retrieveMailbox))
            .thenCompose(CompletableFutureUtil::allOf)
            .thenApply(stream -> stream
                .flatMap(OptionalConverter::toStream)
                .collect(Guavate.<Mailbox>toImmutableList()))
            .join();
    }

    @Override
    public void save(Mailbox mailbox) throws MailboxException {
        Preconditions.checkArgument(mailbox instanceof SimpleMailbox);
        SimpleMailbox cassandraMailbox = (SimpleMailbox) mailbox;

        CassandraId cassandraId = retrieveId(cassandraMailbox);
        cassandraMailbox.setMailboxId(cassandraId);
        if (mailbox.getName().length() >= MAX_SIZE_FOR_MAILBOX_NAME) {
            throw new TooLongMailboxNameException("too long mailbox name");
        }
        try {
            boolean applied = mailboxDAO.retrieveMailbox(cassandraId)
                    .thenCompose(optional -> optional
                            .map(storedMailbox -> mailboxPathDAO.delete(storedMailbox.generateAssociatedPath()))
                            .orElse(CompletableFuture.completedFuture(null)))
                    .thenCompose(any -> mailboxPathDAO.save(mailbox.generateAssociatedPath(), cassandraId))
                    .thenCompose(result -> {
                        if (result) {
                            return mailboxDAO.save(cassandraMailbox).thenApply(any -> result);
                        }
                        return CompletableFuture.completedFuture(result);
                    }).join();

            if (!applied) {
                throw new MailboxExistsException(mailbox.generateAssociatedPath().asString());
            }
        } catch (CompletionException e) {
            if (e.getCause() instanceof InvalidQueryException) {
                String errorMessage = e.getCause().getMessage();
                if (StringUtils.containsIgnoreCase(errorMessage, VALUES_MAY_NOT_BE_LARGER_THAN_64_K) ||
                        StringUtils.containsIgnoreCase(errorMessage, CLUSTERING_COLUMNS_IS_TOO_LONG)) {
                    throw new TooLongMailboxNameException("too long mailbox name");
                }
                throw new MailboxException("It has error with cassandra storage", e.getCause());
            }
            throw e;
        }
    }

    private CassandraId retrieveId(SimpleMailbox cassandraMailbox) {
        if (cassandraMailbox.getMailboxId() == null) {
            return CassandraId.timeBased();
        } else {
            return (CassandraId) cassandraMailbox.getMailboxId();
        }
    }

    @Override
    public boolean hasChildren(Mailbox mailbox, char delimiter) {
        return mailboxPathDAO.listUserMailboxes(mailbox.getNamespace(), mailbox.getUser())
            .thenApply(stream -> stream
                .anyMatch(idAndPath -> idAndPath.getMailboxPath().getName().startsWith(mailbox.getName() + String.valueOf(delimiter))))
            .join();
    }

    @Override
    public List<Mailbox> list() throws MailboxException {
        return mailboxDAO.retrieveAllMailboxes()
            .join()
            .collect(Guavate.toImmutableList());
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public void updateACL(Mailbox mailbox, MailboxACL.MailboxACLCommand mailboxACLCommand) throws MailboxException {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        new CassandraACLMapper(cassandraId, session, cassandraAsyncExecutor, maxRetry).updateACL(mailboxACLCommand);
    }

    @Override
    public void endRequest() {
        // Do nothing
    }

    private String constructEscapedRegexForMailboxNameMatching(MailboxPath path) {
        return Collections
            .list(new StringTokenizer(path.getName(), WILDCARD, true))
            .stream()
            .map(this::tokenToPatternPart)
            .collect(Collectors.joining());
    }

    private String tokenToPatternPart(Object token) {
        if (token.equals(WILDCARD)) {
            return ".*";
        } else {
            return Pattern.quote((String) token);
        }
    }

}
