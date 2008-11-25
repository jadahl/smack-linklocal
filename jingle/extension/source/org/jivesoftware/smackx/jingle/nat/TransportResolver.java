/**
 * $RCSfile: TransportResolver.java,v $
 * $Revision: 1.1 $
 * $Date: 2007/07/02 17:41:07 $
 *
 * Copyright (C) 2002-2006 Jive Software. All rights reserved.
 * ====================================================================
 * The Jive Software License (based on Apache Software License, Version 1.1)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by
 *        Jive Software (http://www.jivesoftware.com)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Smack" and "Jive Software" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please
 *    contact webmaster@jivesoftware.com.
 *
 * 5. Products derived from this software may not be called "Smack",
 *    nor may "Smack" appear in their name, without prior written
 *    permission of Jive Software.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL JIVE SOFTWARE OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 */

package org.jivesoftware.smackx.jingle.nat;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.jingle.JingleSession;
import org.jivesoftware.smackx.jingle.SmackLogger;

/**
 * A TransportResolver is used for obtaining a list of valid transport
 * candidates. A transport candidate is composed by an IP address and a port number.
 * It is called candidate, because it can be elected or not.
 *
 * @author Thiago Camargo
 * @author Alvaro Saurin <alvaro.saurin@gmail.com>
 */
public abstract class TransportResolver {

	private static final SmackLogger LOGGER = SmackLogger.getLogger(TransportResolver.class);
	
	public enum Type {

        rawupd, ice
    }

    ;

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Type type = Type.rawupd;

    // the time, in milliseconds, before a check aborts
    public static final int CHECK_TIMEOUT = 3000;

    // Listeners for events
    private final ArrayList<TransportResolverListener> listeners = new ArrayList<TransportResolverListener>();

    // TRue if the resolver is working
    private boolean resolving;

    // This will be true when all the transport candidates have been gathered...
    private boolean resolved;

    // This indicates that a transport resolver is initialized
    private boolean initialized = false;

    // We store a list of candidates internally, just in case there are several
    // possibilities. When the user asks for a transport, we return the best
    // one.
    protected final List<TransportCandidate> candidates = new ArrayList<TransportCandidate>();

    /**
     * Default constructor.
     */
    protected TransportResolver() {
        super();

        resolving = false;
        resolved = false;
    }

    /**
     * Initialize the Resolver
     */
    public abstract void initialize() throws XMPPException;

    /**
     * Start a the resolution.
     */
    public abstract void resolve(JingleSession session) throws XMPPException;

    /**
     * Clear the list of candidates and start a new resolution process.
     *
     * @throws XMPPException
     */
    public void clear() throws XMPPException {
        cancel();
        candidates.clear();
    }

    /**
     * Cancel any asynchronous resolution operation.
     */
    public abstract void cancel() throws XMPPException;

    /**
     * Return true if the resolver is working.
     *
     * @return true if the resolver is working.
     */
    public boolean isResolving() {
        return resolving;
    }

    /**
     * Return true if the resolver has finished the search for transport
     * candidates.
     *
     * @return true if the search has finished
     */
    public boolean isResolved() {
        return resolved;
    }

    /**
     * Set the Transport Resolver as initialized.
     */
    public synchronized void setInitialized() {
        initialized = true;
    }

    /**
     * Chack if the Transport Resolver is initialized
     *
     * @return
     */
    public synchronized boolean isInitialized() {
        return initialized;
    }

    /**
     * Indicate the beggining of the resolution process. This method must be
     * used by subclasses at the begining of their resolve() method.
     */
    protected synchronized void setResolveInit() {
        resolved = false;
        resolving = true;

        triggerResolveInit();
    }

    /**
     * Indicate the end of the resolution process. This method must be used by
     * subclasses at the begining of their resolve() method.
     */
    protected synchronized void setResolveEnd() {
        resolved = true;
        resolving = false;

        triggerResolveEnd();
    }

    // Listeners management

    /**
     * Add a transport resolver listener.
     *
     * @param li The transport resolver listener to be added.
     */
    public void addListener(TransportResolverListener li) {
        synchronized (listeners) {
            listeners.add(li);
        }
    }

    /**
     * Removes a transport resolver listener.
     *
     * @param li The transport resolver listener to be removed
     */
    public void removeListener(TransportResolverListener li) {
        synchronized (listeners) {
            listeners.remove(li);
        }
    }

    /**
     * Get the list of listeners
     *
     * @return the list of listeners
     */
    public ArrayList getListenersList() {
        synchronized (listeners) {
            return new ArrayList(listeners);
        }
    }

