package org.jivesoftware.smack;

/**
 * Notification for new Link-local services created.
 */
public interface LLServiceListener {

    /**
     * The function called when a new Link-local service is created.
     *
     * @param service the new service
     */
    public void serviceCreated(LLService service);
}
