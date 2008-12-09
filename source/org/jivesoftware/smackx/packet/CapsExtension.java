package org.jivesoftware.smackx.packet;

import org.jivesoftware.smack.packet.PacketExtension;

public class CapsExtension implements PacketExtension {

    private String node, version, hash;
    private static final String XMLNS = "http://jabber.org/protocol/caps";


    public CapsExtension(String node, String version, String hash) {
        this.node = node;
        this.version = version;
        this.hash = hash;
    }

    public String getElementName() {
        return "s";
    }

    public String getNamespace() {
        return XMLNS;
    }

    /*<c xmlns='http://jabber.org/protocol/caps' 
     hash='sha-1'
     node='http://code.google.com/p/exodus'
     ver='QgayPKawpkPSDYmwT/WM94uAlu0='/>
     */
    public String toXML() {
        String xml = "<s xmlns='" + XMLNS + "' " +
            "hash='" + hash + "' " +
            "node='" + node + "' " +
            "ver='" + version + "'/>";

        return xml;
    }
}
