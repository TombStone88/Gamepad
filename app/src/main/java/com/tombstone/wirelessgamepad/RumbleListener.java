package com.tombstone.wirelessgamepad;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * Listens on UDP port {@value PORT} for rumble commands sent by the PC.
 *
 * <p>Expected packet format: {@code VIBRATE,<largeMotor>,<smallMotor>}<br>
 * where largeMotor and smallMotor are integers in the range [0, 255].</p>
 *
 * <p>Malformed packets are silently discarded — the listener thread continues
 * running so that subsequent valid packets are still received.</p>
 *
 * <p>Call {@link #start(Callback)} once after the gamepad connection is confirmed.
 * Call {@link #stop()} to release the socket and stop the thread.</p>
 */
public class RumbleListener {

    private static final String TAG = "RumbleListener";

    public static final int PORT = 5001;

    public interface Callback {
        void onRumble(int largeMotor, int smallMotor);
    }

    private volatile boolean running = false;

    /** Volatile so that stop() can safely close the socket from the UI thread. */
    private volatile DatagramSocket socket;

    /**
     * Starts listening for rumble packets. If already running, this call is a no-op.
     *
     * @param callback called on the listener thread whenever a valid rumble packet arrives
     */
    public void start(Callback callback) {

        if (running) {
            return;
        }

        running = true;

        Thread thread = new Thread(() -> {

            try {
                socket = new DatagramSocket(PORT);
                byte[] buf = new byte[64];

                while (running) {

                    DatagramPacket packet = new DatagramPacket(buf, buf.length);

                    try {
                        socket.receive(packet);
                    } catch (SocketException e) {
                        // Socket was closed by stop() — this is the normal shutdown path.
                        break;
                    }

                    String msg = new String(packet.getData(), 0, packet.getLength()).trim();
                    String[] parts = msg.split(",");

                    if (parts.length != 3 || !parts[0].equals("VIBRATE")) {
                        // Malformed packet — discard and keep listening.
                        Log.d(TAG, "Ignored malformed rumble packet: " + msg);
                        continue;
                    }

                    int large;
                    int small;

                    try {
                        large = Integer.parseInt(parts[1].trim());
                        small = Integer.parseInt(parts[2].trim());
                    } catch (NumberFormatException e) {
                        // Values are not integers — discard this packet and keep listening.
                        Log.d(TAG, "Ignored rumble packet with non-integer values: " + msg);
                        continue;
                    }

                    // Clamp to valid range so the vibrator API never receives bad values.
                    large = Math.max(0, Math.min(255, large));
                    small = Math.max(0, Math.min(255, small));

                    if (callback != null) {
                        callback.onRumble(large, small);
                    }
                }

            } catch (Exception e) {
                // Unexpected error opening or using the socket.
                Log.e(TAG, "Rumble listener error: " + e.getMessage());
            } finally {
                closeSocket();
                running = false;
            }

        }, "Gamepad-Rumble");

        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Stops the listener. Safe to call from any thread, including the UI thread.
     * Closing the socket unblocks any in-progress {@code receive()} call.
     */
    public void stop() {
        running = false;
        closeSocket();
    }

    private void closeSocket() {
        DatagramSocket s = socket;
        if (s != null && !s.isClosed()) {
            s.close();
            socket = null;
        }
    }
}
