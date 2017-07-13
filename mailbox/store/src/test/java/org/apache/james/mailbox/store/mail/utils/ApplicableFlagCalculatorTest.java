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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;

import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.util.SharedByteArrayInputStream;

import com.google.common.base.Charsets;
import org.apache.james.mailbox.ApplicableFlagBuilder;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MutableMailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.MessageUtil;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

public class ApplicableFlagCalculatorTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void constructorShouldThrowWhenNull() throws Exception {
        expectedException.expect(NullPointerException.class);
        new ApplicableFlagCalculator(null);
    }

    @Test
    public void computeApplicableFlagsShouldReturnOnlyDefaultApplicableFlagsWhenNoMessage() throws Exception {
        ApplicableFlagCalculator calculator = new ApplicableFlagCalculator(ImmutableList.<MutableMailboxMessage>of());

        assertThat(calculator.computeApplicableFlags()).isEqualTo(getDefaultApplicableFlag());
    }

    @Test
    public void computeApplicableFlagsShouldReturnOnlyDefaultApplicableFlagWhenNoMessageWithUserCustomFlag() throws Exception {
        List<MutableMailboxMessage> mailboxMessages = ImmutableList.of(
            createMessage(new Flags(Flag.ANSWERED)),
            createMessage(new Flags(Flag.DELETED)),
            createMessage(new Flags(Flag.USER)),
            createMessage(new Flags(Flag.RECENT)));

        ApplicableFlagCalculator calculator = new ApplicableFlagCalculator(mailboxMessages);

        assertThat(calculator.computeApplicableFlags()).isEqualTo(getDefaultApplicableFlag());
    }

    @Test
    public void computeApplicableFlagsShouldReturnOnlyDefaultApplicableFlagAndAllUserCustomFlagUsedOneMessage() throws Exception {
        List<MutableMailboxMessage> mailboxMessages = ImmutableList.of(
            createMessage(new Flags("capture me")),
            createMessage(new Flags("french")));

        ApplicableFlagCalculator calculator = new ApplicableFlagCalculator(mailboxMessages);

        Flags expected = ApplicableFlagBuilder
            .builder()
            .add("capture me", "french")
            .build();

        assertThat(calculator.computeApplicableFlags()).isEqualTo(expected);
    }

    @Test
    public void unionFlagsShouldAlwaysIgnoreRecentAndUser() throws  Exception {
        List<MutableMailboxMessage> mailboxMessages = ImmutableList.of(
            createMessage(new Flags(Flag.RECENT)),
            createMessage(new Flags(Flag.USER)));

        ApplicableFlagCalculator calculator = new ApplicableFlagCalculator(mailboxMessages);

        Flags result = calculator.computeApplicableFlags();

        softly.assertThat(result.contains(Flag.RECENT)).isFalse();
        softly.assertThat(result.contains(Flag.USER)).isFalse();
    }

    private MutableMailboxMessage createMessage(Flags messageFlags) {
        String content = "Any content";
        int bodyStart = 10;
        return MessageUtil.buildMutableMailboxMessage()
            .messageId(new DefaultMessageId())
            .internalDate(new Date())
            .size(content.length())
            .bodyStartOctet(bodyStart)
            .content(new SharedByteArrayInputStream(content.getBytes(Charsets.UTF_8)))
            .flags(messageFlags)
            .propertyBuilder(new PropertyBuilder())
            .mailboxId(TestId.of(1))
            .attachments(ImmutableList.<MessageAttachment>of())
            .build();
    }

    private Flags getDefaultApplicableFlag() {
        return ApplicableFlagBuilder.builder().build();
    }
}