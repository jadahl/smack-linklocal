package org.jivesoftware.smack;


import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.filter.MessageTypeFilter;


import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLService acts as an abstract interface to a Link-local XMPP service
 * according to XEP-0174. XEP-0174 describes how this is achieved using
 * mDNS/DNS-SD, and this class creates an implementation unspecific 
 * interface for doing this.
 *
 * The mDNS/DNS-SD is for example implemented by JmDNSService (using JmDNS).
 *
 * There is only one instance of LLService possible at one time.
 *
 * Tasks taken care of here are:
 * <ul>
 *   <li>Connection Management
 *     <ul>
 *       <li>Keep track of active connections from and to the local client</li>
 *       <li>Listen for connections on a semi randomized port announced by the
 *           mDNS/DNS-SD daemon</li>
 *       <li>Establish new connections when there is none to use and packets are
 *           to be sent</li>
 *     <ul>
 *   <li>Chat Management - Keep track of messaging sessions between users</li>
 * </ul>
 *
 * @author Jonas Ådahl
 */
public abstract class LLService {
    private LLService service = null;

    static final int DEFAULT_MIN_PORT = 2300;
    static final int DEFAULT_MAX_PORT = 2400;
    protected LLPresence presence;
    private String serviceName;
    private String host;
    private boolean done = false;
    private Thread listenerThread;

    private boolean initiated = false;

    private Map<String,LLChat> chats =
        new ConcurrentHashMap<String,LLChat>();

    private Map<String,XMPPLLConnection> ingoing =
        new ConcurrentHashMap<String,XMPPLLConnection>();
    private Map<String,XMPPLLConnection> outgoing =
        new ConcurrentHashMap<String,XMPPLLConnection>();

    // Listeners for state updates, such as LLService closed down
    private Set<LLServiceStateListener> stateListeners =
        new CopyOnWriteArraySet<LLServiceStateListener>();

    // Listeners for XMPPLLConnections associated with this service
    private Set<LLServiceConnectionListener> llServiceConnectionListeners =
        new CopyOnWriteArraySet<LLServiceConnectionListener>();

    // Listeners for packets coming from this Link-local service
    private final Map<PacketListener, ListenerWrapper> listeners =
            new ConcurrentHashMap<PacketListener, ListenerWrapper>();

    // Presence discoverer, notifies of changes in presences on the network.
    private LLPresenceDiscoverer presenceDiscoverer;

    // chat listeners gets notified when new chats are created
    private Set<LLChatListener> chatListeners = new CopyOnWriteArraySet<LLChatListener>();

    // Set of associated connections.
    private Set<XMPPLLConnection> associatedConnections =
        new HashSet<XMPPLLConnection>();

    private ServerSocket socket;

    /**
     * Spam stdout with some debug information.
     */
    public void spam() {
        System.out.println("Number of ingoing connection in map: " + ingoing.size());
        System.out.println("Number of outgoing connection in map: " + outgoing.size());

        System.out.println("Active chats:");
        for (LLChat chat : chats.values()) {
            System.out.println(" * " + chat.getServiceName());
        }

        System.out.println("Known presences:");
        for (LLPresence presence : presenceDiscoverer.getPresences()) {
            System.out.println(" * " + presence.getServiceName() + "(" + presence.getStatus() + ")");
        }
        Thread.currentThread().getThreadGroup().list();
    }

