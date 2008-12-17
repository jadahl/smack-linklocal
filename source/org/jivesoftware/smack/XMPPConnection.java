/**
 * $RCSfile$
 * $Revision: 10876 $
 * $Date: 2008-11-14 02:31:37 +0800 (Fri, 14 Nov 2008) $
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

import org.jivesoftware.smack.debugger.SmackDebugger;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.util.StringUtils;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates a connection to a XMPP server. A simple use of this API might
 * look like the following:
 * <pre>
 * // Create a connection to the igniterealtime.org XMPP server.
 * XMPPConnection con = new XMPPConnection("igniterealtime.org");
 * // Connect to the server
 * con.connect();
 * // Most servers require you to login before performing other tasks.
 * con.login("jsmith", "mypass");
 * // Start a new conversation with John Doe and send him a message.
 * Chat chat = connection.getChatManager().createChat("jdoe@igniterealtime.org"</font>, new MessageListener() {
 * <p/>
 *     public void processMessage(Chat chat, Message message) {
 *         // Print out any messages we get back to standard out.
 *         System.out.println(<font color="green">"Received message: "</font> + message);
 *     }
 * });
 * chat.sendMessage(<font color="green">"Howdy!"</font>);
 * // Disconnect from the server
 * con.disconnect();
 * </pre>
 * <p/>
 * XMPPConnections can be reused between connections. This means that an XMPPConnection
 * may be connected, disconnected and then connected again. Listeners of the XMPPConnection
 * will be retained accross connections.<p>
 * <p/>
 * If a connected XMPPConnection gets disconnected abruptly then it will try to reconnect
 * again. To stop the reconnection process, use {@link #disconnect()}. Once stopped
 * you can use {@link #connect()} to manually connect to the server.
 *
 * @author Matt Tucker
 */
public class XMPPConnection extends AbstractConnection {
    private final static Set<ConnectionCreationListener> connectionEstablishedListeners =
            new CopyOnWriteArraySet<ConnectionCreationListener>();

    private String user = null;
    /**
     * Flag that indicates if the user is currently authenticated with the server.
     */
    private boolean authenticated = false;

    /**
     * Flag that indicates if the user was authenticated with the server when the connection
     * to the server was closed (abruptly or not).
     */
    private boolean wasAuthenticated = false;
    private boolean anonymous = false;
    private boolean usingTLS = false;

    Roster roster = null;
    private AccountManager accountManager = null;
    private SASLAuthentication saslAuthentication = new SASLAuthentication(this);

    // CallbackHandler to handle prompting for theh keystore password.
    private CallbackHandler callbackHandler = null;

    /**
     * Collection of available stream compression methods offered by the server.
     */
    private Collection compressionMethods;
    /**
     * Flag that indicates if stream compression is actually in use.
     */
    private boolean usingCompression;
    /**
     * Holds the initial configuration used while creating the connection.
     */
    private ConnectionConfiguration configuration;
    private ChatManager chatManager;

    static {
        // Use try block since we may not have permission to get a system
        // property (for example, when an applet).
        try {
            DEBUG_ENABLED = Boolean.getBoolean("smack.debugEnabled");
        }
        catch (Exception e) {
            // Ignore.
        }
        // Ensure the SmackConfiguration class is loaded by calling a method in it.
        //
        // FIXME loading the class here makes the updated ServiceDiscoveryManager load
        // before XMPPLLConnection, causing a NullPointerException when adding
        // link-local connection creation listener.
        SmackConfiguration.getVersion();
    }

    /**
     * Creates a new connection to the specified XMPP server. A DNS SRV lookup will be
     * performed to determine the IP address and port corresponding to the
     * service name; if that lookup fails, it's assumed that server resides at
     * <tt>serviceName</tt> with the default port of 5222. Encrypted connections (TLS)
     * will be used if available, stream compression is disabled, and standard SASL
     * mechanisms will be used for authentication.<p>
     * <p/>
     * This is the simplest constructor for connecting to an XMPP server. Alternatively,
     * you can get fine-grained control over connection settings using the
     * {@link #XMPPConnection(ConnectionConfiguration)} constructor.<p>
     * <p/>
     * Note that XMPPConnection constructors do not establish a connection to the server
     * and you must call {@link #connect()}.<p>
     * <p/>
     * The CallbackHandler will only be used if the connection requires the client provide
     * an SSL certificate to the server. The CallbackHandler must handle the PasswordCallback
     * to prompt for a password to unlock the keystore containing the SSL certificate.
     *
     * @param serviceName the name of the XMPP server to connect to; e.g. <tt>example.com</tt>.
     * @param callbackHandler the CallbackHandler used to prompt for the password to the keystore.
     */
    public XMPPConnection(String serviceName, CallbackHandler callbackHandler) {
        // Create the configuration for this new connection
        ConnectionConfiguration config = new ConnectionConfiguration(serviceName);
        config.setCompressionEnabled(false);
        config.setSASLAuthenticationEnabled(true);
        config.setDebuggerEnabled(DEBUG_ENABLED);
        this.configuration = config;
        this.callbackHandler = callbackHandler;
    }

