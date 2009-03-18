package org.jivesoftware.smack;

import org.jivesoftware.smack.util.Tuple;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.LinkedList;


/**
 * An implementation of LLPresenceDiscoverer using JmDNS.
 *
 * @author Jonas Ådahl
 */
class JmDNSPresenceDiscoverer extends LLPresenceDiscoverer {
    protected static final int SERVICE_REQUEST_TIMEOUT = 10000; 
    protected static JmDNS jmdns;

    JmDNSPresenceDiscoverer() throws XMPPException {
        jmdns = JmDNSService.jmdns;
        if (jmdns == null)
            throw new XMPPException("Failed to fully initiate mDNS daemon.");

        jmdns.addServiceListener(JmDNSService.SERVICE_TYPE, new PresenceServiceListener());
    }

    /**
     * Convert raw TXT fields to a list of strings.
     * The raw TXT fields are encoded as follows:
     * <ul>
     *  <li>Byte 0 specifies the length of the first field (which starts at byte 1).</li>
     *  <li>If the last byte of the previous field is the last byte of the array,
     *  all TXT fields has been read.</li>
     *  <li>If there are more bytes following, the next byte after the last of the
     *  previous field specifies the length of the next field.</li>
     * </ul>
     *
     * @param bytes raw TXT fields as an array of bytes.
     * @return TXT fields as a list of strings.
     */
    private static List<String> TXTToList(byte[] bytes) {
        List<String> list = new LinkedList<String>();
        try {
            int size_i = 0;
            while (size_i < bytes.length) {
                int s = (int)(bytes[size_i]);
                list.add(new String(bytes, ++size_i, s, "UTF-8"));
                size_i += s;
            }
        } catch (UnsupportedEncodingException uee) {
            // XXX Without UTF-8 we are totally screwed;
        }
        return list;
    }

    /**
     * Convert a TXT field list bundled with a '_presence._tcp' service to a
     * String,String tuple. The TXT field list looks as following:
     * "key=value" which is converted into the tuple (key, value).
     *
     * @param strings the TXT fields.
     * @return a list of key,value tuples.
     */
    private static List<Tuple<String,String>> TXTListToXMPPRecords(List<String> strings) {
        // records :: [(String, String)]
        List<Tuple<String,String>> records = new LinkedList<Tuple<String,String>>();
        for (String s : strings) {
            String[] record = s.split("=", 2);
            // check if valid
            if (record.length == 2)
                records.add(new Tuple<String, String>(record[0], record[1]));
        }
        return records;
    }

    /**
     * Implementation of a JmDNS ServiceListener. Listens to service resolved and
     * service information resolved events.
     */
    private class PresenceServiceListener implements ServiceListener {
        public void serviceAdded(ServiceEvent event) {
            // XXX
            // To reduce network usage, we should only request information
            // when needed.
            new RequestInfoThread(event).start();
        }
        public void serviceRemoved(ServiceEvent event) {
            presenceRemoved(event.getName());
        }
        public void serviceResolved(ServiceEvent event) {
            presenceInfoAdded(event.getName(),
                    new LLPresence(event.getName(),
                        event.getInfo().getHostAddress(), event.getInfo().getPort(),
                        TXTListToXMPPRecords(TXTToList(event.getInfo().getTextBytes()))));
        }

        private class RequestInfoThread extends Thread {
            ServiceEvent event;

            RequestInfoThread(ServiceEvent event) {
                this.event = event;
            }

            public void run() {
                jmdns.requestServiceInfo(event.getType(), event.getName(),
                        true, SERVICE_REQUEST_TIMEOUT);
            }
        }
    }
}
