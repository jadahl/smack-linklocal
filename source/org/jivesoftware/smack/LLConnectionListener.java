package org.jivesoftware.smack;

/**
 * Notification about when new Link-local connections has been established.
 */
public interface LLConnectionListener {

    /**
     * A new link-local connection has been established.
     *
     * @param connection the new established connection.
     */
    public void connectionCreated(XMPPLLConnection connection);
}