    /**
     * Creates a new XMPP conection in the same way {@link #XMPPConnection(String,CallbackHandler)} does, but
     * with no callback handler for password prompting of the keystore.  This will work
     * in most cases, provided the client is not required to provide a certificate to 
     * the server.
     *
     * @param serviceName the name of the XMPP server to connect to; e.g. <tt>example.com</tt>.
     */
    public XMPPConnection(String serviceName) {
        // Create the configuration for this new connection
        ConnectionConfiguration config = new ConnectionConfiguration(serviceName);
        config.setCompressionEnabled(false);
        config.setSASLAuthenticationEnabled(true);
        config.setDebuggerEnabled(DEBUG_ENABLED);
        this.configuration = config;
        this.callbackHandler = null;
    }

    /**
     * Creates a new XMPP conection in the same way {@link #XMPPConnection(ConnectionConfiguration,CallbackHandler)} does, but
     * with no callback handler for password prompting of the keystore.  This will work
     * in most cases, provided the client is not required to provide a certificate to 
     * the server.
     *
     *
     * @param config the connection configuration.
     */
    public XMPPConnection(ConnectionConfiguration config) {
        this.configuration = config;
        this.callbackHandler = null;
    }

    /**
     * Creates a new XMPP connection using the specified connection configuration.<p>
     * <p/>
     * Manually specifying connection configuration information is suitable for
     * advanced users of the API. In many cases, using the
     * {@link #XMPPConnection(String)} constructor is a better approach.<p>
     * <p/>
     * Note that XMPPConnection constructors do not establish a connection to the server
     * and you must call {@link #connect()}.<p>
     * <p/>
     *
     * The CallbackHandler will only be used if the connection requires the client provide
     * an SSL certificate to the server. The CallbackHandler must handle the PasswordCallback
     * to prompt for a password to unlock the keystore containing the SSL certificate.
     *
     * @param config the connection configuration.
     * @param callbackHandler the CallbackHandler used to prompt for the password to the keystore.
     */
    public XMPPConnection(ConnectionConfiguration config, CallbackHandler callbackHandler) {
        this.configuration = config;
        this.callbackHandler = callbackHandler;
    }


    /**
     * Returns the full XMPP address of the user that is logged in to the connection or
     * <tt>null</tt> if not logged in yet. An XMPP address is in the form
     * username@server/resource.
     *
     * @return the full XMPP address of the user logged in.
     */
    public String getUser() {
        if (!isAuthenticated()) {
            return null;
        }
        return user;
    }

    /**
     * Logs in to the server using the strongest authentication mode supported by
     * the server, then sets presence to available. If more than five seconds
     * (default timeout) elapses in each step of the authentication process without
     * a response from the server, or if an error occurs, a XMPPException will be thrown.<p>
     *
     * It is possible to log in without sending an initial available presence by using
     * {@link ConnectionConfiguration#setSendPresence(boolean)}. If this connection is
     * not interested in loading its roster upon login then use
     * {@link ConnectionConfiguration#setRosterLoadedAtLogin(boolean)}.
     * Finally, if you want to not pass a password and instead use a more advanced mechanism
     * while using SASL then you may be interested in using
     * {@link ConnectionConfiguration#setCallbackHandler(javax.security.auth.callback.CallbackHandler)}.
     * For more advanced login settings see {@link ConnectionConfiguration}.
     *
     * @param username the username.
     * @param password the password or <tt>null</tt> if using a CallbackHandler.
     * @throws XMPPException if an error occurs.
     */
    public void login(String username, String password) throws XMPPException {
        login(username, password, "Smack");
    }

