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

package org.jivesoftware.smack;

import org.jivesoftware.smack.debugger.SmackDebugger;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.XMPPError;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.lang.reflect.Constructor;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;


/**
 * Link-local XMPP connection according to XEP-0174 connection. Automatically
 * created by LLService and closed by inactivity.
 *
 */
public class XMPPLLConnection extends AbstractConnection // public for debugging reasons
{
    private final static Set<LLConnectionListener> linkLocalListeners =
            new CopyOnWriteArraySet<LLConnectionListener>();
    

    private LLService service;
    private LLPresence localPresence, remotePresence;
    private boolean initiator;
    private long lastActivity = 0;
    protected XMPPLLConnection connection;
    private Thread timeoutThread;

    private LLConnectionConfiguration configuration;

    /**
     * Instantiate a new link-local connection. Use the config parameter to
     * specify if the connection is acting as server or client.
     *
     * @param config specification about how the new connection is to be set up.
     */
    XMPPLLConnection(LLService service, LLConnectionConfiguration config) {
        connection = this;
        this.service = service;
        configuration = config;
        updateLastActivity();

        // A timeout thread's purpose is to close down inactive connections
        // after a certain amount of seconds (defaults to 15).
        timeoutThread = new Thread() {
            public void run() {
                try {
                    while (connection != null) {
                        //synchronized (connection) {
                            Thread.sleep(14000);
                            long currentTime = new Date().getTime();
                            if (currentTime - lastActivity > 15000) {
                                shutdown();
                                break;
                            }
                        //}
                    }
                } catch (InterruptedException ie) {
                    shutdown();
                }
            }
        };

        timeoutThread.setName("Smack Link-local Connection Timeout (" + connection.connectionCounterValue + ")");
        timeoutThread.setDaemon(true);


        if (config.isInitiator()) {
            // we are connecting to remote host
            localPresence = config.getLocalPresence();
            remotePresence = config.getRemotePresence();
            serviceName = remotePresence.getServiceName();
            initiator = true;
        } else {
            // a remote host connected to us
            localPresence = config.getLocalPresence();
            remotePresence = null;
            serviceName = null;
            initiator = false;
            socket = config.getSocket();
        }
    }

    /**
     * Tells if this connection instance is the initiator.
     *
     * @return true if this instance is the one connecting to a remote peer.
     */
    public boolean isInitiator() {
        return initiator;
    }

    /**
     * Return the user name of the remote peer (service name).
     *
     * @return the remote hosts service name / username
     */
    public String getUser() {
        // username is the service name of the local presence
        return localPresence.getServiceName();
    }

    /**
     * Sets the name of the service provided in the <stream:stream ...> from the remote peer.
     *
     * @param serviceName the name of the service
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }


    /**
     * Set the remote presence. Used when being connected,
     * will not know the remote service name until stream is initiated.
     *
     * @param remotePresence presence information about the connecting client.
     */
    void setRemotePresence(LLPresence remotePresence) {
        this.remotePresence = remotePresence;
    }

    /**
     * Start listen for data and a stream tag.
     */
    void initListen() throws XMPPException {
        initConnection();
    }

    /**
     * Adds a listener that are notified when a new link-local connection
     * has been established.
     *
     * @param listener A class implementing the LLConnectionListener interface.
     */
    public static void addLLConnectionListener(LLConnectionListener listener) {
        linkLocalListeners.add(listener);
    }

    /**
     * Removes a listener from the new connection listener list.
     *
     * @param listener The class implementing the LLConnectionListener interface that
     * is to be removed.
     */
    public static void removeLLConnectionListener(LLConnectionListener listener) {
        linkLocalListeners.remove(listener);
    }

    /**
     * Create a socket, connect to the remote peer and initiate a XMPP stream session.
     */
    void connect() throws XMPPException {
        String host = remotePresence.getHost();
        int port = remotePresence.getPort();

        try {
            socket = new Socket(host, port);
        }
        catch (UnknownHostException uhe) {
            String errorMessage = "Could not connect to " + host + ":" + port + ".";
            throw new XMPPException(errorMessage, new XMPPError(
                    XMPPError.Condition.remote_server_timeout, errorMessage),
                    uhe);
        }
        catch (IOException ioe) {
            String errorMessage = "Error connecting to " + host + ":"
                    + port + ".";
            throw new XMPPException(errorMessage, new XMPPError(
                    XMPPError.Condition.remote_server_error, errorMessage), ioe);
        }
        initConnection();

        notifyLLListenersConnected();
    }


    /**
     * Handles the opening of a stream after a remote client has connected and opened a stream.
     * @throws XMPPException if service name is missing or service is unknown to the mDNS daemon.
     */
    public void streamInitiatingReceived() throws XMPPException {
        if (serviceName == null) {
            shutdown();
        } else {
            packetWriter = new PacketWriter(this);
            if (debugger != null) {
                if (debugger.getWriterListener() != null) {
                    packetWriter.addPacketListener(debugger.getWriterListener(), null);
                }
            }
            packetWriter.startup();
            notifyLLListenersConnected();
        }
    }

    /**
     * Notify new connection listeners that a new connection has been established.
     */
    private void notifyLLListenersConnected() {
        for (LLConnectionListener listener : linkLocalListeners) {
            listener.connectionCreated(this);
        }
    }

    /**
     * Update the timer telling when the last activity happend. Used by timeout
     * thread to tell how long the connection has been inactive.
     */
    void updateLastActivity() {
        lastActivity = new Date().getTime();
    }

