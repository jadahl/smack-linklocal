package org.jivesoftware.smack;

import org.jivesoftware.smack.util.Tuple;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javax.jmdns.impl.JmDNSImpl;
import javax.jmdns.impl.DNSCache;
import javax.jmdns.impl.DNSEntry;
//import javax.jmdns.impl.DNSRecord;
//import javax.jmdns.impl.DNSListener;

import java.util.LinkedList;
import java.net.InetAddress;
import java.io.IOException;
import java.util.Hashtable;

/**
 * Implements a LLService using JmDNS.
 *
 * @author Jonas Ådahl
 */
public class JmDNSService extends LLService {
    static JmDNS jmdns = null;
    static JmDNSPresenceDiscoverer presenceDiscoverer = null;
    private ServiceInfo serviceInfo;
    static final String SERVICE_TYPE = "_presence._tcp.local.";

    private JmDNSService(LLPresence presence, LLPresenceDiscoverer presenceDiscoverer) {
        super(presence, presenceDiscoverer);
    }

    /**
     * Instantiate a new JmDNSService and start to listen for connections.
     *
     * @param presence the mDNS presence information that should be used.
     */
    public static LLService create(LLPresence presence) throws XMPPException {
        return create(presence, null);
    }

    /**
     * Instantiate a new JmDNSService and start to listen for connections.
     *
     * @param presence the mDNS presence information that should be used.
     * @param addr the INET Address to use.
     */
    public static LLService create(LLPresence presence, InetAddress addr) throws XMPPException {
        // Start the JmDNS daemon.
        initJmDNS(addr);

        // Start the presence discoverer
        JmDNSPresenceDiscoverer presenceDiscoverer = new JmDNSPresenceDiscoverer();

        // Start the presence service
        JmDNSService service = new JmDNSService(presence, presenceDiscoverer);

        return service;
    }

    @Override
    public void close() {
        super.close();
        jmdns.close();
    }

    /**
     * Start the JmDNS daemon.
     */
    private static void initJmDNS(InetAddress addr) throws XMPPException {
        try {
            if (jmdns == null) {
                if (addr == null) {
                    jmdns = JmDNS.create();
                }
                else {
                    jmdns = JmDNS.create(addr);
                }
            }
        }
        catch (IOException ioe) {
            throw new XMPPException(ioe);
        }
    }

    protected void updateText() {
        Hashtable<String,String> ht = new Hashtable<String,String>();
        
        for (Tuple<String,String> t : presence.toList()) {
            if (t.a != null && t.b != null) {
                ht.put(t.a, t.b);
            }
        }

        serviceInfo.setText(ht);
    }

    /**
     * Register the DNS-SD service with the daemon.
     */
    protected void registerService() throws XMPPException {
        Hashtable<String,String> ht = new Hashtable<String,String>();
        
        for (Tuple<String,String> t : presence.toList()) {
            if (t.a != null && t.b != null)
                ht.put(t.a, t.b);
        }
        serviceInfo = ServiceInfo.create(SERVICE_TYPE,
                presence.getServiceName(), presence.getPort(), 0, 0, ht);
        try {
            String originalName = serviceInfo.getQualifiedName();
            jmdns.registerService(serviceInfo);
            presence.setServiceName(serviceInfo.getName());

            if (!originalName.equals(serviceInfo.getQualifiedName())) {
                // Update presence service name
                // Name collision occured, lets remove confusing elements
                // from cache in case something goes wrong
                JmDNSImpl jmdnsimpl = (JmDNSImpl) jmdns;
                DNSCache.CacheNode n = jmdnsimpl.getCache().find(originalName);

                LinkedList<DNSEntry> toRemove = new LinkedList<DNSEntry>();
                while (n != null) {
                    DNSEntry e = n.getValue();
                    if (e != null)
                        toRemove.add(e);

                    n = n.next();
                }

                // Remove the DNSEntry's one by one
                for (DNSEntry e : toRemove) {
                    jmdnsimpl.getCache().remove(e);
                }
            }
        }
        catch (IOException ioe) {
            throw new XMPPException(ioe);
        }
    }

    /**
     * Reregister the DNS-SD service with the daemon.
     */
    protected void reannounceService() throws XMPPException {
        try {
            jmdns.reannounceService(serviceInfo);
        }
        catch (IOException ioe) {
            throw new XMPPException("Exception occured when reannouncing mDNS presence.", ioe);
        }
    }

    /**
     * Unregister the DNS-SD service, making the client unavailable.
     */
    public void makeUnavailable() {
        jmdns.unregisterService(serviceInfo);
        serviceInfo = null;
    }
}
