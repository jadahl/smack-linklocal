package org.jivesoftware.smackx;

import org.jivesoftware.smackx.packet.DiscoverInfo;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;


/**
 * Keeps track of entity capabilities.
 */
public class EntityCapsManager {

    /**
     * Map of (node, hash algorithm) -&gt; DiscoverInfo. 
     */
    private static Map<String,DiscoverInfo> caps =
        new ConcurrentHashMap<String,DiscoverInfo>();

    /**
     * Add DiscoverInfo to the database.
     *
     * @param node The node name. Could be for example "http://psi-im.org#q07IKJEyjvHSyhy//CH0CxmKi8w=".
     * @param hashAlgo The algorithm used to create the hash value. In most cases sha-1.
     * @param info DiscoverInfo for the specified node.
     */
    public static void addDiscoverInfo(String node, DiscoverInfo info) {
        // clean up so it doesn't contain who it's from or to.
        info.setFrom(null);
        info.setTo(null);
        info.setPacketID(null);

        caps.put(node, info);
    }

    /**
     * Retrieve DiscoverInfo for a specific node.
     *
     * @param node The node name.
     * @return The corresponding DiscoverInfo or null if none is known.
     */
    public static DiscoverInfo getDiscoverInfo(String node) {
        return caps.get(node);
    }
}
