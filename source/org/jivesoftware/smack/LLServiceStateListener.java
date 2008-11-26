package org.jivesoftware.smack;

import org.jivesoftware.smack.packet.Message;

/**
 * Interface for handeling link-local service events such as 
 * service closing, service crashes and other events.
 */
public interface LLServiceStateListener {

    /**
     * Notification that the connection was closed normally.
     */
    public void serviceClosed();

    /**
     * Notification that the connection was closed due to an exception.
     *
     * @param e the exception.
     */
    public void serviceClosedOnError(Exception e);

    /**
     * Notification that a message with unknown presence was received.
     * This could be someone being invisible, meaning no presece is
     * announced.
     *
     * @param e the exception.
     */
    public void unknownOriginMessage(Message e);
}
