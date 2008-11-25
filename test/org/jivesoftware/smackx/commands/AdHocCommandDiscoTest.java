/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
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

package org.jivesoftware.smackx.commands;

import org.jivesoftware.smack.test.SmackTestCase;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.packet.DiscoverItems;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;

/**
 * AdHocCommand tests.
 *
 * @author Matt Tucker
 */
public class AdHocCommandDiscoTest extends SmackTestCase {

    /**
     * Constructor for test.
     * @param arg0 argument.
     */
    public AdHocCommandDiscoTest(String arg0) {
        super(arg0);
    }

    public void testAdHocCommands() {
        try {
            AdHocCommandManager manager1 = AdHocCommandManager.getAddHocCommandsManager(getConnection(0));
            manager1.registerCommand("test", "test node", LocalCommand.class);

            manager1.registerCommand("test2", "test node", new LocalCommandFactory() {
                public LocalCommand getInstance() throws InstantiationException, IllegalAccessException {
                    return new LocalCommand() {
                        public boolean isLastStage() {
                            return true;
                        }

                        public boolean hasPermission(String jid) {
                            return true;
                        }

                        public void execute() throws XMPPException {
                            Form result = new Form(Form.TYPE_RESULT);
                            FormField resultField = new FormField("test2");
                            resultField.setLabel("test node");
                            resultField.addValue("it worked");
                            result.addField(resultField);
                            setForm(result);
                        }

                        public void next(Form response) throws XMPPException {
                            //
                        }

                        public void complete(Form response) throws XMPPException {
                            //
                        }

                        public void prev() throws XMPPException {
                            //
                        }

                        public void cancel() throws XMPPException {
                            //
                        }
                    };
                }
            });
            
            AdHocCommandManager manager2 = AdHocCommandManager.getAddHocCommandsManager(getConnection(1));
            DiscoverItems items = manager2.discoverCommands(getFullJID(0));

            assertTrue("Disco for command test failed", items.getItems().next().getNode().equals("test"));

            RemoteCommand command = manager2.getRemoteCommand(getFullJID(0), "test2");
            command.execute();
            assertEquals("Disco for command test failed", command.getForm().getField("test2").getValues().next(), "it worked");
        }
        catch (Exception e) {
            fail(e.getMessage());
        }
    }

    protected void setUp() throws Exception {
        super.setUp();

    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    protected int getMaxConnections() {
        return 2;
    }
}