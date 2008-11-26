/**
 * $RCSfile$
 * $Revision: 10856 $
 * $Date: 2008-10-31 11:51:12 +0800 (Fri, 31 Oct 2008) $
 *
 * Copyright 2003-2007 Jive Software.
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
 * Listens for XML traffic from the XMPP server and parses it into packet objects.
 * The packet reader also manages all packet listeners and collectors.<p>
 *
 * @see PacketCollector
 * @see PacketListener
 * @author Matt Tucker
 */
abstract class AbstractPacketReader {
    protected Thread readerThread;
    private ExecutorService listenerExecutor;

    private AbstractConnection connection;

    protected XmlPullParser parser;
    protected boolean done;
    private Collection<PacketCollector> collectors = new ConcurrentLinkedQueue<PacketCollector>();
    protected final Map<PacketListener, ListenerWrapper> listeners =
            new ConcurrentHashMap<PacketListener, ListenerWrapper>();
    protected final Collection<ConnectionListener> connectionListeners =
            new CopyOnWriteArrayList<ConnectionListener>();

    protected String connectionID = null;
    private Semaphore connectionSemaphore;

    protected AbstractPacketReader(final AbstractConnection connection) {
        this.connection = connection;
    }

    /**
     * Initializes the reader in order to be used. The reader is initialized during the
     * first connection and when reconnecting due to an abruptly disconnection.
     */
    protected void init() {
        done = false;
        connectionID = null;

        readerThread = new Thread() {
            public void run() {
                parsePackets(this);
            }
        };
        readerThread.setName("Smack Packet Reader (" + connection.connectionCounterValue + ")");
        readerThread.setDaemon(true);

        // Create an executor to deliver incoming packets to listeners. We'll use a single
        // thread with an unbounded queue.
        listenerExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {

            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable,
                        "Smack Listener Processor (" + connection.connectionCounterValue + ")");
                thread.setDaemon(true);
                return thread;
            }
        });

        resetParser();
    }

    /**
     * Creates a new packet collector for this reader. A packet filter determines
     * which packets will be accumulated by the collector.
     *
     * @param packetFilter the packet filter to use.
     * @return a new packet collector.
     */
    public PacketCollector createPacketCollector(PacketFilter packetFilter) {
        PacketCollector collector = new PacketCollector(this, packetFilter);
        collectors.add(collector);
        // Add the collector to the list of active collector.
        return collector;
    }

    protected void cancelPacketCollector(PacketCollector packetCollector) {
        collectors.remove(packetCollector);
    }

    /**
     * Registers a packet listener with this reader. A packet filter determines
     * which packets will be delivered to the listener.
     *
     * @param packetListener the packet listener to notify of new packets.
     * @param packetFilter the packet filter to use.
     */
    public void addPacketListener(PacketListener packetListener, PacketFilter packetFilter) {
        ListenerWrapper wrapper = new ListenerWrapper(packetListener, packetFilter);
        listeners.put(packetListener, wrapper);
    }

    /**
     * Removes a packet listener.
     *
     * @param packetListener the packet listener to remove.
     */
    public void removePacketListener(PacketListener packetListener) {
        listeners.remove(packetListener);
    }

    /**
     * Starts the packet reader thread and returns once a connection to the server
     * has been established. A connection will be attempted for a maximum of five
     * seconds. An XMPPException will be thrown if the connection fails.
     *
     * @throws XMPPException if the server fails to send an opening stream back
     *      for more than five seconds.
     */
    public void startup() throws XMPPException {
        connectionSemaphore = new Semaphore(1);

        readerThread.start();
        // Wait for stream tag before returing. We'll wait a couple of seconds before
        // giving up and throwing an error.
        try {
            connectionSemaphore.acquire();

            // A waiting thread may be woken up before the wait time or a notify
            // (although this is a rare thing). Therefore, we continue waiting
            // until either a connectionID has been set (and hence a notify was
            // made) or the total wait time has elapsed.
            int waitTime = SmackConfiguration.getPacketReplyTimeout();
            connectionSemaphore.tryAcquire(3 * waitTime, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException ie) {
            // Ignore.
        }
        if (connectionID == null) {
            throw new XMPPException("Connection failed. No response from server.");
        }
        else {
            connection.connectionID = connectionID;
        }
    }

    /**
     * Shuts the packet reader down.
     */
    public void shutdown() {
        // Notify connection listeners of the connection closing if done hasn't already been set.
        if (!done) {
            for (ConnectionListener listener : connectionListeners) {
                try {
                    listener.connectionClosed();
                }
                catch (Exception e) {
                    // Cath and print any exception so we can recover
                    // from a faulty listener and finish the shutdown process
                    e.printStackTrace();
                }
            }
        }
        done = true;

        // Shut down the listener executor.
        listenerExecutor.shutdown();
    }

    /**
     * Cleans up all resources used by the packet reader.
     */
    void cleanup() {
        connectionListeners.clear();
        listeners.clear();
        collectors.clear();
    }

    /**
     * Parse top-level packets in order to process them further.
     *
     * @param thread the thread that is being used by the reader to parse incoming packets.
     */
    abstract protected void parsePackets(Thread thread);


    /**
     * Sends out a notification that there was an error with the connection
     * and closes the connection.
     *
     * @param e the exception that causes the connection close event.
     */
    void notifyConnectionError(Exception e) {
        done = true;
        // Closes the connection temporary. A reconnection is possible
        connection.shutdown();
        // Print the stack trace to help catch the problem
        e.printStackTrace();
        // Notify connection listeners of the error.
        for (ConnectionListener listener : connectionListeners) {
            try {
                listener.connectionClosedOnError(e);
            }
            catch (Exception e2) {
                // Cath and print any exception so we can recover
                // from a faulty listener
                e2.printStackTrace();
            }
        }
    }

    /**
     * Sends a notification indicating that the connection was reconnected successfully.
     */
    protected void notifyReconnection() {
        // Notify connection listeners of the reconnection.
        for (ConnectionListener listener : connectionListeners) {
            try {
                listener.reconnectionSuccessful();
            }
            catch (Exception e) {
                // Cath and print any exception so we can recover
                // from a faulty listener
                e.printStackTrace();
            }
        }
    }

    /**
     * Resets the parser using the latest connection's reader. Reseting the parser is necessary
     * when the plain connection has been secured or when a new opening stream element is going
     * to be sent by the server.
     */
    protected void resetParser() {
        try {
            parser = new MXParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(connection.reader);
        }
        catch (XmlPullParserException xppe) {
            xppe.printStackTrace();
        }
    }

    /**
     * Releases the connection ID lock so that the thread that was waiting can resume. The
     * lock will be released when one of the following three conditions is met:<p>
     *
     * 1) An opening stream was sent from a non XMPP 1.0 compliant server
     * 2) Stream features were received from an XMPP 1.0 compliant server that does not support TLS
     * 3) TLS negotiation was successful
     *
     */
    protected void releaseConnectionIDLock() {
        connectionSemaphore.release();
    }

    /**
     * Processes a packet after it's been fully parsed by looping through the installed
     * packet collectors and listeners and letting them examine the packet to see if
     * they are a match with the filter.
     *
     * @param packet the packet to process.
     */
    protected void processPacket(Packet packet) {
        if (packet == null) {
            return;
        }

        // Loop through all collectors and notify the appropriate ones.
        for (PacketCollector collector: collectors) {
            collector.processPacket(packet);
        }

        // Deliver the incoming packet to listeners.
        listenerExecutor.submit(new ListenerNotification(packet));
    }

    protected StreamError parseStreamError(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        StreamError streamError = null;
        boolean done = false;
        while (!done) {
            int eventType = parser.next();

            if (eventType == XmlPullParser.START_TAG) {
                streamError = new StreamError(parser.getName());
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals("error")) {
                    done = true;
                }
            }
        }
        return streamError;
    }

    /**
     * A runnable to notify all listeners of a packet.
     */
    private class ListenerNotification implements Runnable {

        private Packet packet;

        public ListenerNotification(Packet packet) {
            this.packet = packet;
        }

        public void run() {
            for (ListenerWrapper listenerWrapper : listeners.values()) {
                listenerWrapper.notifyListener(packet);
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
    }
}
