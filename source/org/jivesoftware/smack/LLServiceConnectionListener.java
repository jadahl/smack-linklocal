package org.jivesoftware.smack;

/**
 * Notification about when new Link-local connections associated with a
 * specific Link-local service has been established.
 */
public interface LLServiceConnectionListener {

    /**
     * A new link-local connection has been established.
     *
     * @param connection the new established connection.
     */
    public void connectionCreated(XMPPLLConnection connection);
}
