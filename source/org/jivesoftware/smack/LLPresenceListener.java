package org.jivesoftware.smack;

/**
 * Interface for receiving notifications about presence changes.
 */
public interface LLPresenceListener {
    /**
     * New link-local presence has been discovered.
     * 
     * @param presence information about the new presence
     */

    public void presenceNew(LLPresence presence);
    /**
     * A link-local presence has gone offline.
     * @param presence the presence which went offline.
     */
    public void presenceRemove(LLPresence presence);
}
