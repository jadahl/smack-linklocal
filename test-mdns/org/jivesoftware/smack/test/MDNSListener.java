package org.jivesoftware.smack.test;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;

public class MDNSListener implements LLPresenceListener {

    public void presenceNew(LLPresence pr) {
        try {
            System.out.println("New presence: " + pr.getServiceName() + 
                    " (" + pr.getStatus() + ")");
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }

    }

    public void presenceRemove(LLPresence pr) {
        System.out.println("Removed presence: " + pr.getServiceName());
    }


    public void connectionCreated(AbstractConnection connection) {
        System.err.println("Notification about a connection that was created...");
    }
}
