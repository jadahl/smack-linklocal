/*
 * Copyright 2009 Jonas Ådahl.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smackx.provider;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.jivesoftware.smackx.packet.CapsExtension;

import org.xmlpull.v1.XmlPullParser;

public class CapsExtensionProvider implements PacketExtensionProvider {
    public PacketExtension parseExtension(XmlPullParser parser)
        throws Exception {
        boolean done = false;
        int startDepth = parser.getDepth();

        String hash = parser.getAttributeValue(null, "hash");
        String node = parser.getAttributeValue(null, "node");
        String ver = parser.getAttributeValue(null, "ver");

        // Make the parser 
        while (true) {
            int eventType = parser.next();

            if (eventType == XmlPullParser.END_TAG &&
                    parser.getDepth() == startDepth)
                break;
        }

        if (hash != null && node != null && ver != null) {
            return new CapsExtension(node, ver, hash);
        }
        else {
            //throw new XMPPException("Malformed caps element.");
            // Malformed, ignore it
            return null;
        }
    }
}
