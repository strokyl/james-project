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

package org.apache.james.imap.processor.base;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import org.apache.james.mailbox.MessageUid;
import org.junit.Before;
import org.junit.Test;
import static org.assertj.guava.api.Assertions.assertThat;
import org.assertj.core.api.Assertions;

public class UidMsnMapperTest {
    private UidMsnMapper testee;
    private MessageUid messageUid1;
    private MessageUid messageUid2;
    private MessageUid messageUid3;
    private MessageUid messageUid4;

    @Before
    public void setUp() {
        testee = new UidMsnMapper();
        messageUid1 = MessageUid.of(1);
        messageUid2 = MessageUid.of(2);
        messageUid3 = MessageUid.of(3);
        messageUid4 = MessageUid.of(4);
    }

    @Test
    public void getUidShouldReturnEmptyIfNoMessageWithTheGivenMessageNumber() {
        assertThat(
            testee.getUid(0)
        ).isAbsent();
    }

    @Test
    public void getUidShouldTheCorrespondingUidIfItExist() {
        testee.addUid(messageUid1);

        Optional<MessageUid> result = testee.getUid(1);

        assertThat(result).isPresent();
        assertThat(result).contains(messageUid1);
    }

    @Test
    public void getFirstUidShouldReturnEmptyIfNoMessage() {
        assertThat(testee.getFirstUid()).isAbsent();
    }

    @Test
    public void getLastUidShouldReturnEmptyIfNoMessage() {
        assertThat(testee.getLastUid()).isAbsent();
    }

    @Test
    public void getFirstUidShouldReturnFirstUidIfAtLeastOneMessage() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);

        Optional<MessageUid> result = testee.getFirstUid();

        assertThat(result).contains(messageUid1);
    }

    @Test
    public void getLastUidShouldReturnLastUidIfAtLeastOneMessage() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);

        Optional<MessageUid> result = testee.getLastUid();

        assertThat(result).contains(messageUid2);
    }

    @Test
    public void getMsnShouldReturnAbsentIfNoCorrespondingMessage() {
        testee.addUid(messageUid1);

        assertThat(testee.getMsn(messageUid2)).isAbsent();
    }

    @Test
    public void getMsnShouldReturnMessageNumberIfUidIsThere() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);

        Optional<Integer> result = testee.getMsn(messageUid2);
        assertThat(result).contains(2);
    }

    @Test
    public void getNumMessageShouldReturnZeroIfNoMapping() {
    }

    @Test
    public void getNumMessageShouldReturnTheNumOfMessage() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);

        Assertions.assertThat(testee.getNumMessage()).isEqualTo(2);
    }

    @Test
    public void isEmptyShouldReturnTrueIfNoMapping() {
        Assertions.assertThat(testee.isEmpty()).isTrue();
    }

    @Test
    public void isEmptyShouldReturnFalseIfNoMapping() {
        testee.addUid(messageUid1);

        Assertions.assertThat(testee.isEmpty()).isFalse();
    }

    @Test
    public void clearShouldClearMapping() {
        testee.addUid(messageUid1);

        testee.clear();

        Assertions.assertThat(testee.isEmpty()).isTrue();
    }

    @Test
    public void addUidShouldKeepMessageNumberContiguous() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid4);
        testee.addUid(messageUid2);

        Assertions.assertThat(messageNumberAreContiguous(testee)).isTrue();
    }

    @Test
    public void addUidShouldNotOverridePreviousMapping() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid2);

        Assertions.assertThat(testee.getMsn(messageUid2).get()).isEqualTo(2);
    }

    @Test
    public void  removeShouldRemoveOnlyTheCorrectUidWhenOneTheMiddle() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.remove(messageUid2);

        assertThat(testee.getMsn(messageUid2)).isAbsent();
        assertThat(testee.getMsn(messageUid1)).isPresent();
        assertThat(testee.getMsn(messageUid3)).isPresent();
    }

    @Test
    public void  removeShouldRemoveOnlyTheCorrectUidWhenOneTheBeginning() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.remove(messageUid1);

        assertThat(testee.getMsn(messageUid1)).isAbsent();
        assertThat(testee.getMsn(messageUid2)).isPresent();
        assertThat(testee.getMsn(messageUid3)).isPresent();
    }

    @Test
    public void  removeShouldRemoveOnlyTheCorrectUidWhenOneTheEnd() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.remove(messageUid3);

        assertThat(testee.getMsn(messageUid3)).isAbsent();
        assertThat(testee.getMsn(messageUid1)).isPresent();
        assertThat(testee.getMsn(messageUid2)).isPresent();
    }

    @Test
    public void removeShouldKeepMessageNumberContiguousWhenDeletingTheFirstOne() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid4);

        testee.remove(messageUid1);

        Assertions.assertThat(messageNumberAreContiguous(testee)).isTrue();
    }

    @Test
    public void removeShouldKeepMessageNumberContiguousWhenDeletingTheLastOne() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid4);

        testee.remove(messageUid4);

        Assertions.assertThat(messageNumberAreContiguous(testee)).isTrue();
    }

    @Test
    public void removeShouldKeepMessageNumberContiguousWhenDeletingOneInTheMiddle() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid4);

        testee.remove(messageUid3);

        Assertions.assertThat(messageNumberAreContiguous(testee)).isTrue();
    }

    @Test
    public void removeShouldKeepUidInOrderWhenDeletingTheFirstOne() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid4);

        testee.remove(messageUid1);

        Assertions.assertThat(uidAreInOrder(testee)).isTrue();
    }

    @Test
    public void removeShouldKeepUidInOrderWhenDeletingTheLastOne() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid4);

        testee.remove(messageUid4);

        Assertions.assertThat(uidAreInOrder(testee)).isTrue();
    }

    @Test
    public void removeShouldKeepUidInOrderWhenDeletingOneInTheMiddle() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid4);

        testee.remove(messageUid3);

        Assertions.assertThat(uidAreInOrder(testee)).isTrue();
    }

    private static boolean messageNumberAreContiguous(UidMsnMapper testee) {
        int first = UidMsnMapper.FIRST_MSN;
        int last = testee.getNumMessage();

        for(int i = first; i < last; i++) {
            if (!testee.getUid(i).isPresent()) {
                return false;
            }
        }

        return true;
    }

    private static boolean uidAreInOrder(UidMsnMapper testee) {
        int first = UidMsnMapper.FIRST_MSN;
        int last = testee.getNumMessage();
        Optional<MessageUid> previous = Optional.absent();
        Optional<MessageUid> current;

        for(int i = first; i < last; i++) {
            current = testee.getUid(i);

            if (current.isPresent() && previous.isPresent() && previous.get().compareTo(current.get()) > 0) {
                return false;
            }

            previous = current;
        }

        return true;
    }
}