    protected LLService(LLPresence presence, LLPresenceDiscoverer discoverer) {
        this.presence = presence;
        presenceDiscoverer = discoverer;
        serviceName = presence.getServiceName();
        host = presence.getHost();
        service = this;

        XMPPLLConnection.addLLConnectionListener(new LLConnectionListener() {
            public void connectionCreated(XMPPLLConnection connection) {
                // We only care about this connection if we were the one
                // creating it
                if (isAssociatedConnection(connection)) {
                    if (connection.isInitiator()) {
                        addOutgoingConnection(connection);
                    }
                    else {
                        addIngoingConnection(connection);
                    }

                    connection.addConnectionListener(new ConnectionActivityListener(connection));

                    // Notify listeners that a new connection associated with this
                    // service has been created.
                    notifyNewServiceConnection(connection);

                    // add message listener. filter logic:
                    // type = msg ^ (msg.type = chat v msg.type = normal v msg.type = error)
                    connection.addPacketListener(new MessageListener(service),
                        new AndFilter(
                            new PacketTypeFilter(Message.class),
                            new OrFilter(
                                new MessageTypeFilter(Message.Type.chat),
                                new OrFilter(
                                    new MessageTypeFilter(Message.Type.normal),
                                    new MessageTypeFilter(Message.Type.error)))));

                    // add other existing packet filters associated with this service
                    for (ListenerWrapper wrapper : listeners.values()) {
                        connection.addPacketListener(wrapper.getPacketListener(),
                                wrapper.getPacketFilter());
                    }
                }
            }
        });

    }

    /**
     * Returns the running mDNS/DNS-SD XMPP instance. There can only be one
     * instance at a time.
     *
     * @return the active LLService instance.
     * @throws XMPPException if the LLService hasn't been instantiated.
     */
    /*public static LLService getServiceInstance() throws XMPPException {
        if (service == null)
            throw new XMPPException("Link-local service not initiated.");
        return service;
    }*/

    /**
     * Registers the service to the mDNS/DNS-SD daemon.
     * Should be implemented by the class extending this, for mDNS/DNS-SD library specific calls.
     */
    protected abstract void registerService() throws XMPPException;

    /**
     * Re-announce the presence information by using the mDNS/DNS-SD daemon.
     */
    protected abstract void reannounceService() throws XMPPException;

    /**
     * Make the client unavailabe. Equivalent to sending unavailable-presence.
     */
    public abstract void makeUnavailable();

    /**
     * Update the text field information. Used for setting new presence information.
     */
    protected abstract void updateText();

