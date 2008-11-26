package org.jivesoftware.smack;

/**
 * Notification about new Link-local chat sessions.
 */
public interface LLChatListener {

    /**
     * New chat has been created.
     *
     * @param chat the newly created chat.
     */
    public void newChat(LLChat chat);
}
