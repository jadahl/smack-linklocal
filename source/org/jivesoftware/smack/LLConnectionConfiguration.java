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

import javax.net.SocketFactory;
import java.net.Socket;

/**
 * Link-local connection configuration settings. Two general cases exists,
 * one where the we want to connect to a remote peer, and one when o remote
 * peer has connected to us.
 */
public class LLConnectionConfiguration implements Cloneable {
    private LLPresence remotePresence;
    private LLPresence localPresence;
    private Socket socket;

    private boolean debuggerEnabled = AbstractConnection.DEBUG_ENABLED;

    // Holds the socket factory that is used to generate the socket in the connection
    private SocketFactory socketFactory;

    /** 
     * Configuration used for connecting to remote peer.
     * @param local LLPresence for the local user
     * @param remote LLPresence for the remote user
     */
    LLConnectionConfiguration(LLPresence local, LLPresence remote) {
        this.localPresence = local;
        this.remotePresence = remote;
    }

    /** 
     * Instantiate a link-local configuration when the connection is acting as
     * the host.
     * 
     * @param local the local link-local presence class.
     * @param remoteSocket the socket which the new connection is assigned to.
     */
    LLConnectionConfiguration(LLPresence local, Socket remoteSocket) {
        this.localPresence = local;
        this.socket = remoteSocket;
    }

    /**
     * Tells if the connection is the initiating one.
     * @return true if this configuration is for the connecting connection.
     */
    public boolean isInitiator() {
        return socket == null;
    }

    /**
     * Return the service name of the remote peer.
     * @return the remote peer's service name.
     */
    public String getRemoteServiceName() {
        return remotePresence.getServiceName();
    }

    /**
     * Return the service name of this client.
     * @return this clients service name.
     */
    public String getLocalServiceName() {
        return localPresence.getServiceName();
    } 

    /**
     * Return this clients link-local presence information.
     * @return this clients link-local presence information.
     */
    public LLPresence getLocalPresence() {
        return localPresence;
    }

    /**
     * Return the remote client's link-local presence information.
     * @return the remote client's link-local presence information.
     */
    public LLPresence getRemotePresence() {
        return remotePresence;
    }

    /**
     * Return the socket which has been established when the
     * remote client connected.
     * @return the socket established when the remote client connected.
     */
    public Socket getSocket() {
        return socket;
    }
}
