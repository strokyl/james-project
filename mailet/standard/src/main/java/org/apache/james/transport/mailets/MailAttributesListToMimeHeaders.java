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

package org.apache.james.transport.mailets;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.google.common.base.Strings;

/**
 * <p>Convert attributes of type List&lt;String&gt; to headers</p>
 *
 * <p>Sample configuration:</p>
 * <pre><code>
 * &lt;mailet match="All" class="MailAttributesToMimeHeaders"&gt;
 * &lt;simplemapping&gt;org.apache.james.attribute1;
 * headerName1&lt;/simplemapping&gt;
 * &lt;simplemapping&gt;org.apache.james.attribute2;
 * headerName2&lt;/simplemapping&gt; &lt;/mailet&gt;
 * </code></pre>
 */
public class MailAttributesListToMimeHeaders extends GenericMailet {

    private Map<String, String> attributeNameToHeader;

    @Override
    public void init() throws MessagingException {
        String simpleMappings = getInitParameter("simplemapping");
        if (Strings.isNullOrEmpty(simpleMappings)) {
            throw new MessagingException("simplemapping is required");
        }

        attributeNameToHeader = MappingArgument.parse(simpleMappings);
    }

    @Override
    public void service(Mail mail) {
        try {
            MimeMessage message = mail.getMessage();
            for (Entry<String, String> entry : attributeNameToHeader.entrySet()) {
                List<String> values = (List<String>) mail.getAttribute(entry.getKey());
                addHeaders(message, entry.getValue(), values);
            }
            message.saveChanges();
        } catch (MessagingException e) {
            log("Exception while adding headers", e);
        }
    }

    private void addHeaders(MimeMessage message, String headerName, List<String> values) throws MessagingException {
        if (values != null) {
            for(String value : values) {
                message.addHeader(headerName, value);
            }
        }
    }

}
