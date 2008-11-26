/**
 * $RCSfile$
 * $Revision: 7419 $
 * $Date: 2007-03-09 10:02:27 +0800 (Fri, 09 Mar 2007) $
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
import org.jivesoftware.smack.packet.Packet;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Writes packets to a XMPP server. Packets are sent using a dedicated thread. Packet
 * interceptors can be registered to dynamically modify packets before they're actually
 * sent. Packet listeners can be registered to listen for all outgoing packets.
 *
 * @author Matt Tucker
 */
class PacketWriter {

    private Thread writerThread;
    private Thread keepAliveThread;
    private Writer writer;
    private AbstractConnection connection;
    private final BlockingQueue<Packet> queue;
    private boolean done;
    
    private final Map<PacketListener, ListenerWrapper> listeners =
            new ConcurrentHashMap<PacketListener, ListenerWrapper>();

    /**
     * Timestamp when the last stanza was sent to the server. This information is used
     * by the keep alive process to only send heartbeats when the connection has been idle.
     */
    private long lastActive = System.currentTimeMillis();

    /**
     * List of PacketInterceptors that will be notified when a new packet is about to be
     * sent to the server. These interceptors may modify the packet before it is being
     * actually sent to the server.
     */
    private final Map<PacketInterceptor, InterceptorWrapper> interceptors =
            new ConcurrentHashMap<PacketInterceptor, InterceptorWrapper>();

    /**
     * Creates a new packet writer with the specified connection.
     *
     * @param connection the connection.
     */
    protected PacketWriter(AbstractConnection connection) {
        this.queue = new ArrayBlockingQueue<Packet>(500, true);
        this.connection = connection;
        init();
    }

    /** 
    * Initializes the writer in order to be used. It is called at the first connection and also 
    * is invoked if the connection is disconnected by an error.
    */ 
    protected void init() {
        this.writer = connection.writer;
        done = false;

        writerThread = new Thread() {
            public void run() {
                writePackets(this);
            }
        };
        writerThread.setName("Smack Packet Writer (" + connection.connectionCounterValue + ")");
        writerThread.setDaemon(true);
    }

    /**
     * Sends the specified packet to the server.
     *
     * @param packet the packet to send.
     */
    public void sendPacket(Packet packet) {
        if (!done) {
            // Invoke interceptors for the new packet that is about to be sent. Interceptors
            // may modify the content of the packet.
            processInterceptors(packet);

            try {
                queue.put(packet);
            }
            catch (InterruptedException ie) {
                ie.printStackTrace();
                return;
            }
            synchronized (queue) {
                queue.notifyAll();
            }

            // Process packet writer listeners. Note that we're using the sending
            // thread so it's expected that listeners are fast.
            processListeners(packet);
        }
    }

