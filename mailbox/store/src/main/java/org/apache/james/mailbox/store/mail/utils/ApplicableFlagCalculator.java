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

package org.apache.james.mailbox.store.mail.utils;

import javax.mail.Flags;

import org.apache.james.mailbox.ApplicableFlagBuilder;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import org.apache.james.mailbox.store.mail.model.MutableMailboxMessage;

public class ApplicableFlagCalculator {

    private static Function<MailboxMessage, Flags> toFlags() {
        return new Function<MailboxMessage, Flags>() {
            @Override
            public Flags apply(MailboxMessage mailboxMessage) {
                return mailboxMessage.createFlags();
            }
        };
    }

    private final Iterable<MutableMailboxMessage> mailboxMessages;

    public ApplicableFlagCalculator(Iterable<MutableMailboxMessage> mailboxMessages) {
        Preconditions.checkNotNull(mailboxMessages);
        this.mailboxMessages = mailboxMessages;
    }

    public Flags computeApplicableFlags() {
        return ApplicableFlagBuilder.builder()
                .add(FluentIterable.from(mailboxMessages)
                    .transform(toFlags())
                    .toArray(Flags.class))
                .build();
    }
}
