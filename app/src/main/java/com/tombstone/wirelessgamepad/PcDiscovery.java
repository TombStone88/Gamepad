package com.tombstone.wirelessgamepad;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * Broadcasts a discovery request on the local network and collects replies
 * from any PC running gamepad_receiver.py, so the user doesn't have to type
 * an IP address manually.
 */
public class PcDiscovery {

    public static final int DISCOVERY_PORT = 5003;
    private static final String REQUEST = "GAMEPAD_DISCOVER";
    private static final String REPLY_PREFIX = "GAMEPAD_HERE:";

    public static class FoundPc {
        public final String ip;
        public final String name;
        public FoundPc(String ip, String name) {
            this.ip = ip;
            this.name = name;
        }
    }

    public interface Callback {
        void onResult(List<FoundPc> found);
    }

    public static void search(int timeoutMs, Callback callback) {
        new Thread(() -> {
            List<FoundPc> results = new ArrayList<>();
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                socket.setSoTimeout(timeoutMs);

                byte[] req = REQUEST.getBytes();
                InetAddress broadcast = InetAddress.getByName("255.255.255.255");
                socket.send(new DatagramPacket(req, req.length, broadcast, DISCOVERY_PORT));

                byte[] buf = new byte[128];
                while (true) {
                    DatagramPacket resp = new DatagramPacket(buf, buf.length);
                    socket.receive(resp); // throws SocketTimeoutException once nothing new arrives
                    String msg = new String(resp.getData(), 0, resp.getLength());
                    if (msg.startsWith(REPLY_PREFIX)) {
                        String name = msg.substring(REPLY_PREFIX.length());
                        String ip = resp.getAddress().getHostAddress();
                        boolean already = false;
                        for (FoundPc f : results) if (f.ip.equals(ip)) already = true;
                        if (!already) results.add(new FoundPc(ip, name));
                    }
                }
            } catch (SocketTimeoutException timeout) {
                // Normal — discovery window closed, return whatever we collected
            } catch (Exception e) {
                // Network error — return whatever we found (possibly nothing)
            }
            callback.onResult(results);
        }).start();
    }
}
