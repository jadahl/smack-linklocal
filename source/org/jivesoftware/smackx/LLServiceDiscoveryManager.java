package org.jivesoftware.smackx;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.LLServiceConnectionListener;
import org.jivesoftware.smack.XMPPLLConnection;
import org.jivesoftware.smack.LLService;
import org.jivesoftware.smack.LLPresence;
import org.jivesoftware.smack.LLPresenceListener;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.DiscoverItems;
import org.jivesoftware.smackx.packet.DataForm;

import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/** 
 * LLServiceDiscoveryManager acts as a wrapper around ServiceDiscoveryManager
 * as ServiceDiscoveryManager only creates an interface for requesting service
 * information on existing connections. Simply said it creates new connections
 * when needed,  uses already active connections  when appropriate and applies
 * values to new connections.
 *
 * @author Jonas Ådahl
 */
public class LLServiceDiscoveryManager {
    private Map<String, NodeInformationProvider> nodeInformationProviders =
            new ConcurrentHashMap<String,NodeInformationProvider>();
    private final List<String> features =
        new ArrayList<String>();
    private DataForm extendedInfo = null;

    private LLService service;
    private EntityCapsManager capsManager;

    /*static {
        XMPPLLConnection.addLLConnectionListener(new ConnectionServiceMaintainer());
    }*/

    public LLServiceDiscoveryManager(LLService service) {
        this.service = service;

        capsManager = new EntityCapsManager();
        capsManager.addCapsVerListener(new CapsPresenceRenewer());
        capsManager.calculateEntityCapsVersion(
                ServiceDiscoveryManager.getIdentityType(),
                ServiceDiscoveryManager.getIdentityName(),
                features, extendedInfo);

        // Add presence listener. The presence listener will gather
        // entity caps data
        service.addPresenceListener(new LLPresenceListener() {
            public void presenceNew(LLPresence presence) {
                if (presence.getHash() != null &&
                    presence.getNode() != null &&
                    presence.getVer() != null) {
                    // Add presence to caps manager
                    capsManager.addUserCapsNode(presence.getServiceName(),
                        presence.getNode() + "#" + presence.getVer());
                }
            }

            public void presenceRemove(LLPresence presence) {

            }
        });


        service.addLLServiceConnectionListener(new ConnectionServiceMaintainer());
    }

    /**
     * Returns the name of the client that will be returned when asked for the client identity
     * in a disco request. The name could be any value you need to identity this client.
     * 
     * @return the name of the client that will be returned when asked for the client identity
     *          in a disco request.
     */
    public static String getIdentityName() {
        return ServiceDiscoveryManager.getIdentityName();
    }

    /**
     * Sets the name of the client that will be returned when asked for the client identity
     * in a disco request. The name could be any value you need to identity this client.
     * 
     * @param name the name of the client that will be returned when asked for the client identity
     *          in a disco request.
     */
    public static void setIdentityName(String name) {
        ServiceDiscoveryManager.setIdentityType(name);
    }

    /**
     * Returns the type of client that will be returned when asked for the client identity in a 
     * disco request. The valid types are defined by the category client. Follow this link to learn 
     * the possible types: <a href="http://www.jabber.org/registrar/disco-categories.html#client">Jabber::Registrar</a>.
     * 
     * @return the type of client that will be returned when asked for the client identity in a 
     *          disco request.
     */
    public static String getIdentityType() {
        return ServiceDiscoveryManager.getIdentityType();
    }

    /**
     * Sets the type of client that will be returned when asked for the client identity in a 
     * disco request. The valid types are defined by the category client. Follow this link to learn 
     * the possible types: <a href="http://www.jabber.org/registrar/disco-categories.html#client">Jabber::Registrar</a>.
     * 
     * @param type the type of client that will be returned when asked for the client identity in a 
     *          disco request.
     */
    public static void setIdentityType(String type) {
        ServiceDiscoveryManager.setIdentityType(type);
    }

    /**
     * Returns a new or already established connection to the given service name.
     *
     * @param serviceName remote service to which we wish to be connected to.
     * @returns an established connection to the given service name.
     */
    private XMPPLLConnection getConnection(String serviceName) throws XMPPException {
        return service.getConnection(serviceName);
    }

    /** 
     * Returns a ServiceDiscoveryManager instance for a new or already established
     * connection to the given service name.
     *
     * @param serviceName the name of the service we wish to get the ServiceDiscoveryManager instance for.
     * @returns the ServiceDiscoveryManager instance.
     */
    private ServiceDiscoveryManager getInstance(String serviceName) throws XMPPException  {
        return ServiceDiscoveryManager.getInstanceFor(getConnection(serviceName));
    }

