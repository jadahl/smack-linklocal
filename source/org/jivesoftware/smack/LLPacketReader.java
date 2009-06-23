/**
 * $RCSfile$
 * $Revision: 7232 $
 * $Date: 2007-02-20 16:57:31 -0800 (Tue, 20 Feb 2007) $
 *
 * Copyright 2003-2007 Jive Software. 2008-2009 Jonas Ådahl
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

package org.jivesoftware.smack;

import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.xmlpull.mxp1.MXParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Listens for XML traffic from a remote XMPP client and parses it into packet objects.
 * The packet reader also manages all packet listeners and collectors.<p>
 *
 * @see PacketCollector
 * @see PacketListener
 * @author Matt Tucker
 */
public class LLPacketReader extends AbstractPacketReader {

    private XMPPLLConnection connection;
    private LLService service;

    public LLPacketReader(final LLService service, final XMPPLLConnection connection) {
        super(connection);
        this.service = service;
        this.connection = connection;
        this.init();
    }

    /**
     * Parse top-level packets in order to process them further.
     *
     * @param thread the thread that is being used by the reader to parse incoming packets.
     */
    protected void parsePackets(Thread thread) {
        try {
            int eventType = parser.getEventType();
            do {
                connection.updateLastActivity();
                if (eventType == XmlPullParser.START_TAG) {
                    if (parser.getName().equals("message")) {
                        processPacket(PacketParserUtils.parseMessage(parser));
                    }
                    else if (parser.getName().equals("iq")) {
                        processPacket(parseIQ(parser));
                    }
                    else if (parser.getName().equals("presence")) {
                        processPacket(PacketParserUtils.parsePresence(parser));
                    }
                    // We found an opening stream. Record information about it, then notify
                    // the connectionID lock so that the packet reader startup can finish.
                    else if (parser.getName().equals("stream")) {
                        // Ensure the correct jabber:client namespace is being used.
                        if ("jabber:client".equals(parser.getNamespace(null))) {
                            // Get the connection id.
                            for (int i=0; i<parser.getAttributeCount(); i++) {
                                if (parser.getAttributeName(i).equals("id")) {
                                    // Save the connectionID
                                    connectionID = parser.getAttributeValue(i);
                                    /*
                                    TODO implement 1.0 features (stream features etc)
                                    if (!"1.0".equals(parser.getAttributeValue("", "version"))) {
                                        // Notify that a stream has been opened if the
                                        // server is not XMPP 1.0 compliant otherwise make the
                                        // notification after TLS has been negotiated or if TLS
                                        // is not supported
                                        releaseConnectionIDLock();
                                    }*/
                                }
                                else if (parser.getAttributeName(i).equals("from")) {
                                    // Use the server name that the server says that it is.
                                    connection.setServiceName(parser.getAttributeValue(i));
                                }
                            }


                            // if we are the initiator, this means stream has been initiated
                            // if we aren't the initiator, this means we have to respond with
                            // stream initiator.
                            if (connection.isInitiator()) {
                                connectionID = connection.getServiceName();
                                releaseConnectionIDLock();
                            }
                            else {
                                // Check if service name is a known entity
                                // if it is, open the stream and keep it open
                                // otherwise open and immediately close it
                                if (connection.getServiceName() == null) {
                                    System.err.println("No service name specified in stream initiation, canceling.");
                                    shutdown();
                                } else {
                                    // Check if service name is known, if so
                                    // we will continue the session

                                    LLPresence presence = service.getPresenceByServiceName(connection.getServiceName());
                                    if (presence != null) {
                                        connection.setRemotePresence(presence);
                                        connectionID = connection.getServiceName();
                                        connection.streamInitiatingReceived();
                                        releaseConnectionIDLock();
                                    } else {
                                        System.err.println("Unknown service name '" +
                                                connection.getServiceName() +
                                                "' specified in stream initation, canceling.");
                                        shutdown();
                                    }
                                }
                            }
                        }
                    }
                    else if (parser.getName().equals("error")) {
                        throw new XMPPException(parseStreamError(parser));
                    }
                }
                else if (eventType == XmlPullParser.END_TAG) {
                    if (parser.getName().equals("stream")) {
                        // Disconnect the connection
                        connection.disconnect();
                    }
                }
                eventType = parser.next();
            } while (!done && eventType != XmlPullParser.END_DOCUMENT && thread == readerThread);
        }
        catch (Exception e) {
            if (!done) {
                // Close the connection and notify connection listeners of the
                // error.
                notifyConnectionError(e);
            }
        }
    }

    /**
     * Parses an IQ packet.
     *
     * @param parser the XML parser, positioned at the start of an IQ packet.
     * @return an IQ object.
     * @throws Exception if an exception occurs while parsing the packet.
     */
    private IQ parseIQ(XmlPullParser parser) throws Exception {
        IQ iqPacket = null;

        String id = parser.getAttributeValue("", "id");
        String to = parser.getAttributeValue("", "to");
        String from = parser.getAttributeValue("", "from");
        IQ.Type type = IQ.Type.fromString(parser.getAttributeValue("", "type"));
        XMPPError error = null;

        boolean done = false;
        while (!done) {
            int eventType = parser.next();

            if (eventType == XmlPullParser.START_TAG) {
                String elementName = parser.getName();
                String namespace = parser.getNamespace();
                if (elementName.equals("error")) {
                    error = PacketParserUtils.parseError(parser);
                }
                // Otherwise, see if there is a registered provider for
                // this element name and namespace.
                else {
                    Object provider = ProviderManager.getInstance().getIQProvider(elementName, namespace);
                    if (provider != null) {
                        if (provider instanceof IQProvider) {
                            iqPacket = ((IQProvider)provider).parseIQ(parser);
                        }
                        else if (provider instanceof Class) {
                            iqPacket = (IQ)PacketParserUtils.parseWithIntrospection(elementName,
                                    (Class)provider, parser);
                        }
                    }
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals("iq")) {
                    done = true;
                }
            }
        }
        // Decide what to do when an IQ packet was not understood
        if (iqPacket == null) {
            if (IQ.Type.GET == type || IQ.Type.SET == type ) {
                // If the IQ stanza is of type "get" or "set" containing a child element
                // qualified by a namespace it does not understand, then answer an IQ of
                // type "error" with code 501 ("feature-not-implemented")
                iqPacket = new IQ() {
                    public String getChildElementXML() {
                        return null;
                    }
                };
                iqPacket.setPacketID(id);
                iqPacket.setTo(from);
                iqPacket.setFrom(to);
                iqPacket.setType(IQ.Type.ERROR);
                iqPacket.setError(new XMPPError(XMPPError.Condition.feature_not_implemented));
                connection.sendPacket(iqPacket);
                return null;
            }
            else {
                // If an IQ packet wasn't created above, create an empty IQ packet.
                iqPacket = new IQ() {
                    public String getChildElementXML() {
                        return null;
                    }
                };
            }
        }

        // Set basic values on the iq packet.
        iqPacket.setPacketID(id);
        iqPacket.setTo(to);
        iqPacket.setFrom(from);
        iqPacket.setType(type);
        iqPacket.setError(error);

        return iqPacket;
    }
}
