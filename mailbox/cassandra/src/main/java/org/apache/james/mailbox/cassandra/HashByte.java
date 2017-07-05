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

package org.apache.james.mailbox.cassandra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

public class HashByte {

    private static final Logger LOGGER = LoggerFactory.getLogger(HashByte.class);

    public static String hash(byte[] array) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");

            return byteAray2Hex(messageDigest.digest(array));
        } catch (NoSuchAlgorithmException e) {
            //this should never happen
            LOGGER.error("SHA-1 algorithm not found", e);
            throw new RuntimeException(e);
        }
    }

    private static String byteAray2Hex(byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b: hash) {
            formatter.format("%02x", b);
        }

        return formatter.toString();
    }
}
