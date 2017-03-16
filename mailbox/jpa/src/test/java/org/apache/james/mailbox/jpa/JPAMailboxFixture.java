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

package org.apache.james.mailbox.jpa;

import org.apache.james.mailbox.jpa.mail.model.*;
import org.apache.james.mailbox.jpa.mail.model.openjpa.AbstractJPAMailboxMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAMailboxMessage;
import org.apache.james.mailbox.jpa.user.model.JPASubscription;

public interface JPAMailboxFixture {

    Class<?>[] MAILBOX_PERSISTANCE_CLASSES = new Class[] {JPAMailbox.class,
        AbstractJPAMailboxMessage.class,
        JPAMailboxMessage.class,
        JPAProperty.class,
        JPAUserFlag.class,
        JPAMailboxAnnotation.class,
        JPASubscription.class,
        JPAMailboxAnnotationId.class};

    String[] MAILBOX_TABLE_NAMES = new String[] {"JAMES_MAIL_USERFLAG",
        "JAMES_MAIL_PROPERTY",
        "JAMES_MAILBOX_ANNOTATION",
        "JAMES_MAILBOX",
        "JAMES_MAIL"};
}
