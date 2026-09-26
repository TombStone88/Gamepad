package com.tombstone.wirelessgamepad;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Listens on a fixed port for "VIBRATE,<largeMotor>,<smallMotor>" UDP messages
 * sent from the PC whenever a game triggers rumble on the virtual controller.
 */
public class RumbleListener {

    public static final int PORT = 5001;

    public interface Callback {
        void onRumble(int largeMotor, int smallMotor);
    }

    private volatile boolean running = false;
    private DatagramSocket socket;

    public void start(Callback callback) {
        if (running) return;
        running = true;
        new Thread(() -> {
            try {
                socket = new DatagramSocket(PORT);
                byte[] buf = new byte[64];
                while (running) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    String[] parts = msg.split(",");
                    if (parts.length == 3 && parts[0].equals("VIBRATE") && callback != null) {
                        int large = Integer.parseInt(parts[1]);
                        int small = Integer.parseInt(parts[2]);
                        callback.onRumble(large, small);
                    }
                }
            } catch (Exception e) {
                // Socket closed on stop(), or a malformed packet — safe to ignore
            }
        }).start();
    }

    public void stop() {
        running = false;
        if (socket != null) socket.close();
    }
}
