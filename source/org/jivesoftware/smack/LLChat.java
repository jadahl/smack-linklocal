package org.jivesoftware.smack;

import org.jivesoftware.smack.packet.Message;

import java.util.Set;
import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Keeps track of a chat session between two link-local clients.
 */
public class LLChat {
    private String serviceName;
    private LLService service;

    private Set<LLMessageListener> listeners = new CopyOnWriteArraySet<LLMessageListener>();

    // Queue for storing messages in case no listener is available when a
    // message is to be delivered.
    private List<Message> messageQueue = new LinkedList<Message>();


    LLChat(LLService service, LLPresence presence) throws XMPPException {
        this.service = service;;
        serviceName = presence.getServiceName();
    }

    /**
     * Get the service name of the remote client of this chat session.
     * 
     * @return the service name of the remote client of this chat session
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Deliver a message to the message listeners.
     *
     * @param message the message to be delivered.
     */
    void deliver(Message message) {
        // if no listeners are available, queue the messages for later.
        synchronized (listeners) {
            if (listeners.isEmpty())
                messageQueue.add(message);
            else {
                for (LLMessageListener listener : listeners) {
                    listener.processMessage(this, message);
                }
            }
        }
    }

    /**
     * Send a message packet to the remote client.
     *
     * @param message the message to be sent.
     * @throws XMPPException if an exception occurs during transmission.
     */
    public void sendMessage(Message message) throws XMPPException {
        message.setTo(serviceName);
        message.setType(Message.Type.chat);
        service.sendMessage(message);
    }

    /**
     * Send a message to the remote client.
     *
     * @param message the message to be sent.
     * @throws XMPPException if an exception occurs during transmission.
     */
    public void sendMessage(String text) throws XMPPException {
        Message message = new Message(serviceName, Message.Type.chat);
        message.setBody(text);

        service.sendMessage(message);
    }

    /**
     * Add a message listener. The message listener will be notified when new
     * messages are received. If there was no listener when messages was to be
     * delivered, the first listener to be added will receive all of the queued
     * messages.
     */
    public void addMessageListener(LLMessageListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
            for (Message message : messageQueue)
                listener.processMessage(this, message);
            messageQueue.clear();
        }
    }
}