    /**
     * Registers extended discovery information of this XMPP entity. When this
     * client is queried for its information this data form will be returned as
     * specified by XEP-0128.
     * <p>
     *
     * Since no packet is actually sent to the server it is safe to perform this
     * operation before logging to the server. In fact, you may want to
     * configure the extended info before logging to the server so that the
     * information is already available if it is required upon login.
     *
     * @param info
     *            the data form that contains the extend service discovery
     *            information.
     */
    public void setExtendedInfo(DataForm info) {
        extendedInfo = info;

        // set for already active connections
        for (XMPPLLConnection connection : service.getConnections())
            ServiceDiscoveryManager.getInstanceFor(connection).setExtendedInfo(info);

        renewEntityCapsVersion();
    }

    /**
     * Removes the dataform containing extended service discovery information
     * from the information returned by this XMPP entity.<p>
     *
     * Since no packet is actually sent to the server it is safe to perform this
     * operation before logging to the server.
     */
    public void removeExtendedInfo() {
        extendedInfo = null;

        // remove for already active connections
        for (XMPPLLConnection connection : service.getConnections())
            ServiceDiscoveryManager.getInstanceFor(connection).removeExtendedInfo();

        renewEntityCapsVersion();
    }

    /**
     * Returns the discovered information of a given XMPP entity addressed by its JID.
     * 
     * @param entityID the address of the XMPP entity.
     * @return the discovered information.
     * @throws XMPPException if the operation failed for some reason.
     */
    public DiscoverInfo discoverInfo(String serviceName) throws XMPPException {
        return getInstance(serviceName).discoverInfo(serviceName);
    }
    /**
     * Returns the discovered information of a given XMPP entity addressed by its JID and
     * note attribute. Use this message only when trying to query information which is not 
     * directly addressable.
     * 
     * @param entityID the address of the XMPP entity.
     * @param node the attribute that supplements the 'jid' attribute.
     * @return the discovered information.
     * @throws XMPPException if the operation failed for some reason.
     */
    public DiscoverInfo discoverInfo(String serviceName, String node) throws XMPPException {
        return getInstance(serviceName).discoverInfo(serviceName, node);
    }

    /**
     * Returns the discovered items of a given XMPP entity addressed by its JID.
     * 
     * @param entityID the address of the XMPP entity.
     * @return the discovered information.
     * @throws XMPPException if the operation failed for some reason.
     */
    public DiscoverItems discoverItems(String entityID) throws XMPPException {
        return discoverItems(entityID, null);
    }

    /**
     * Returns the discovered items of a given XMPP entity addressed by its JID and
     * note attribute. Use this message only when trying to query information which is not 
     * directly addressable.
     * 
     * @param entityID the address of the XMPP entity.
     * @param node the attribute that supplements the 'jid' attribute.
     * @return the discovered items.
     * @throws XMPPException if the operation failed for some reason.
     */
    public DiscoverItems discoverItems(String serviceName, String node) throws XMPPException {
        return getInstance(serviceName).discoverItems(serviceName, node);
    }

    /**
     * Sets the NodeInformationProvider responsible for providing information 
     * (ie items) related to a given node. Every time this client receives a disco request
     * regarding the items of a given node, the provider associated to that node will be the 
     * responsible for providing the requested information.<p>
     * 
     * In MUC, a node could be 'http://jabber.org/protocol/muc#rooms' which means that the
     * NodeInformationProvider will provide information about the rooms where the user has joined. 
     * 
     * @param node the node whose items will be provided by the NodeInformationProvider.
     * @param listener the NodeInformationProvider responsible for providing items related
     *      to the node.
     */
    public void setNodeInformationProvider(String node,
            NodeInformationProvider listener) throws XMPPException {
        // store this NodeInformationProvider so we can add it to new connections.
        nodeInformationProviders.put(node, listener);

        // set for already active connections
        for (XMPPLLConnection connection : service.getConnections())
            ServiceDiscoveryManager.getInstanceFor(connection).setNodeInformationProvider(node, listener);
    }

    /**
     * Removes the NodeInformationProvider responsible for providing information 
     * (ie items) related to a given node. This means that no more information will be
     * available for the specified node.
     * 
     * In MUC, a node could be 'http://jabber.org/protocol/muc#rooms' which means that the
     * NodeInformationProvider will provide information about the rooms where the user has joined. 
     * 
     * @param node the node to remove the associated NodeInformationProvider.
     */
    public void removeNodeInformationProvider(String node) throws XMPPException {
        // remove from wrapper class
        nodeInformationProviders.remove(node);

        // remove from existing connections
        for (XMPPLLConnection connection : service.getConnections())
            ServiceDiscoveryManager.getInstanceFor(connection).removeNodeInformationProvider(node);
    }

    /**
     * Returns the supported features by this XMPP entity.
     * 
     * @return an Iterator on the supported features by this XMPP entity.
     */
    public Iterator<String> getFeatures() {
        synchronized (features) {
            return Collections.unmodifiableList(new ArrayList<String>(features)).iterator();
        }
    }

