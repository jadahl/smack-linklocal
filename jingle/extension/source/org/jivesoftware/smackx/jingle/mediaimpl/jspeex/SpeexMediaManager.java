/**
 * $RCSfile: SpeexMediaManager.java,v $
 * $Revision: 1.3 $
 * $Date: 25/12/2006
 * <p/>
 * Copyright 2003-2006 Jive Software.
 * <p/>
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.smackx.jingle.mediaimpl.jspeex;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jivesoftware.smackx.jingle.JingleSession;
import org.jivesoftware.smackx.jingle.SmackLogger;
import org.jivesoftware.smackx.jingle.media.JingleMediaManager;
import org.jivesoftware.smackx.jingle.media.JingleMediaSession;
import org.jivesoftware.smackx.jingle.media.PayloadType;
import org.jivesoftware.smackx.jingle.mediaimpl.JMFInit;
import org.jivesoftware.smackx.jingle.nat.JingleTransportManager;
import org.jivesoftware.smackx.jingle.nat.TransportCandidate;

/**
 * Implements a jingleMediaManager using JMF based API and JSpeex.
 * It supports Speex codec.
 * <i>This API only currently works on windows.</i>
 *
 * @author Thiago Camargo
 */
public class SpeexMediaManager extends JingleMediaManager {

	private static final SmackLogger LOGGER = SmackLogger.getLogger(SpeexMediaManager.class);

	public static final String MEDIA_NAME = "Speex";

    private List<PayloadType> payloads = new ArrayList<PayloadType>();

    public SpeexMediaManager(JingleTransportManager transportManager) {
        super(transportManager);
        setupPayloads();
        setupJMF();
    }

    /**
     * Returns a new jingleMediaSession
     *
     * @param payloadType payloadType
     * @param remote      remote Candidate
     * @param local       local Candidate
     * @return JingleMediaSession
     */
    public JingleMediaSession createMediaSession(PayloadType payloadType, final TransportCandidate remote, final TransportCandidate local, final JingleSession jingleSession) {
        return new AudioMediaSession(payloadType, remote, local, null,null);
    }

    /**
     * Setup API supported Payloads
     */
    private void setupPayloads() {
        payloads.add(new PayloadType.Audio(15, "speex"));
    }

    /**
     * Return all supported Payloads for this Manager
     *
     * @return The Payload List
     */
    public List<PayloadType> getPayloads() {
        return payloads;
    }

    /**
     * Runs JMFInit the first time the application is started so that capture
     * devices are properly detected and initialized by JMF.
     */
    public static void setupJMF() {
        // .jmf is the place where we store the jmf.properties file used
        // by JMF. if the directory does not exist or it does not contain
        // a jmf.properties file. or if the jmf.properties file has 0 length
        // then this is the first time we're running and should continue to
        // with JMFInit
        String homeDir = System.getProperty("user.home");
        File jmfDir = new File(homeDir, ".jmf");
        String classpath = System.getProperty("java.class.path");
        classpath += System.getProperty("path.separator")
                + jmfDir.getAbsolutePath();
        System.setProperty("java.class.path", classpath);

        if (!jmfDir.exists())
            jmfDir.mkdir();

        File jmfProperties = new File(jmfDir, "jmf.properties");

        if (!jmfProperties.exists()) {
            try {
                jmfProperties.createNewFile();
            }
            catch (IOException ex) {
                LOGGER.debug("Failed to create jmf.properties");
                ex.printStackTrace();
            }
        }

        // if we're running on linux checkout that libjmutil.so is where it
        // should be and put it there.
        runLinuxPreInstall();

        if (jmfProperties.length() == 0) {
            new JMFInit(null, false);
        }

    }

    private static void runLinuxPreInstall() {
        // @TODO Implement Linux Pre-Install
    }
    
    public String getName() {
        return MEDIA_NAME;
    }
}