    /**
     * Logs in to the server using the strongest authentication mode supported by
     * the server. If the server supports SASL authentication then the user will be
     * authenticated using SASL if not Non-SASL authentication will be tried. If more than
     * five seconds (default timeout) elapses in each step of the authentication process
     * without a response from the server, or if an error occurs, a XMPPException will be
     * thrown.<p>
     * 
     * Before logging in (i.e. authenticate) to the server the connection must be connected.
     * For compatibility and easiness of use the connection will automatically connect to the
     * server if not already connected.<p>
     *
     * It is possible to log in without sending an initial available presence by using
     * {@link ConnectionConfiguration#setSendPresence(boolean)}. If this connection is
     * not interested in loading its roster upon login then use
     * {@link ConnectionConfiguration#setRosterLoadedAtLogin(boolean)}.
     * Finally, if you want to not pass a password and instead use a more advanced mechanism
     * while using SASL then you may be interested in using
     * {@link ConnectionConfiguration#setCallbackHandler(javax.security.auth.callback.CallbackHandler)}.
     * For more advanced login settings see {@link ConnectionConfiguration}.
     *
     * @param username the username.
     * @param password the password or <tt>null</tt> if using a CallbackHandler.
     * @param resource the resource.
     * @throws XMPPException if an error occurs.
     * @throws IllegalStateException if not connected to the server, or already logged in
     *      to the serrver.
     */
    public synchronized void login(String username, String password, String resource) throws XMPPException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server.");
        }
        if (authenticated) {
            throw new IllegalStateException("Already logged in to server.");
        }
        // Do partial version of nameprep on the username.
        username = username.toLowerCase().trim();

        String response;
        if (configuration.isSASLAuthenticationEnabled() &&
                saslAuthentication.hasNonAnonymousAuthentication()) {
            // Authenticate using SASL
            if (password != null) {
                response = saslAuthentication.authenticate(username, password, resource);
            }
            else {
                response = saslAuthentication
                        .authenticate(username, resource, configuration.getCallbackHandler());
            }
        }
        else {
            // Authenticate using Non-SASL
            response = new NonSASLAuthentication(this).authenticate(username, password, resource);
        }

        // Set the user.
        if (response != null) {
            this.user = response;
            // Update the serviceName with the one returned by the server
            this.serviceName = StringUtils.parseServer(response);
        }
        else {
            this.user = username + "@" + this.serviceName;
            if (resource != null) {
                this.user += "/" + resource;
            }
        }

        // If compression is enabled then request the server to use stream compression
        if (configuration.isCompressionEnabled()) {
            useCompression();
        }

        // Create the roster if it is not a reconnection.
        if (this.roster == null) {
            this.roster = new Roster(this);
        }
        if (configuration.isRosterLoadedAtLogin()) {
        	roster.reload();
        }

        // Set presence to online.
        if (configuration.isSendPresence()) {
            packetWriter.sendPacket(new Presence(Presence.Type.available));
        }

        // Indicate that we're now authenticated.
        authenticated = true;
        anonymous = false;

        // Stores the autentication for future reconnection
        this.getConfiguration().setLoginInfo(username, password, resource);

        // If debugging is enabled, change the the debug window title to include the
        // name we are now logged-in as.
        // If DEBUG_ENABLED was set to true AFTER the connection was created the debugger
        // will be null
        if (configuration.isDebuggerEnabled() && debugger != null) {
            debugger.userHasLogged(user);
        }
    }

    /**
     * Logs in to the server anonymously. Very few servers are configured to support anonymous
     * authentication, so it's fairly likely logging in anonymously will fail. If anonymous login
     * does succeed, your XMPP address will likely be in the form "server/123ABC" (where "123ABC"
     * is a random value generated by the server).
     *
     * @throws XMPPException if an error occurs or anonymous logins are not supported by the server.
     * @throws IllegalStateException if not connected to the server, or already logged in
     *      to the serrver.
     */
    public synchronized void loginAnonymously() throws XMPPException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server.");
        }
        if (authenticated) {
            throw new IllegalStateException("Already logged in to server.");
        }

        String response;
        if (configuration.isSASLAuthenticationEnabled() &&
                saslAuthentication.hasAnonymousAuthentication()) {
            response = saslAuthentication.authenticateAnonymously();
        }
        else {
            // Authenticate using Non-SASL
            response = new NonSASLAuthentication(this).authenticateAnonymously();
        }

        // Set the user value.
        this.user = response;
        // Update the serviceName with the one returned by the server
        this.serviceName = StringUtils.parseServer(response);

        // If compression is enabled then request the server to use stream compression
        if (configuration.isCompressionEnabled()) {
            useCompression();
        }

        // Anonymous users can't have a roster.
        roster = null;

        // Set presence to online.
        packetWriter.sendPacket(new Presence(Presence.Type.available));

        // Indicate that we're now authenticated.
        authenticated = true;
        anonymous = true;

        // If debugging is enabled, change the the debug window title to include the
        // name we are now logged-in as.
        // If DEBUG_ENABLED was set to true AFTER the connection was created the debugger
        // will be null
        if (configuration.isDebuggerEnabled() && debugger != null) {
            debugger.userHasLogged(user);
        }
    }

    /**
     * Returns the roster for the user logged into the server. If the user has not yet
     * logged into the server (or if the user is logged in anonymously), this method will return
     * <tt>null</tt>.
     *
     * @return the user's roster, or <tt>null</tt> if the user has not logged in yet.
     */
    public Roster getRoster() {
    	if (!configuration.isRosterLoadedAtLogin()) {
            roster.reload();
    	}
        if (roster == null) {
            return null;
        }
        // If this is the first time the user has asked for the roster after calling
        // login, we want to wait for the server to send back the user's roster. This
        // behavior shields API users from having to worry about the fact that roster
        // operations are asynchronous, although they'll still have to listen for
        // changes to the roster. Note: because of this waiting logic, internal
        // Smack code should be wary about calling the getRoster method, and may need to
        // access the roster object directly.
        if (!roster.rosterInitialized) {
            try {
                synchronized (roster) {
                    long waitTime = SmackConfiguration.getPacketReplyTimeout();
                    long start = System.currentTimeMillis();
                    while (!roster.rosterInitialized) {
                        if (waitTime <= 0) {
                            break;
                        }
                        roster.wait(waitTime);
                        long now = System.currentTimeMillis();
                        waitTime -= now - start;
                        start = now;
                    }
                }
            }
            catch (InterruptedException ie) {
                // Ignore.
            }
        }
        return roster;
    }

    /**
     * Returns an account manager instance for this connection.
     *
     * @return an account manager for this connection.
     */
    public synchronized AccountManager getAccountManager() {
        if (accountManager == null) {
            accountManager = new AccountManager(this);
        }
        return accountManager;
    }

    /**
     * Returns a chat manager instance for this connection. The ChatManager manages all incoming and
     * outgoing chats on the current connection.
     *
     * @return a chat manager instance for this connection.
     */
    public synchronized ChatManager getChatManager() {
        if (this.chatManager == null) {
            this.chatManager = new ChatManager(this);
        }
        return this.chatManager;
    }

    /**
     * Returns true if the connection to the server has successfully negotiated TLS. Once TLS
     * has been negotiatied the connection has been secured. @see #isUsingTLS. 
     *
     * @return true if a secure connection to the server.
     */
    public boolean isSecureConnection() {
        return isUsingTLS();
    }

    /**
     * Returns true if currently authenticated by successfully calling the login method.
     *
     * @return true if authenticated.
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Returns true if currently authenticated anonymously.
     *
     * @return true if authenticated anonymously.
     */
    public boolean isAnonymous() {
        return anonymous;
    }

    /**
     * Closes the connection by setting presence to unavailable then closing the stream to
     * the XMPP server. The shutdown logic will be used during a planned disconnection or when
     * dealing with an unexpected disconnection. Unlike {@link #disconnect()} the connection's
     * packet reader, packet writer, and {@link Roster} will not be removed; thus
     * connection's state is kept.
     *
     * @param unavailablePresence the presence packet to send during shutdown.
     */
    protected void shutdown(Presence unavailablePresence) {
        // Set presence to offline.
        packetWriter.sendPacket(unavailablePresence);

        this.setWasAuthenticated(authenticated);
        authenticated = false;
        connected = false;

        packetReader.shutdown();
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

        saslAuthentication.init();
    }

    /**
     * Shuts down the connection by calling #shutdown(Presence) with a basic
     * unavailable presence.
     *
     * @see #shutdown(Presence)
     */
    protected void shutdown() {
        shutdown(new Presence(Presence.Type.unavailable));
    }


    /**
     * Closes the connection by setting presence to unavailable then closing the stream to
     * the XMPP server. The XMPPConnection can still be used for connecting to the server
     * again.<p>
     * <p/>
     * This method cleans up all resources used by the connection. Therefore, the roster,
     * listeners and other stateful objects cannot be re-used by simply calling connect()
     * on this connection again. This is unlike the behavior during unexpected disconnects
     * (and subsequent connections). In that case, all state is preserved to allow for
     * more seamless error recovery.
     */
    public void disconnect() {
        disconnect(new Presence(Presence.Type.unavailable));
    }

    /**
     * Closes the connection. A custom unavailable presence is sent to the server, followed
     * by closing the stream. The XMPPConnection can still be used for connecting to the server
     * again. A custom unavilable presence is useful for communicating offline presence
     * information such as "On vacation". Typically, just the status text of the presence
     * packet is set with online information, but most XMPP servers will deliver the full
     * presence packet with whatever data is set.<p>
     * <p/>
     * This method cleans up all resources used by the connection. Therefore, the roster,
     * listeners and other stateful objects cannot be re-used by simply calling connect()
     * on this connection again. This is unlike the behavior during unexpected disconnects
     * (and subsequent connections). In that case, all state is preserved to allow for
     * more seamless error recovery.
     *
     * @param unavailablePresence the presence packet to send during shutdown.
     */
    public void disconnect(Presence unavailablePresence) {
        // If not connected, ignore this request.
        if (packetReader == null || packetWriter == null) {
            return;
        }

        shutdown(unavailablePresence);

        if (roster != null) {
            roster.cleanup();
            roster = null;
        }

        wasAuthenticated = false;

        packetWriter.cleanup();
        packetWriter = null;
        packetReader.cleanup();
        packetReader = null;
    }
    /**
     * Adds a new listener that will be notified when new XMPPConnections are created. Note
     * that newly created connections will not be actually connected to the server.
     *
     * @param connectionCreationListener a listener interested on new connections.
     */
    public static void addConnectionCreationListener(
            ConnectionCreationListener connectionCreationListener) {
        connectionEstablishedListeners.add(connectionCreationListener);
    }

    /**
     * Removes a listener that was interested in connection creation events.
     *
     * @param connectionCreationListener a listener interested on new connections.
     */
    public static void removeConnectionCreationListener(
            ConnectionCreationListener connectionCreationListener) {
        connectionEstablishedListeners.remove(connectionCreationListener);
    }

    private void connectUsingConfiguration(ConnectionConfiguration config) throws XMPPException {
        this.host = config.getHost();
        this.port = config.getPort();
        try {
            if (config.getSocketFactory() == null) {
                this.socket = new Socket(host, port);
            }
            else {
                this.socket = config.getSocketFactory().createSocket(host, port);
            }
        }
        catch (UnknownHostException uhe) {
            String errorMessage = "Could not connect to " + host + ":" + port + ".";
            throw new XMPPException(errorMessage, new XMPPError(
                    XMPPError.Condition.remote_server_timeout, errorMessage),
                    uhe);
        }
        catch (IOException ioe) {
            String errorMessage = "XMPPError connecting to " + host + ":"
                    + port + ".";
            throw new XMPPException(errorMessage, new XMPPError(
                    XMPPError.Condition.remote_server_error, errorMessage), ioe);
        }
        this.serviceName = config.getServiceName();
        initConnection();
    }

    /**
     * Initializes the connection by creating a packet reader and writer and opening a
     * XMPP stream to the server.
     *
     * @throws XMPPException if establishing a connection to the server fails.
     */
    private void initConnection() throws XMPPException {
        boolean isFirstInitialization = packetReader == null || packetWriter == null;
        if (!isFirstInitialization) {
            usingCompression = false;
        }

        // Set the reader and writer instance variables
        initReaderAndWriter();

        try {
            if (isFirstInitialization) {
                packetWriter = new PacketWriter(this);
                packetReader = new PacketReader(this);

                // If debugging is enabled, we should start the thread that will listen for
                // all packets and then log them.
                if (configuration.isDebuggerEnabled()) {
                    packetReader.addPacketListener(debugger.getReaderListener(), null);
                    if (debugger.getWriterListener() != null) {
                        packetWriter.addPacketListener(debugger.getWriterListener(), null);
                    }
                }
            }
            else {
                packetWriter.init();
                packetReader.init();
            }

            // Start the packet writer. This will open a XMPP stream to the server
            packetWriter.startup();
            // Start the packet reader. The startup() method will block until we
            // get an opening stream packet back from server.
            packetReader.startup();

            // Make note of the fact that we're now connected.
            connected = true;

            // Start keep alive process (after TLS was negotiated - if available)
            packetWriter.startKeepAliveProcess();


            if (isFirstInitialization) {
                // Notify listeners that a new connection has been established
                for (ConnectionCreationListener listener : connectionEstablishedListeners) {
                    listener.connectionCreated(this);
                }
            }
            else {
                packetReader.notifyReconnection();
            }

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
                catch (Throwable ignore) {  /* ignore */}
                writer = null;
            }
            if (socket != null) {
                try {
                    socket.close();
                }
                catch (Exception e) { /* ignore */ }
                socket = null;
            }
            this.setWasAuthenticated(authenticated);
            authenticated = false;
            connected = false;

            throw ex;        // Everything stoppped. Now throw the exception.
        }
    }

    private void initReaderAndWriter() throws XMPPException {
        try {
            if (!usingCompression) {
                reader =
                        new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            }
            else {
                try {
                    Class<?> zoClass = Class.forName("com.jcraft.jzlib.ZOutputStream");
                    Constructor<?> constructor =
                            zoClass.getConstructor(OutputStream.class, Integer.TYPE);
                    Object out = constructor.newInstance(socket.getOutputStream(), 9);
                    Method method = zoClass.getMethod("setFlushMode", Integer.TYPE);
                    method.invoke(out, 2);
                    writer =
                            new BufferedWriter(new OutputStreamWriter((OutputStream) out, "UTF-8"));

                    Class<?> ziClass = Class.forName("com.jcraft.jzlib.ZInputStream");
                    constructor = ziClass.getConstructor(InputStream.class);
                    Object in = constructor.newInstance(socket.getInputStream());
                    method = ziClass.getMethod("setFlushMode", Integer.TYPE);
                    method.invoke(in, 2);
                    reader = new BufferedReader(new InputStreamReader((InputStream) in, "UTF-8"));
                }
                catch (Exception e) {
                    e.printStackTrace();
                    reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    writer = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                }
            }
        }
        catch (IOException ioe) {
            throw new XMPPException(
                    "XMPPError establishing connection with server.",
                    new XMPPError(XMPPError.Condition.remote_server_error,
                            "XMPPError establishing connection with server."),
                    ioe);
        }

        // If debugging is enabled, we open a window and write out all network traffic.
        System.err.println("Debugging enabled? " + configuration.isDebuggerEnabled());
        //if (configuration.isDebuggerEnabled()) {
        if (true) {
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
                            .getConstructor(XMPPConnection.class, Writer.class, Reader.class);
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

    /***********************************************
     * TLS code below
     **********************************************/

    /**
     * Returns true if the connection to the server has successfully negotiated TLS. Once TLS
     * has been negotiatied the connection has been secured.
     *
     * @return true if the connection to the server has successfully negotiated TLS.
     */
    public boolean isUsingTLS() {
        return usingTLS;
    }

    /**
     * Returns the SASLAuthentication manager that is responsible for authenticating with
     * the server.
     *
     * @return the SASLAuthentication manager that is responsible for authenticating with
     *         the server.
     */
    public SASLAuthentication getSASLAuthentication() {
        return saslAuthentication;
    }

    /**
     * Returns the configuration used to connect to the server.
     *
     * @return the configuration used to connect to the server.
     */
    protected ConnectionConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Returns true if a presence automatically will be sent upon a successful
     * login.
     *
     * @return true if a presence will be sent upon a successful login or false
     * if not.
     */
    public boolean isSendPresence() {
        return configuration.isSendPresence();
    }

    /**
     * Notification message saying that the server supports TLS so confirm the server that we
     * want to secure the connection.
     *
     * @param required true when the server indicates that TLS is required.
     */
    void startTLSReceived(boolean required) {
        if (required && configuration.getSecurityMode() ==
                ConnectionConfiguration.SecurityMode.disabled) {
            packetReader.notifyConnectionError(new IllegalStateException(
                    "TLS required by server but not allowed by connection configuration"));
            return;
        }

        if (configuration.getSecurityMode() == ConnectionConfiguration.SecurityMode.disabled) {
            // Do not secure the connection using TLS since TLS was disabled
            return;
        }
        try {
            writer.write("<starttls xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>");
            writer.flush();
        }
        catch (IOException e) {
            packetReader.notifyConnectionError(e);
        }
    }

    /**
     * The server has indicated that TLS negotiation can start. We now need to secure the
     * existing plain connection and perform a handshake. This method won't return until the
     * connection has finished the handshake or an error occured while securing the connection.
     *
     * @throws Exception if an exception occurs.
     */
    void proceedTLSReceived() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        KeyStore ks = null;
        KeyManager[] kms = null;
        PasswordCallback pcb = null;

        if(callbackHandler == null) {
           ks = null;
        } else {
            //System.out.println("Keystore type: "+configuration.getKeystoreType());
            if(configuration.getKeystoreType().equals("NONE")) {
                ks = null;
                pcb = null;
            }
            else if(configuration.getKeystoreType().equals("PKCS11")) {
                try {
                    Constructor c = Class.forName("sun.security.pkcs11.SunPKCS11").getConstructor(InputStream.class);
                    String pkcs11Config = "name = SmartCard\nlibrary = "+configuration.getPKCS11Library();
                    ByteArrayInputStream config = new ByteArrayInputStream(pkcs11Config.getBytes());
                    Provider p = (Provider)c.newInstance(config);
                    Security.addProvider(p);
                    ks = KeyStore.getInstance("PKCS11",p);
                    pcb = new PasswordCallback("PKCS11 Password: ",false);
                    callbackHandler.handle(new Callback[]{pcb});
                    ks.load(null,pcb.getPassword());
                }
                catch (Exception e) {
                    ks = null;
                    pcb = null;
                }
            }
            else if(configuration.getKeystoreType().equals("Apple")) {
                ks = KeyStore.getInstance("KeychainStore","Apple");
                ks.load(null,null);
                //pcb = new PasswordCallback("Apple Keychain",false);
                //pcb.setPassword(null);
            }
            else {
                ks = KeyStore.getInstance(configuration.getKeystoreType());
                try {
                    ks.load(new FileInputStream(configuration.getKeystorePath()), pcb.getPassword());
                    pcb = new PasswordCallback("Keystore Password: ",false);
                    callbackHandler.handle(new Callback[]{pcb});
                }
                catch(Exception e) {
                    ks = null;
                    pcb = null;
                }
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            try {
                if(pcb == null) {
                    kmf.init(ks,null);
                } else {
                    kmf.init(ks,pcb.getPassword());
                    pcb.clearPassword();
                }
                kms = kmf.getKeyManagers();
            } catch (NullPointerException npe) {
                kms = null;
            }
        }

        // Verify certificate presented by the server
        context.init(kms,
                new javax.net.ssl.TrustManager[]{new ServerTrustManager(serviceName, configuration)},
                new java.security.SecureRandom());
        Socket plain = socket;
        // Secure the plain connection
        socket = context.getSocketFactory().createSocket(plain,
                plain.getInetAddress().getHostName(), plain.getPort(), true);
        socket.setSoTimeout(0);
        socket.setKeepAlive(true);
        // Initialize the reader and writer with the new secured version
        initReaderAndWriter();
        // Proceed to do the handshake
        ((SSLSocket) socket).startHandshake();
        //if (((SSLSocket) socket).getWantClientAuth()) {
        //    System.err.println("Connection wants client auth");
        //}
        //else if (((SSLSocket) socket).getNeedClientAuth()) {
        //    System.err.println("Connection needs client auth");
        //}
        //else {
        //    System.err.println("Connection does not require client auth");
       // }
        // Set that TLS was successful
        usingTLS = true;

        // Set the new  writer to use
        packetWriter.setWriter(writer);
        // Send a new opening stream to the server
        packetWriter.openStream();
    }

    /**
     * Sets the available stream compression methods offered by the server.
     *
     * @param methods compression methods offered by the server.
     */
    void setAvailableCompressionMethods(Collection methods) {
        compressionMethods = methods;
    }

    /**
     * Returns true if the specified compression method was offered by the server.
     *
     * @param method the method to check.
     * @return true if the specified compression method was offered by the server.
     */
    private boolean hasAvailableCompressionMethod(String method) {
        return compressionMethods != null && compressionMethods.contains(method);
    }

    /**
     * Returns true if network traffic is being compressed. When using stream compression network
     * traffic can be reduced up to 90%. Therefore, stream compression is ideal when using a slow
     * speed network connection. However, the server will need to use more CPU time in order to
     * un/compress network data so under high load the server performance might be affected.<p>
     * <p/>
     * Note: to use stream compression the smackx.jar file has to be present in the classpath.
     *
     * @return true if network traffic is being compressed.
     */
    public boolean isUsingCompression() {
        return usingCompression;
    }

    /**
     * Starts using stream compression that will compress network traffic. Traffic can be
     * reduced up to 90%. Therefore, stream compression is ideal when using a slow speed network
     * connection. However, the server and the client will need to use more CPU time in order to
     * un/compress network data so under high load the server performance might be affected.<p>
     * <p/>
     * Stream compression has to have been previously offered by the server. Currently only the
     * zlib method is supported by the client. Stream compression negotiation has to be done
     * before authentication took place.<p>
     * <p/>
     * Note: to use stream compression the smackx.jar file has to be present in the classpath.
     *
     * @return true if stream compression negotiation was successful.
     */
    private boolean useCompression() {
        // If stream compression was offered by the server and we want to use
        // compression then send compression request to the server
        if (authenticated) {
            throw new IllegalStateException("Compression should be negotiated before authentication.");
        }
        try {
            Class.forName("com.jcraft.jzlib.ZOutputStream");
        }
        catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot use compression. Add smackx.jar to the classpath");
        }
        if (hasAvailableCompressionMethod("zlib")) {
            requestStreamCompression();
            // Wait until compression is being used or a timeout happened
            synchronized (this) {
                try {
                    this.wait(SmackConfiguration.getPacketReplyTimeout() * 5);
                }
                catch (InterruptedException e) {
                    // Ignore.
                }
            }
            return usingCompression;
        }
        return false;
    }

    /**
     * Request the server that we want to start using stream compression. When using TLS
     * then negotiation of stream compression can only happen after TLS was negotiated. If TLS
     * compression is being used the stream compression should not be used.
     */
    private void requestStreamCompression() {
        try {
            writer.write("<compress xmlns='http://jabber.org/protocol/compress'>");
            writer.write("<method>zlib</method></compress>");
            writer.flush();
        }
        catch (IOException e) {
            packetReader.notifyConnectionError(e);
        }
    }

    /**
     * Start using stream compression since the server has acknowledged stream compression.
     *
     * @throws Exception if there is an exception starting stream compression.
     */
    void startStreamCompression() throws Exception {
        // Secure the plain connection
        usingCompression = true;
        // Initialize the reader and writer with the new secured version
        initReaderAndWriter();

        // Set the new  writer to use
        packetWriter.setWriter(writer);
        // Send a new opening stream to the server
        packetWriter.openStream();
        // Notify that compression is being used
        synchronized (this) {
            this.notify();
        }
    }

    /**
     * Notifies the XMPP connection that stream compression was denied so that
     * the connection process can proceed.
     */
    void streamCompressionDenied() {
        synchronized (this) {
            this.notify();
        }
    }

    /**
     * Establishes a connection to the XMPP server and performs an automatic login
     * only if the previous connection state was logged (authenticated). It basically
     * creates and maintains a socket connection to the server.<p>
     * <p/>
     * Listeners will be preserved from a previous connection if the reconnection
     * occurs after an abrupt termination.
     *
     * @throws XMPPException if an error occurs while trying to establish the connection.
     *      Two possible errors can occur which will be wrapped by an XMPPException --
     *      UnknownHostException (XMPP error code 504), and IOException (XMPP error code
     *      502). The error codes and wrapped exceptions can be used to present more
     *      appropiate error messages to end-users.
     */
    public void connect() throws XMPPException {
        // Stablishes the connection, readers and writers
        connectUsingConfiguration(configuration);
        // Automatically makes the login if the user was previouslly connected successfully
        // to the server and the connection was terminated abruptly
        if (connected && wasAuthenticated) {
            // Make the login
            try {
                if (isAnonymous()) {
                    // Make the anonymous login
                    loginAnonymously();
                }
                else {
                    login(getConfiguration().getUsername(), getConfiguration().getPassword(),
                            getConfiguration().getResource());
                }
            }
            catch (XMPPException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Sets whether the connection has already logged in the server.
     *
     * @param wasAuthenticated true if the connection has already been authenticated.
     */
    private void setWasAuthenticated(boolean wasAuthenticated) {
        if (!this.wasAuthenticated) {
            this.wasAuthenticated = wasAuthenticated;
        }
    }
}
