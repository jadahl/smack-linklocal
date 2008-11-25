/**
 * $Revision:$
 * $Date:$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.smack.packet;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import org.xml.sax.SAXException;
import static junit.framework.Assert.assertEquals;

import java.io.IOException;

/**
 *
 */
public class PresenceTest {
    @Test
    public void setPresenceTypeTest() throws IOException, SAXException {
        Presence.Type type = Presence.Type.unavailable;
        Presence.Type type2 = Presence.Type.subscribe;

        StringBuilder controlBuilder = new StringBuilder();
        controlBuilder.append("<presence")
                .append(" type=\"")
                .append(type)
                .append("\">")
                .append("</presence>");
        String control = controlBuilder.toString();

        Presence presenceTypeInConstructor = new Presence(type);
        presenceTypeInConstructor.setPacketID(Packet.ID_NOT_AVAILABLE);
        assertEquals(type, presenceTypeInConstructor.getType());
        assertXMLEqual(control, presenceTypeInConstructor.toXML());

        controlBuilder = new StringBuilder();
        controlBuilder.append("<presence")
                .append(" type=\"")
                .append(type2)
                .append("\">")
                .append("</presence>");
        control = controlBuilder.toString();

        Presence presenceTypeSet = getNewPresence();
        presenceTypeSet.setType(type2);
        assertEquals(type2, presenceTypeSet.getType());
        assertXMLEqual(control, presenceTypeSet.toXML());
    }

    @Test(expected=NullPointerException.class)
    public void setNullPresenceTypeTest() {
        getNewPresence().setType(null);
    }

    @Test
    public void isPresenceAvailableTest() {
        Presence presence = getNewPresence();
        presence.setType(Presence.Type.available);
        assertTrue(presence.isAvailable());

        presence.setType(Presence.Type.unavailable);
        assertFalse(presence.isAvailable());
    }

    @Test
    public void setPresenceStatusTest() throws IOException, SAXException {
        final String status = "This is a test of the emergency broadcast system.";

        StringBuilder controlBuilder = new StringBuilder();
        controlBuilder.append("<presence>")
                .append("<status>")
                .append(status)
                .append("</status>")
                .append("</presence>");
        String control = controlBuilder.toString();

        Presence presence = getNewPresence();
        presence.setStatus(status);

        assertEquals(status, presence.getStatus());
        assertXMLEqual(control, presence.toXML());
    }

    @Test
    public void setPresencePriorityTest() throws IOException, SAXException {
        final int priority = 10;

        StringBuilder controlBuilder = new StringBuilder();
        controlBuilder.append("<presence>")
                .append("<priority>")
                .append(priority)
                .append("</priority>")
                .append("</presence>");
        String control = controlBuilder.toString();

        Presence presence = getNewPresence();
        presence.setPriority(priority);

        assertEquals(priority, presence.getPriority());
        assertXMLEqual(control, presence.toXML());
    }

    @Test(expected=IllegalArgumentException.class)
    public void setIllegalPriorityTest() {
        getNewPresence().setPriority(Integer.MIN_VALUE);
    }

    @Test
    public void setPresenceModeTest() throws IOException, SAXException {
        Presence.Mode mode1 = Presence.Mode.dnd;
                final int priority = 10;
        final String status = "This is a test of the emergency broadcast system.";
        Presence.Mode mode2 = Presence.Mode.chat;

        StringBuilder controlBuilder = new StringBuilder();
        controlBuilder.append("<presence>")
                .append("<status>")
                .append(status)
                .append("</status>")
                .append("<priority>")
                .append(priority)
                .append("</priority>")
                .append("<show>")
                .append(mode1)
                .append("</show>")
                .append("</presence>");
        String control = controlBuilder.toString();

        Presence presenceModeInConstructor = new Presence(Presence.Type.available, status, priority,
                mode1);
        presenceModeInConstructor.setPacketID(Packet.ID_NOT_AVAILABLE);
        assertEquals(mode1, presenceModeInConstructor.getMode());
        assertXMLEqual(control, presenceModeInConstructor.toXML());

        controlBuilder = new StringBuilder();
        controlBuilder.append("<presence>")
                .append("<show>")
                .append(mode2)
                .append("</show>")
                .append("</presence>");
       control = controlBuilder.toString();

        Presence presenceModeSet = getNewPresence();
        presenceModeSet.setMode(mode2);
        assertEquals(mode2, presenceModeSet.getMode());
        assertXMLEqual(control, presenceModeSet.toXML());
    }

    @Test
    public void isModeAwayTest() {
        Presence presence = getNewPresence();
        presence.setMode(Presence.Mode.away);
        assertTrue(presence.isAway());

        presence.setMode(Presence.Mode.chat);
        assertFalse(presence.isAway());
    }

    @Test
    public void presenceXmlLangTest() throws IOException, SAXException {
        final String lang = "sp";

        StringBuilder controlBuilder = new StringBuilder();
        controlBuilder.append("<presence")
                .append(" xml:lang=\"")
                .append(lang)
                .append("\">")
                .append("</presence>");
        String control = controlBuilder.toString();

        Presence presence = getNewPresence();
        presence.setLanguage(lang);

        assertXMLEqual(control, presence.toXML());
    }

    private static Presence getNewPresence() {
        Presence presence = new Presence(Presence.Type.available);
        presence.setPacketID(Packet.ID_NOT_AVAILABLE);
        return presence;
    }
}