    /**
     * Sends the specified packet to the remote peer.
     *
     * @param packet the packet to send
     */
    @Override
    public void sendPacket(Packet packet) {
        updateLastActivity();
        // always add the from='' attribute
        packet.setFrom(getUser());
        super.sendPacket(packet);
    }

    /**
     * Initializes the connection by creating a packet reader and writer and opening a
     * XMPP stream to the server.
     *
     * @throws XMPPException if establishing a connection to the server fails.
     */
    private void initConnection() throws XMPPException {
        // Set the reader and writer instance variables
        initReaderAndWriter();
        timeoutThread.start();

        try {
            // Don't initialize packet writer until we know it's a valid connection
            // unless we are the initiator. If we are NOT the initializer, we instead
            // wait for a stream initiation before doing anything.
            if (isInitiator())
                packetWriter = new PacketWriter(this);

            // Initialize packet reader
            packetReader = new LLPacketReader(service, this);

            // If debugging is enabled, we should start the thread that will listen for
            // all packets and then log them.
            // XXX FIXME debugging enabled not working
            if (false) {//configuration.isDebuggerEnabled()) {
                packetReader.addPacketListener(debugger.getReaderListener(), null);
            }

            // Make note of the fact that we're now connected.
            connected = true;

            // If we are the initiator start the packet writer. This will open a XMPP
            // stream to the server. If not, a packet writer will be started after
            // receiving an initial stream start tag.
            if (isInitiator())
                packetWriter.startup();
            // Start the packet reader. The startup() method will block until we
            // get an opening stream packet back from server.
            packetReader.startup();
        }
        catch (XMPPException ex) {
            // An exception occurred in setting up the connection. Make sure we shut down the
            // readers and writers and close the socket.

            if (packetWriter != null) {
                try {
                    packetWriter.shutdown();
                }
                catch (Throwable ignore) { /* ignore */ }
                packetWriter = null;
            }
            if (packetReader != null) {
                try {
                    packetReader.shutdown();
                }
                catch (Throwable ignore) { /* ignore */ }
                packetReader = null;
            }
            if (socket != null) {
                try {
                    socket.close();
                }
                catch (Exception e) { /* ignore */ }
                socket = null;
            }
            // closing reader after socket since reader.close() blocks otherwise
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (Throwable ignore) { /* ignore */ }
                reader = null;
            }
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (Throwable ignore) {  /* ignore */ }
                writer = null;
            }
            connected = false;

            throw ex;        // Everything stoppped. Now throw the exception.
        }
    }

    private void initReaderAndWriter() throws XMPPException {
        try {
            reader =
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        }
        catch (IOException ioe) {
            throw new XMPPException(
                    "XMPPError establishing connection with server.",
                    new XMPPError(XMPPError.Condition.remote_server_error,
                            "XMPPError establishing connection with server."),
                    ioe);
        }

        // If debugging is enabled, we open a window and write out all network traffic.
        if (false) {//configuration.isDebuggerEnabled()) {
            if (debugger == null) {
                // Detect the debugger class to use.
                String className = null;
                // Use try block since we may not have permission to get a system
                // property (for example, when an applet).
                try {
                    className = System.getProperty("smack.debuggerClass");
                }
                catch (Throwable t) {
                    // Ignore.
                }
                Class<?> debuggerClass = null;
                if (className != null) {
                    try {
                        debuggerClass = Class.forName(className);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (debuggerClass == null) {
                    try {
                        debuggerClass =
                                Class.forName("org.jivesoftware.smackx.debugger.EnhancedDebugger");
                    }
                    catch (Exception ex) {
                        try {
                            debuggerClass =
                                    Class.forName("org.jivesoftware.smack.debugger.LiteDebugger");
                        }
                        catch (Exception ex2) {
                            ex2.printStackTrace();
                        }
                    }
                }
                // Create a new debugger instance. If an exception occurs then disable the debugging
                // option
                try {
                    Constructor<?> constructor = debuggerClass
                            .getConstructor(XMPPLLConnection.class, Writer.class, Reader.class);
                    debugger = (SmackDebugger) constructor.newInstance(this, writer, reader);
                    reader = debugger.getReader();
                    writer = debugger.getWriter();
                }
                catch (Exception e) {
                    e.printStackTrace();
                    DEBUG_ENABLED = false;
                }
            }
            else {
                // Obtain new reader and writer from the existing debugger
                reader = debugger.newConnectionReader(reader);
                writer = debugger.newConnectionWriter(writer);
            }
        }
    }

    protected void shutdown() {
        connection = null;

        if (packetReader != null)
            packetReader.shutdown();
        if (packetWriter != null)
            packetWriter.shutdown();

        // Wait 150 ms for processes to clean-up, then shutdown.
        try {
            Thread.sleep(150);
        }
        catch (Exception e) {
            // Ignore.
        }

        // Close down the readers and writers.
        if (reader != null) {
            try {
                reader.close();
            }
            catch (Throwable ignore) { /* ignore */ }
            reader = null;
        }
        if (writer != null) {
            try {
                writer.close();
            }
            catch (Throwable ignore) { /* ignore */ }
            writer = null;
        }

        try {
            socket.close();
        }
        catch (Exception e) {
            // Ignore.
        }
    } 

    public void disconnect() {
        // If not connected, ignore this request.
        if (packetReader == null || packetWriter == null) {
            return;
        }

        shutdown();

        packetWriter.cleanup();
        packetWriter = null;
        packetReader.cleanup();
        packetReader = null;
    }
}
