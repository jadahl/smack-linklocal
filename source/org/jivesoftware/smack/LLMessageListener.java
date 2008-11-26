package org.jivesoftware.smack;

import org.jivesoftware.smack.packet.Message;

/**
 * Notification when messages are being delivered to a chat.
 */
public interface LLMessageListener {
    /**
     * New message in chat.
     *
     * @param chat the chat session which the message was delivered to.
     * @param message the message being delivered.
     */
    void processMessage(LLChat chat, Message message);
}