    /**
     * Trigger a new candidate added event.
     *
     * @param cand The candidate added to the list of candidates.
     */
    protected void triggerCandidateAdded(TransportCandidate cand) {
        Iterator iter = getListenersList().iterator();
        while (iter.hasNext()) {
            TransportResolverListener trl = (TransportResolverListener) iter.next();
            if (trl instanceof TransportResolverListener.Resolver) {
                TransportResolverListener.Resolver li = (TransportResolverListener.Resolver) trl;
                LOGGER.debug("triggerCandidateAdded : " + cand.getLocalIp());
                li.candidateAdded(cand);
            }
        }
    }

    /**
     * Trigger a event notifying the initialization of the resolution process.
     */
    private void triggerResolveInit() {
        Iterator iter = getListenersList().iterator();
        while (iter.hasNext()) {
            TransportResolverListener trl = (TransportResolverListener) iter.next();
            if (trl instanceof TransportResolverListener.Resolver) {
                TransportResolverListener.Resolver li = (TransportResolverListener.Resolver) trl;
                li.init();
            }
        }
    }

    /**
     * Trigger a event notifying the obtention of all the candidates.
     */
    private void triggerResolveEnd() {
        Iterator iter = getListenersList().iterator();
        while (iter.hasNext()) {
            TransportResolverListener trl = (TransportResolverListener) iter.next();
            if (trl instanceof TransportResolverListener.Resolver) {
                TransportResolverListener.Resolver li = (TransportResolverListener.Resolver) trl;
                li.end();
            }
        }
    }

    // Candidates management

    /**
     * Clear the list of candidate
     */
    protected void clearCandidates() {
        synchronized (candidates) {
            candidates.clear();
        }
    }

    /**
     * Add a new transport candidate
     *
     * @param cand The candidate to add
     */
    protected void addCandidate(TransportCandidate cand) {
        synchronized (candidates) {
            if (!candidates.contains(cand))
                candidates.add(cand);
        }

        // Notify the listeners
        triggerCandidateAdded(cand);
    }

    /**
     * Get an iterator for the list of candidates
     *
     * @return an iterator
     */
    public Iterator getCandidates() {
        synchronized (candidates) {
            return Collections.unmodifiableList(new ArrayList(candidates)).iterator();
        }
    }

    /**
     * Get the candididate with the highest preference.
     *
     * @return The best candidate, according to the preference order.
     */
    public TransportCandidate getPreferredCandidate() {
        TransportCandidate result = null;

        ArrayList cands = (ArrayList) getCandidatesList();
        if (cands.size() > 0) {
            Collections.sort(cands);
            // Return the last candidate
            result = (TransportCandidate) cands.get(cands.size() - 1);
            LOGGER.debug("Result: " + result.getIp());
        }

        return result;
    }

    /**
     * Get the numer of transport candidates.
     *
     * @return The length of the transport candidates list.
     */
    public int getCandidateCount() {
        synchronized (candidates) {
            return candidates.size();
        }
    }

    /**
     * Get the list of candidates
     *
     * @return the list of transport candidates
     */
    public List<TransportCandidate> getCandidatesList() {
        List<TransportCandidate> result = null;

        synchronized (candidates) {
            result = new ArrayList<TransportCandidate>(candidates);
        }

        return result;
    }

    /**
     * Get the n-th candidate
     *
     * @return a transport candidate
     */
    public TransportCandidate getCandidate(int i) {
        TransportCandidate cand;

        synchronized (candidates) {
            cand = (TransportCandidate) candidates.get(i);
        }
        return cand;
    }

    /**
     * Initialize Transport Resolver and wait until it is complete unitialized.
     */
    public void initializeAndWait() throws XMPPException {
        this.initialize();
        try {
            LOGGER.debug("Initializing transport resolver...");
            while (!this.isInitialized()) {
                LOGGER.debug("Resolver init still pending");
                Thread.sleep(1000);
            }
            LOGGER.debug("Transport resolved\n");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Obtain a free port we can use.
     *
     * @return A free port number.
     */
    protected int getFreePort() {
        ServerSocket ss;
        int freePort = 0;

        for (int i = 0; i < 10; i++) {
            freePort = (int) (10000 + Math.round(Math.random() * 10000));
            freePort = freePort % 2 == 0 ? freePort : freePort + 1;
            try {
                ss = new ServerSocket(freePort);
                freePort = ss.getLocalPort();
                ss.close();
                return freePort;
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            ss = new ServerSocket(0);
            freePort = ss.getLocalPort();
            ss.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return freePort;
    }
}