    /**
     * Registers that a new feature is supported by this XMPP entity. When this client is 
     * queried for its information the registered features will be answered.<p>
     *
     * Since no packet is actually sent to the server it is safe to perform this operation
     * before logging to the server. In fact, you may want to configure the supported features
     * before logging to the server so that the information is already available if it is required
     * upon login.
     *
     * @param feature the feature to register as supported.
     */
    public void addFeature(String feature) {
        synchronized (features) {
            features.add(feature);
        }

        renewEntityCapsVersion();
    }

    /**
     * Removes the specified feature from the supported features by this XMPP entity.<p>
     *
     * Since no packet is actually sent to the server it is safe to perform this operation
     * before logging to the server.
     *
     * @param feature the feature to remove from the supported features.
     */
    public void removeFeature(String feature) throws XMPPException {
        synchronized (features) {
            features.remove(feature);
            for (XMPPLLConnection connection : service.getConnections())
                ServiceDiscoveryManager.getInstanceFor(connection).removeFeature(feature);
        }

        renewEntityCapsVersion();
    }

    /**
     * Returns true if the specified feature is registered in the ServiceDiscoveryManager.
     *
     * @param feature the feature to look for.
     * @return a boolean indicating if the specified featured is registered or not.
     */
    public boolean includesFeature(String feature) {
        synchronized (features) {
            return features.contains(feature);
        }
    }

    /**
     * Returns true if the server supports publishing of items. A client may wish to publish items
     * to the server so that the server can provide items associated to the client. These items will
     * be returned by the server whenever the server receives a disco request targeted to the bare
     * address of the client (i.e. user@host.com).
     * 
     * @param entityID the address of the XMPP entity.
     * @return true if the server supports publishing of items.
     * @throws XMPPException if the operation failed for some reason.
     */
    public boolean canPublishItems(String entityID) throws XMPPException {
        return getInstance(entityID).canPublishItems(entityID);
    }

    /**
     * Publishes new items to a parent entity. The item elements to publish MUST have at least 
     * a 'jid' attribute specifying the Entity ID of the item, and an action attribute which 
     * specifies the action being taken for that item. Possible action values are: "update" and 
     * "remove".
     * 
     * @param entityID the address of the XMPP entity.
     * @param discoverItems the DiscoveryItems to publish.
     * @throws XMPPException if the operation failed for some reason.
     */
    public void publishItems(String entityID, DiscoverItems discoverItems)
            throws XMPPException {
        publishItems(entityID, null, discoverItems);
    }

    /**
     * Publishes new items to a parent entity and node. The item elements to publish MUST have at 
     * least a 'jid' attribute specifying the Entity ID of the item, and an action attribute which 
     * specifies the action being taken for that item. Possible action values are: "update" and 
     * "remove".
     * 
     * @param entityID the address of the XMPP entity.
     * @param node the attribute that supplements the 'jid' attribute.
     * @param discoverItems the DiscoveryItems to publish.
     * @throws XMPPException if the operation failed for some reason.
     */
    public void publishItems(String entityID, String node, DiscoverItems discoverItems)
            throws XMPPException {
        getInstance(entityID).publishItems(entityID, node, discoverItems);
    }

    private void renewEntityCapsVersion() {
        if (capsManager != null) {
            capsManager.calculateEntityCapsVersion(
                    ServiceDiscoveryManager.getIdentityType(),
                    ServiceDiscoveryManager.getIdentityName(),
                    features, extendedInfo);
        }
    }

    /**
     * In case that a connection is unavailable we create a new connection
     * and push the service discovery procedure until the new connection is
     * established.
     */
    private class ConnectionServiceMaintainer implements LLServiceConnectionListener {

        public void connectionCreated(XMPPLLConnection connection) {
            // Add service discovery for Link-local connections.\
            ServiceDiscoveryManager manager =
                new ServiceDiscoveryManager(connection);

            // Set Entity Capabilities Manager
            manager.setEntityCapsManager(capsManager);

            // Set extended info
            manager.setExtendedInfo(extendedInfo);

            // Set node information providers
            for (Map.Entry<String,NodeInformationProvider> entry :
                    nodeInformationProviders.entrySet()) {
                manager.setNodeInformationProvider(entry.getKey(), entry.getValue());
            }

            // add features
            synchronized (features) {
                for (String feature : features) {
                    manager.addFeature(feature);
                }
            }
        }
    }

    private class CapsPresenceRenewer implements CapsVerListener {
        public void capsVerUpdated(String ver) {
            synchronized (service) {
                try {
                    LLPresence presence = service.getLocalPresence();
                    presence.setHash(EntityCapsManager.HASH_METHOD);
                    presence.setNode(capsManager.getNode());
                    presence.setVer(ver);
                    service.updatePresence(presence);
                }
                catch (XMPPException xe) {
                    // ignore
                }
            }
        }
    }
}
