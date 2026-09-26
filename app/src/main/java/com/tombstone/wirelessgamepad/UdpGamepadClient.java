package com.tombstone.wirelessgamepad;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpGamepadClient {

    public static final int PORT = 5000;

    private DatagramSocket socket;
    private InetAddress address;
    private volatile boolean isRunning = false;
    private final GamepadState state;

    public UdpGamepadClient(GamepadState state) {
        this.state = state;
    }

    public void start(String pcIp) {
        if (isRunning) return;
        isRunning = true;
        new Thread(() -> {
            try {
                socket = new DatagramSocket();
                address = InetAddress.getByName(pcIp);
                DatagramPacket packet = new DatagramPacket(new byte[0], 0, address, PORT);

                while (isRunning) {
                    byte[] buf = state.toPacket().getBytes();
                    packet.setData(buf);
                    packet.setLength(buf.length);
                    socket.send(packet);
                    Thread.sleep(16); // ~60Hz
                }
            } catch (Exception e) {
                e.printStackTrace();
                isRunning = false;
            }
        }).start();
    }

    public void stop() {
        isRunning = false;
        if (socket != null) socket.close();
    }

    public boolean isRunning() {
        return isRunning;
    }
}
