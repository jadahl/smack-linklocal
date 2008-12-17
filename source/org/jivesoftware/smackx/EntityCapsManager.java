package org.jivesoftware.smackx;

import org.jivesoftware.smack.AbstractConnection;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.LLPresence;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.filter.PacketExtensionFilter;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.Base64;
import org.jivesoftware.smackx.provider.CapsExtensionProvider;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.CapsExtension;
import org.jivesoftware.smackx.packet.DataForm;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * Keeps track of entity capabilities.
 */
public class EntityCapsManager {

    public static final String HASH_METHOD = "sha-1";
    public static final String HASH_METHOD_CAPS = "SHA-1";

    private static String entityNode = "http://www.igniterealtime.org/projects/smack/";

    /**
     * Map of (node, hash algorithm) -&gt; DiscoverInfo. 
     */
    private static Map<String,DiscoverInfo> caps =
        new ConcurrentHashMap<String,DiscoverInfo>();
    
    /**
     * Map of Full JID -&gt; DiscoverInfo/null.
     * In case of c2s connection the key is formed as user@server/resource (resource is required)
     * In case of link-local connection the key is formed as user@host (no resource)
     */
    private Map<String,String> userCaps =
        new ConcurrentHashMap<String,String>(); 

    // CapsVerListeners gets notified when the version string is changed.
    private Set<CapsVerListener> capsVerListeners =
        new CopyOnWriteArraySet<CapsVerListener>();

    private String currentCapsVersion = null;

    static {
        ProviderManager.getInstance().addExtensionProvider(CapsExtension.NODE_NAME,
                CapsExtension.XMLNS, new CapsExtensionProvider());
    }

    /*EntityCapsManager(String identityType,
            String identityName, List<String> features,
            DataForm extendedInfo) {
        // Calculate the first caps version
        calculateEntityCapsVersion(identityType, identityName, features,
                extendedInfo);
    }*/

    /**
     * Add DiscoverInfo to the database.
     *
     * @param node The node name. Could be for example "http://psi-im.org#q07IKJEyjvHSyhy//CH0CxmKi8w=".
     * @param info DiscoverInfo for the specified node.
     */
    public static void addDiscoverInfoByNode(String node, DiscoverInfo info) {
        cleanupDicsoverInfo(info);

        caps.put(node, info);
    }

    public void addUserCapsNode(String user, String node) {
        userCaps.put(user, node);
    }

    public String getNodeVersionByUser(String user) {
        return userCaps.get(user);
    }

    public DiscoverInfo getDiscoverInfoByUser(String user) {
        String capsNode = userCaps.get(user);
        if (capsNode == null)
            return null;

        return getDiscoverInfoByNode(capsNode);
    }

    public String getCapsVersion() {
        return currentCapsVersion;
    }

    public String getNode() {
        return entityNode;
    }

    public void setNode(String node) {
        entityNode = node;
    }

    /**
     * Retrieve DiscoverInfo for a specific node.
     *
     * @param node The node name.
     * @return The corresponding DiscoverInfo or null if none is known.
     */
    public static DiscoverInfo getDiscoverInfoByNode(String node) {
        return caps.get(node);
    }

    private static void cleanupDicsoverInfo(DiscoverInfo info) {
        info.setFrom(null);
        info.setTo(null);
        info.setPacketID(null);
    }

    public void addPacketListener(AbstractConnection connection) {
        PacketFilter f =
            new AndFilter(
                    new PacketTypeFilter(Presence.class),
                    new PacketExtensionFilter(CapsExtension.NODE_NAME, CapsExtension.XMLNS));
        connection.addPacketListener(new CapsPacketListener(), f);
    }

    public void addCapsVerListener(CapsVerListener listener) {
        capsVerListeners.add(listener);

        if (currentCapsVersion != null)
            listener.capsVerUpdated(currentCapsVersion);
    }

    public void removeCapsVerListener(CapsVerListener listener) {
        capsVerListeners.remove(listener);
    }

    private void notifyCapsVerListeners() {
        // FIXME Add our own caps version to the caps manager.
        for (CapsVerListener listener : capsVerListeners) {
            listener.capsVerUpdated(currentCapsVersion);
        }
    }

    public void spam() {
        System.err.println("User nodes:");
        for (Map.Entry<String,String> e : userCaps.entrySet()) {
            System.err.println(" * " + e.getKey() + " -> " + e.getValue());
        }

        System.err.println("Caps versions:");
        for (Map.Entry<String,DiscoverInfo> e : caps.entrySet()) {
            System.err.println(" * " + e.getKey() + " -> " + e.getValue());
        }
    }

    ///////////
    //  Calculate Entity Caps Version String
    ///////////

    private static String capsToHash(String capsString) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_METHOD_CAPS);
            byte[] digest = md.digest(capsString.getBytes());
            return Base64.encodeBytes(digest);
        }
        catch (NoSuchAlgorithmException nsae) {
            return null;
        }
    }

    private static String formFieldValuesToCaps(Iterator<String> i) {
        String s = "";
        SortedSet<String> fvs = new TreeSet<String>();
        for (; i.hasNext();) {
            fvs.add(i.next());
        }
        for (String fv : fvs) {
            s += fv + "<";
        }
        return s;
    }

    void calculateEntityCapsVersion(String identityType,
            String identityName, List<String> features,
            DataForm extendedInfo) {
        String s = "";

        // Add identity
        // FIXME language
        s += "client/" + identityType + "//" + identityName + "<";

        // Add features
        synchronized (features) {
            SortedSet<String> fs = new TreeSet<String>();
            for (String f : features) {
                fs.add(f);
            }

            for (String f : fs) {
                s += f + "<";
            }
        }

        if (extendedInfo != null) {
            synchronized (extendedInfo) {
                SortedSet<FormField> fs = new TreeSet<FormField>(
                        new Comparator<FormField>() {
                            public int compare(FormField f1, FormField f2) {
                                return f1.getVariable().compareTo(f2.getVariable());
                            }
                        });

                FormField ft = null;

                for (Iterator<FormField> i = extendedInfo.getFields(); i.hasNext();) {
                    FormField f = i.next();
                    if (!f.getVariable().equals("FORM_TYPE")) {
                        fs.add(f);
                    }
                    else {
                        ft = f;
                    }
                }

                // Add FORM_TYPE values
                if (ft != null) {
                    s += formFieldValuesToCaps(ft.getValues());
                }

                // Add the other values
                for (FormField f : fs) {
                    s += f.getVariable() + "<";
                    s += formFieldValuesToCaps(f.getValues());
                }
            }
        }

        currentCapsVersion = capsToHash(s);
        notifyCapsVerListeners();
    }

    class CapsPacketListener implements PacketListener {

        public void processPacket(Packet packet) {
            CapsExtension ext =
                (CapsExtension) packet.getExtension(CapsExtension.NODE_NAME, CapsExtension.XMLNS);

            String nodeVer = ext.getNode() + "#" + ext.getVersion();
            String user = packet.getFrom();

            addUserCapsNode(user, nodeVer);
            
            // DEBUG
            spam();
        }
    }
}
