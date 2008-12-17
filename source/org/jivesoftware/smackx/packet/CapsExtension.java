package org.jivesoftware.smackx.packet;

import org.jivesoftware.smack.packet.PacketExtension;

public class CapsExtension implements PacketExtension {

    private String node, version, hash;
    public static final String XMLNS = "http://jabber.org/protocol/caps";
    public static final String NODE_NAME = "c";

    public CapsExtension() {
    }

    public CapsExtension(String node, String version, String hash) {
        this.node = node;
        this.version = version;
        this.hash = hash;
    }

    public String getElementName() {
        return NODE_NAME;
    }

    public String getNamespace() {
        return XMLNS;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    /*<c xmlns='http://jabber.org/protocol/caps' 
     hash='sha-1'
     node='http://code.google.com/p/exodus'
     ver='QgayPKawpkPSDYmwT/WM94uAlu0='/>
     */
    public String toXML() {
        String xml = "<c xmlns='" + XMLNS + "' " +
            "hash='" + hash + "' " +
            "node='" + node + "' " +
            "ver='" + version + "'/>";

        return xml;
    }
}