    /**
     * Registers a packet listener with this writer. The listener will be
     * notified immediately after every packet this writer sends. A packet filter
     * determines which packets will be delivered to the listener. Note that the thread
     * that writes packets will be used to invoke the listeners. Therefore, each
     * packet listener should complete all operations quickly or use a different
     * thread for processing.
     *
     * @param packetListener the packet listener to notify of sent packets.
     * @param packetFilter the packet filter to use.
     */
    public void addPacketListener(PacketListener packetListener, PacketFilter packetFilter) {
        listeners.put(packetListener, new ListenerWrapper(packetListener, packetFilter));
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
     * Returns the number of registered packet listeners.
     *
     * @return the count of packet listeners.
     */
    public int getPacketListenerCount() {
        return listeners.size();
    }

    /**
     * Registers a packet interceptor with this writer. The interceptor will be
     * notified of every packet that this writer is about to send. Interceptors
     * may modify the packet to be sent. A packet filter determines which packets
     * will be delivered to the interceptor.
     *
     * @param packetInterceptor the packet interceptor to notify of packets about to be sent.
     * @param packetFilter the packet filter to use.
     */
    public void addPacketInterceptor(PacketInterceptor packetInterceptor, PacketFilter packetFilter) {
        interceptors.put(packetInterceptor, new InterceptorWrapper(packetInterceptor, packetFilter));
    }

    /**
     * Removes a packet interceptor.
     *
     * @param packetInterceptor the packet interceptor to remove.
     */
    public void removePacketInterceptor(PacketInterceptor packetInterceptor) {
        interceptors.remove(packetInterceptor);
    }

    /**
     * Starts the packet writer thread and opens a connection to the server. The
     * packet writer will continue writing packets until {@link #shutdown} or an
     * error occurs.
     */
    public void startup() {
        writerThread.start();
    }

    /**
     * Starts the keep alive process. A white space (aka heartbeat) is going to be
     * sent to the server every 30 seconds (by default) since the last stanza was sent
     * to the server.
     */
    void startKeepAliveProcess() {
        // Schedule a keep-alive task to run if the feature is enabled. will write
        // out a space character each time it runs to keep the TCP/IP connection open.
        int keepAliveInterval = SmackConfiguration.getKeepAliveInterval();
        if (keepAliveInterval > 0) {
            KeepAliveTask task = new KeepAliveTask(keepAliveInterval);
            keepAliveThread = new Thread(task);
            task.setThread(keepAliveThread);
            keepAliveThread.setDaemon(true);
            keepAliveThread.setName("Smack Keep Alive (" + connection.connectionCounterValue + ")");
            keepAliveThread.start();
        }
    }

    void setWriter(Writer writer) {
        this.writer = writer;
    }

    /**
     * Shuts down the packet writer. Once this method has been called, no further
     * packets will be written to the server.
     */
    public void shutdown() {
        done = true;
        synchronized (queue) {
            queue.notifyAll();
        }
    }

    /**
     * Cleans up all resources used by the packet writer.
     */
    void cleanup() {
        interceptors.clear();
        listeners.clear();
    }

    /**
     * Returns the next available packet from the queue for writing.
     *
     * @return the next packet for writing.
     */
    private Packet nextPacket() {
        Packet packet = null;
        // Wait until there's a packet or we're done.
        while (!done && (packet = queue.poll()) == null) {
            try {
                synchronized (queue) {
                    queue.wait();
                }
            }
            catch (InterruptedException ie) {
                // Do nothing
            }
        }
        return packet;
    }

    private void writePackets(Thread thisThread) {
        try {
            // Open the stream.
            openStream();
            // Write out packets from the queue.
            while (!done && (writerThread == thisThread)) {
                Packet packet = nextPacket();
                if (packet != null) {
                    synchronized (writer) {
                        writer.write(packet.toXML());
                        writer.flush();
                        // Keep track of the last time a stanza was sent to the server
                        lastActive = System.currentTimeMillis();
                    }
                }
            }
            // Flush out the rest of the queue. If the queue is extremely large, it's possible
            // we won't have time to entirely flush it before the socket is forced closed
            // by the shutdown process.
            try {
                synchronized (writer) {
                   while (!queue.isEmpty()) {
                       Packet packet = queue.remove();
                        writer.write(packet.toXML());
                    }
                    writer.flush();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            // Delete the queue contents (hopefully nothing is left).
            queue.clear();

            // Close the stream.
            try {
                writer.write("</stream:stream>");
                writer.flush();
            }
            catch (Exception e) {
                // Do nothing
            }
            finally {
                try {
                    writer.close();
                }
                catch (Exception e) {
                    // Do nothing
                }
            }
        }
        catch (IOException ioe){
            if (!done) {
                done = true;
                connection.packetReader.notifyConnectionError(ioe);
            }
        }
    }

    /**
     * Process listeners.
     *
     * @param packet the packet to process.
     */
    private void processListeners(Packet packet) {
        // Notify the listeners of the new sent packet
        for (ListenerWrapper listenerWrapper : listeners.values()) {
            listenerWrapper.notifyListener(packet);
        }
    }

    /**
     * Process interceptors. Interceptors may modify the packet that is about to be sent.
     * Since the thread that requested to send the packet will invoke all interceptors, it
     * is important that interceptors perform their work as soon as possible so that the
     * thread does not remain blocked for a long period.
     *
     * @param packet the packet that is going to be sent to the server
     */
    private void processInterceptors(Packet packet) {
        if (packet != null) {
            for (InterceptorWrapper interceptorWrapper : interceptors.values()) {
                interceptorWrapper.notifyListener(packet);
            }
        }
    }

    /**
     * Sends to the server a new stream element. This operation may be requested several times
     * so we need to encapsulate the logic in one place. This message will be sent while doing
     * TLS, SASL and resource binding.
     *
     * @throws IOException If an error occurs while sending the stanza to the server.
     */
    void openStream() throws IOException {
        StringBuilder stream = new StringBuilder();
        stream.append("<stream:stream");
        stream.append(" to=\"").append(connection.serviceName).append("\"");
        // If the connection is a Link-local connection, a 'from' attribute
        // shoud be added.
       if (connection instanceof XMPPLLConnection) 
           stream.append(" from=\"").append(connection.getUser()).append("\"");
        stream.append(" xmlns=\"jabber:client\"");
        stream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
        stream.append(" version=\"1.0\">");
        writer.write(stream.toString());
        writer.flush();
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

    /**
     * A wrapper class to associate a packet filter with an interceptor.
     */
    private static class InterceptorWrapper {

        private PacketInterceptor packetInterceptor;
        private PacketFilter packetFilter;

        public InterceptorWrapper(PacketInterceptor packetInterceptor, PacketFilter packetFilter)
        {
            this.packetInterceptor = packetInterceptor;
            this.packetFilter = packetFilter;
        }

        public boolean equals(Object object) {
            if (object == null) {
                return false;
            }
            if (object instanceof InterceptorWrapper) {
                return ((InterceptorWrapper) object).packetInterceptor
                        .equals(this.packetInterceptor);
            }
            else if (object instanceof PacketInterceptor) {
                return object.equals(this.packetInterceptor);
            }
            return false;
        }

        public void notifyListener(Packet packet) {
            if (packetFilter == null || packetFilter.accept(packet)) {
                packetInterceptor.interceptPacket(packet);
            }
        }
    }

    /**
     * A TimerTask that keeps connections to the server alive by sending a space
     * character on an interval.
     */
    private class KeepAliveTask implements Runnable {

        private int delay;
        private Thread thread;

        public KeepAliveTask(int delay) {
            this.delay = delay;
        }

        protected void setThread(Thread thread) {
            this.thread = thread;
        }
        
        public void run() {
            try {
                // Sleep 15 seconds before sending first heartbeat. This will give time to
                // properly finish TLS negotiation and then start sending heartbeats.
                Thread.sleep(15000);
            }
            catch (InterruptedException ie) {
                // Do nothing
            }
            while (!done && keepAliveThread == thread) {
                synchronized (writer) {
                    // Send heartbeat if no packet has been sent to the server for a given time
                    if (System.currentTimeMillis() - lastActive >= delay) {
                        try {
                            writer.write(" ");
                            writer.flush();
                        }
                        catch (Exception e) {
                            // Do nothing
                        }
                    }
                }
                try {
                    // Sleep until we should write the next keep-alive.
                    Thread.sleep(delay);
                }
                catch (InterruptedException ie) {
                    // Do nothing
                }
            }
        }
    }
}
