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