    public void init() throws XMPPException {
        // allocate a new port for remote clients to connect to
        socket = bindRange(DEFAULT_MIN_PORT, DEFAULT_MAX_PORT);
        presence.setPort(socket.getLocalPort());

        // register service on the allocated port
        registerService();

        // start to listen for new connections
        listenerThread = new Thread() {
            public void run() {
                try {
                    listenForConnections();
                    for (LLServiceStateListener listener : stateListeners)
                        listener.serviceClosed();
                } catch (XMPPException e) {
                    for (LLServiceStateListener listener : stateListeners)
                        listener.serviceClosedOnError(e);
                }
            }
        };
        listenerThread.setName("Smack Link-local Service Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        initiated = true;
    }

    public void close() {
        done = true;

        // close incoming connections
        for (XMPPLLConnection connection : ingoing.values()) {
            try {
                connection.shutdown();
            } catch (Exception e) {
                // ignore
            }
        }

        // close outgoing connections
        for (XMPPLLConnection connection : outgoing.values()) {
            try {
                connection.shutdown();
            } catch (Exception e) {
                // ignore
            }
        }
        try {
            socket.close();
        } catch (IOException ioe) {
            // ignore
        }
    }

    /**
     * Listen for new connections on socket, and spawn XMPPLLConnections
     * when new connections are established.
     *
     * @throws XMPPException whenever an exception occurs
     */
    private void listenForConnections() throws XMPPException {
        while (!done) {
            try {
                // wait for new connection
                Socket s = socket.accept();

                LLConnectionConfiguration config =
                    new LLConnectionConfiguration(presence, s);
                XMPPLLConnection connection = new XMPPLLConnection(this, config);

                // Associate the new connection with this service
                addAssociatedConnection(connection);

                // Spawn new thread to handle the connecting.
                // The reason for spawning a new thread is to let two remote clients
                // be able to connect at the same time.
                Thread connectionInitiatorThread =
                    new ConnectionInitiatorThread(connection);
                connectionInitiatorThread.setName("Smack Link-local Connection Initiator");
                connectionInitiatorThread.setDaemon(true);
                connectionInitiatorThread.start();
            }
            catch (IOException ioe) {
                throw new XMPPException("Link-local service unexpectedly closed down.", ioe);
            }
        }
    }

    /**
     * Bind one socket to any port within a given range.
     *
     * @param min the minimum port number allowed
     * @param max hte maximum port number allowed
     * @throws XMPPException if binding failed on all allowed ports.
     */
    private static ServerSocket bindRange(int min, int max) throws XMPPException {
        int port = 0;
        for (int try_port = min; try_port <= max; try_port++) {
            try {
                ServerSocket socket = new ServerSocket(try_port);
                return socket;
            }
            catch (IOException e) {
                // failed to bind, try next
            }
        }
        throw new XMPPException("Unable to bind port, no ports available.");
    }

    private void unknownOriginMessage(Message message) {
        for (LLServiceStateListener listener : stateListeners) {
            listener.unknownOriginMessage(message);
        }
    }

    /**
     * Adds a listener that are notified when a new link-local connection
     * has been established.
     *
     * @param listener A class implementing the LLConnectionListener interface.
     */
    public void addLLServiceConnectionListener(LLServiceConnectionListener listener) {
        llServiceConnectionListeners.add(listener);
    }

    /**
     * Removes a listener from the new connection listener list.
     *
     * @param listener The class implementing the LLConnectionListener interface that
     * is to be removed.
     */
    public void removeLLServiceConnectionListener(LLServiceConnectionListener listener) {
        llServiceConnectionListeners.remove(listener);
    }

    private void notifyNewServiceConnection(XMPPLLConnection connection) {
        for (LLServiceConnectionListener listener : llServiceConnectionListeners) {
            listener.connectionCreated(connection);
        }
    }

    /**
     * XXX document.
     */
    private void addAssociatedConnection(XMPPLLConnection connection) {
        synchronized (associatedConnections) {
            associatedConnections.add(connection);
        }
    }

    /**
     * XXX document.
     */
    private void removeAssociatedConnection(XMPPLLConnection connection) {
        synchronized (associatedConnections) {
            associatedConnections.remove(connection);
        }
    }

    private boolean isAssociatedConnection(XMPPLLConnection connection) {
        synchronized (associatedConnections) {
            return associatedConnections.contains(connection);
        }
    } 

    /**
     * Add a packet listener.
     *
     * @param listener the PacketListener
     * @param filter the Filter
     */
    public void addPacketListener(PacketListener listener, PacketFilter filter) {
        ListenerWrapper wrapper = new ListenerWrapper(listener, filter);
        listeners.put(listener, wrapper);

        // Also add to existing connections
        synchronized (ingoing) {
            synchronized (outgoing) {
                for (XMPPLLConnection c : getConnections()) {
                    c.addPacketListener(listener, filter);
                }
            }
        }
    }

    /** 
     * Remove a packet listener.
     */
    public void removePacketListener(PacketListener listener) {
        listeners.remove(listener);

        // Also add to existing connections
        synchronized (ingoing) {
            synchronized (outgoing) {
                for (XMPPLLConnection c : getConnections()) {
                    c.removePacketListener(listener);
                }
            }
        }
    }

    /**
     * Add service state listener.
     *
     * @param listener the service state listener to be added.
     */
    public void addServiceStateListener(LLServiceStateListener listener) {
        stateListeners.add(listener);
    }

    /**
     * Remove service state listener.
     *
     * @param listener the service state listener to be removed.
     */
    public void removeServiceStateListener(LLServiceStateListener listener) {
        stateListeners.remove(listener);
    }

    /**
     * Add Link-local chat session listener. The chat session listener will
     * be notified when new link-local chat sessions are created.
     *
     * @param listener the listener to be added.
     */
    public void addLLChatListener(LLChatListener listener) {
        chatListeners.add(listener);
    }

    /**
     * Remove Link-local chat session listener. 
     *
     * @param listener the listener to be removed.
     */
    public void removeLLChatListener(LLChatListener listener) {
        chatListeners.remove(listener);
    }

    /**
     * Add presence listener. A presence listener will be notified of new
     * presences, presences going offline, and changes in presences.
     *
     * @param listener the listener to be added.
     */
    public void addPresenceListener(LLPresenceListener listener) {
        presenceDiscoverer.addPresenceListener(listener);
    }

    /**
     * Remove presence listener.
     *
     * @param listener presence listener to be removed.
     */
    public void removePresenceListener(LLPresenceListener listener) {
        presenceDiscoverer.removePresenceListener(listener);
    }

    /**
     * Get the presence information associated with the given service name.
     *
     * @param serviceName the service name which information should be returned.
     * @return the service information.
     */
    LLPresence getPresenceByServiceName(String serviceName) {
        return presenceDiscoverer.getPresence(serviceName);
    }

    /**
     * Return a collection of all active connections. This may be used if the
     * user wants to change a property on all connections, such as add a service
     * discovery feature or other.
     *
     * @return a colllection of all active connections.
     */
    public Collection<XMPPLLConnection> getConnections() {
        Collection<XMPPLLConnection> connections =
            new ArrayList<XMPPLLConnection>(outgoing.values());
        connections.addAll(ingoing.values());
        return connections;
    }

    /**
     * Returns a connection to a given service name.
     * First checks for an outgoing connection, if noone exists,
     * try ingoing.
     *
     * @param serviceName the service name
     * @return a connection associated with the service name or null if no
     * connection is available.
     */
    XMPPLLConnection getConnectionTo(String serviceName) {
        XMPPLLConnection connection = outgoing.get(serviceName);
        if (connection != null)
            return connection;
        return ingoing.get(serviceName);
    }

    void addIngoingConnection(XMPPLLConnection connection) {
        ingoing.put(connection.getServiceName(), connection);
    }

    void removeIngoingConnection(XMPPLLConnection connection) {
        ingoing.remove(connection.getServiceName());
    }

    void addOutgoingConnection(XMPPLLConnection connection) {
        outgoing.put(connection.getServiceName(), connection);
    }

    void removeOutgoingConnection(XMPPLLConnection connection) {
        outgoing.remove(connection.getServiceName());
    }

    void newLLChat(LLChat chat) {
        chats.put(chat.getServiceName(), chat);
        for (LLChatListener listener : chatListeners) {
            listener.newChat(chat);
        }
    }

    /**
     * Get a LLChat associated with a given service name.
     * If no LLChat session is available, a new one is created.
     *
     * @param serviceName the service name
     * @return a chat session instance associated with the given service name.
     */
    public LLChat getChat(String serviceName) throws XMPPException {
        LLChat chat = chats.get(serviceName);
        if (chat == null) {
            LLPresence presence = getPresenceByServiceName(serviceName);
            if (presence == null)
                throw new XMPPException("Can't initiate new chat to '" +
                        serviceName + "': mDNS presence unknown.");
            chat = new LLChat(this, presence);
            newLLChat(chat);
        }
        return chat;
    }

    /**
     * Returns a XMPPLLConnection to the serviceName.
     * If no established connection exists, a new connection is created.
     * 
     * @param serviceName Service name of the remote client.
     * @return A connection to the given service name.
     */
    public XMPPLLConnection getConnection(String serviceName) throws XMPPException {
        // If a connection exists, return it.
        XMPPLLConnection connection = getConnectionTo(serviceName);
        if (connection != null)
            return connection;

        // If no connection exists, look up the presence and connect according to.
        LLPresence remotePresence = getPresenceByServiceName(serviceName);

        if (remotePresence == null) {
            throw new XMPPException("Can't initiate connection, remote peer is not available.");
        }

        LLConnectionConfiguration config =
            new LLConnectionConfiguration(presence, remotePresence);
        connection = new XMPPLLConnection(this, config);
        // Associate the new connection with this service
        addAssociatedConnection(connection);
        connection.connect();
        addOutgoingConnection(connection);

        return connection;
    }

    /**
     * Send a message to the remote peer.
     *
     * @param message the message to be sent.
     * @throws XMPPException if the message cannot be sent.
     */
    void sendMessage(Message message) throws XMPPException {
        sendPacket(message);
    }


    /**
     * Send a packet to the remote peer.
     *
     * @param packet the packet to be sent.
     * @throws XMPPException if the packet cannot be sent.
     */
    void sendPacket(Packet packet) throws XMPPException {
        getConnection(packet.getTo()).sendPacket(packet);
    }

    /**
     * Send an IQ set or get and get the respons.
     */
    public IQ getIQResponse(IQ request) throws XMPPException {
        XMPPLLConnection connection = getConnection(request.getTo());

        // Create a packet collector to listen for a response.
        PacketCollector collector = connection.createPacketCollector(
                new PacketIDFilter(request.getPacketID()));

        connection.sendPacket(request);

        // Wait up to 5 seconds for a result.
        IQ result = (IQ) collector.nextResult(
                SmackConfiguration.getPacketReplyTimeout());

        // Stop queuing results
        collector.cancel();
        if (result == null) {
            throw new XMPPException("No response from the remove host.");
        }

        return result;
    }

    /**
     * Update the presence information announced by the mDNS/DNS-SD daemon.
     * The presence object stored in the LLService class will be updated
     * with the new information and the daemon will reannounce the changes.
     *
     * @param presence the new presence information
     * @throws XMPPException if an error occurs
     */
    public void updatePresence(LLPresence presence) throws XMPPException {
        this.presence.update(presence);

        if (initiated) {
            updateText();
            reannounceService();
        }
    }

    /**
     * Get current Link-local presence.
     */
    public LLPresence getLocalPresence() {
        return presence;
    }

    /**
     * ConnectionActivityListener listens for link-local connection activity
     * such as closed connection and broken connection, and keeps record of
     * what active connections exist up to date.
     */
    private class ConnectionActivityListener implements ConnectionListener {
        private XMPPLLConnection connection;

        ConnectionActivityListener(XMPPLLConnection connection) {
            this.connection = connection;
        }

        public void connectionClosed() {
            removeConnectionRecord();
        }

        public void connectionClosedOnError(Exception e) {
            removeConnectionRecord();
        }

        public void reconnectingIn(int seconds) {
        }

        public void reconnectionSuccessful() {
        }

        public void reconnectionFailed(Exception e) {
        }

        private void removeConnectionRecord() {
            if (connection.isInitiator())
                removeOutgoingConnection(connection);
            else
                removeIngoingConnection(connection);

            removeAssociatedConnection(connection);
        }
    }

    /**
     * MessageListener listenes for messages from connections and delivers them
     * to the corresponding chat session. If no session is available, a new one
     * is created.
     */
    private class MessageListener implements PacketListener {
        private LLService service;

        MessageListener(LLService service) {
            this.service = service;
        }

        public void processPacket(Packet packet) {
            // handle message
            if (packet instanceof Message) {
                Message message = (Message)packet;
                String remoteServiceName = message.getFrom();
                LLPresence presence =  getPresenceByServiceName(remoteServiceName);

                // Get existing chat instance or create a new one and deliver
                // the message.
                try {
                    getChat(remoteServiceName).deliver(message);
                }
                catch (XMPPException xe) {
                    // If getChat throws an exception, it's because no
                    // presence could be found, thus known origin.
                    service.unknownOriginMessage(message);
                }
            }
        }
    }

    /**
     * Initiates a connection in a seperate thread, controlling
     * it was established correctly and stream was initiated.
     */
    private class ConnectionInitiatorThread extends Thread {
        XMPPLLConnection connection;

        ConnectionInitiatorThread(XMPPLLConnection connection) {
            this.connection = connection;
        }

        public void run() {
            try {
                connection.initListen();
            }
            catch (XMPPException xe) {
                // ignore, since its an incoming connection
                // there is nothing to save
            }
        }
    }

    /**
     * A wrapper class to associate a packet filter with a listener.
     */
    private static class ListenerWrapper {

        private PacketListener packetListener;
        private PacketFilter packetFilter;

        public ListenerWrapper(PacketListener packetListener, PacketFilter packetFilter) {
            this.packetListener = packetListener;
            this.packetFilter = packetFilter;
        }
       
        public void notifyListener(Packet packet) {
            if (packetFilter == null || packetFilter.accept(packet)) {
                packetListener.processPacket(packet);
            }
        }

        public PacketListener getPacketListener() {
            return packetListener;
        }

        public PacketFilter getPacketFilter() {
            return packetFilter;
        }
    }
}

