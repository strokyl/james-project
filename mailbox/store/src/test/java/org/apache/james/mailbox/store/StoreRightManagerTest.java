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

import static org.apache.james.mailbox.fixture.MailboxFixture.MAILBOX_PATH1;
import static org.apache.james.mailbox.fixture.MailboxFixture.OTHER_USER;
import static org.apache.james.mailbox.fixture.MailboxFixture.THIRD_USER;
import static org.apache.james.mailbox.fixture.MailboxFixture.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class StoreRightManagerTest {

    public static final long UID_VALIDITY = 3421l;
    private StoreRightManager storeRightManager;
    private MailboxACLResolver mailboxAclResolver;
    private GroupMembershipResolver groupMembershipResolver;
    private String alice;
    private MockMailboxSession aliceSession;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private MailboxMapper mockedMailboxMapper;

    @Before
    public void setup() throws MailboxException {
        alice = "Alice";
        aliceSession = new MockMailboxSession(alice);
        MailboxSessionMapperFactory mockedMapperFactory = mock(MailboxSessionMapperFactory.class);
        mockedMailboxMapper = mock(MailboxMapper.class);
        mailboxAclResolver = new UnionMailboxACLResolver();
        groupMembershipResolver = new SimpleGroupMembershipResolver();
        when(mockedMapperFactory.getMailboxMapper(aliceSession))
            .thenReturn(mockedMailboxMapper);
        storeRightManager = new StoreRightManager(mockedMapperFactory,
                                                  mailboxAclResolver,
                                                  groupMembershipResolver);
    }

    @Test
    public void hasRighShouldThrowMailboxNotFoundExceptionWhenMailboxDoesNotExist() throws MailboxException {
        expectedException.expect(MailboxNotFoundException.class);

        MailboxPath mailboxPath = MailboxPath.forUser(alice, "unexisting mailbox");
        when(mockedMailboxMapper.findMailboxByPath(mailboxPath)).thenThrow(MailboxNotFoundException.class);
        storeRightManager.hasRight(mailboxPath, Right.Read, aliceSession);
    }

    @Test
    public void hasRighShouldReturnTrueWhenTheUserOwnTheMailbox() throws MailboxException {
        Mailbox mailbox = mock(Mailbox.class);
        when(mailbox.getUser()).thenReturn(alice);

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isFalse();
    }

    @Test
    public void hasRighShouldReturnTrueWhenTheUserDoesnotOwnTheMailboxButHaveTheCorrectedRightOnIt() throws MailboxException {
        Mailbox mailbox = mock(Mailbox.class);
        when(mailbox.getUser()).thenReturn("bob");
        when(mailbox.getACL()).thenReturn(new MailboxACL(new MailboxACL.Entry(alice, Right.Write)));

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isTrue();
    }

    @Test
    public void hasRighShouldReturnTrueWhenTheUserDoesnotOwnTheMailboxButHaveHasLeastTheCorrectedRightOnIt() throws MailboxException {
        Mailbox mailbox = mock(Mailbox.class);
        when(mailbox.getUser()).thenReturn("bob");
        when(mailbox.getACL()).thenReturn(new MailboxACL(new MailboxACL.Entry(alice, Right.Write, Right.Lookup)));

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isTrue();
    }

    @Test
    public void hasRighShouldReturnFalseWhenTheUserDoesNotOwnTheMailboxAndAsNoRightOnIt() throws MailboxException {
        Mailbox mailbox = mock(Mailbox.class);
        when(mailbox.getUser()).thenReturn("bob");

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isFalse();
    }

    @Test
    public void filteredForSessionShouldBeIdentityWhenOwner() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(OTHER_USER).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(THIRD_USER).asAddition());
        MailboxACL actual = StoreRightManager.filteredForSession(
            new SimpleMailbox(MAILBOX_PATH1, UID_VALIDITY), acl, new MockMailboxSession(USER));
        assertThat(actual).isEqualTo(acl);
    }

    @Test
    public void filteredForSessionShouldBeIdentityWhenAdmin() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(OTHER_USER).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(THIRD_USER).asAddition());
        MailboxACL actual = StoreRightManager.filteredForSession(
            new SimpleMailbox(MAILBOX_PATH1, UID_VALIDITY), acl, new MockMailboxSession(THIRD_USER));
        assertThat(actual).isEqualTo(acl);
    }

    @Test
    public void filteredForSessionShouldContainOnlyLoggedUserWhenReadWriteAccess() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(OTHER_USER).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(THIRD_USER).asAddition());
        MailboxACL actual = StoreRightManager.filteredForSession(
            new SimpleMailbox(MAILBOX_PATH1, UID_VALIDITY), acl, new MockMailboxSession(OTHER_USER));
        assertThat(actual.getEntries()).containsKey(MailboxACL.EntryKey.createUserEntryKey(OTHER_USER));
    }
}