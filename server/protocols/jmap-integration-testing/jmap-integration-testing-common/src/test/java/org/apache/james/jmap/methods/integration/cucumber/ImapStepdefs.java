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

package org.apache.james.jmap.methods.integration.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.james.mailets.utils.IMAPMessageReader;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class ImapStepdefs {

    private final MainStepdefs mainStepdefs;
    private final UserStepdefs userStepdefs;
    private final Map<String, IMAPMessageReader> imapConnections;

    @Inject
    private ImapStepdefs(MainStepdefs mainStepdefs, UserStepdefs userStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.userStepdefs = userStepdefs;
        this.imapConnections = new HashMap<>();
    }

    @Then("^the user has a IMAP message in mailbox \"([^\"]*)\"$")
    public void hasMessageInMailbox(String mailbox) throws Throwable {
        IMAPMessageReader imapMessageReader = new IMAPMessageReader("127.0.0.1", 1143);

        assertThat(
            imapMessageReader.userReceivedMessageInMailbox(userStepdefs.lastConnectedUser,
                userStepdefs.passwordByUser.get(userStepdefs.lastConnectedUser),
                mailbox))
            .isTrue();
    }

    @Then("^the user has a IMAP notification about (\\d+) new message when selecting mailbox \"([^\"]*)\"$")
    public void hasANotificationAboutNewMessagesInMailbox(int numOfNewMessage, String mailbox) throws Throwable {
        IMAPMessageReader imapMessageReader = new IMAPMessageReader("127.0.0.1", 1143);

        assertThat(
            imapMessageReader.userGetNotifiedForNewMessagesWhenSelectingMailbox(userStepdefs.lastConnectedUser,
                userStepdefs.passwordByUser.get(userStepdefs.lastConnectedUser),
                numOfNewMessage, mailbox))
            .isTrue();
    }

    @Then("^the user does not have a IMAP message in mailbox \"([^\"]*)\"$")
    public void hasNoMessageInMailbox(String mailbox) throws Throwable {
        IMAPMessageReader imapMessageReader = new IMAPMessageReader("127.0.0.1", 1143);

        assertThat(
            imapMessageReader.userDoesNotReceiveMessageInMailbox(userStepdefs.lastConnectedUser,
                userStepdefs.passwordByUser.get(userStepdefs.lastConnectedUser),
                mailbox))
            .isTrue();
    }

    @Given("^the user has a open IMAP connexion with mailbox \"([^\"]*)\" selected")
    public void openImapConnexionAndSelectMailbox(String mailbox) throws Throwable {
        IMAPMessageReader imapMessageReader = new IMAPMessageReader("127.0.0.1", 1143);

        String login = userStepdefs.lastConnectedUser;
        String password = userStepdefs.passwordByUser.get(login);

        imapMessageReader.connectAndSelect(login, password, mailbox);
        imapConnections.put(mailbox, imapMessageReader);
    }

    @Then("^the user has a IMAP notification about (\\d+) new message on connexion for mailbox \"([^\"]*)\"$")
    public void checkNotificationForNewMessageOnActiveConnexion(int numOfMessage, String mailbox) throws Throwable {
        IMAPMessageReader imapMessageReader = imapConnections.get(mailbox);
        assertThat(imapMessageReader).isNotNull();
        assertThat(imapMessageReader.userGetNotifiedForNewMessages(numOfMessage)).isTrue();
    }

    @Then("^the user does not have IMAP notifications on connexion for mailbox \"([^\"]*)\"$")
    public void checkNoNotificationForNewMessageOnActiveConnexion(String mailbox) throws Throwable {
        IMAPMessageReader imapMessageReader = imapConnections.get(mailbox);
        assertThat(imapMessageReader).isNotNull();
        assertThat(imapMessageReader.userNotGetNotifiedForNewMessages()).isTrue();
    }
}
