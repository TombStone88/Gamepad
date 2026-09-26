package com.tombstone.wirelessgamepad;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Sends the controller state to the PC over UDP at approximately 60 Hz.
 *
 * <p>Connection lifecycle:</p>
 * <pre>
 *   DISCONNECTED
 *        ↓  start()
 *   CONNECTING          → onConnecting()
 *        ↓  first packet sent
 *   CONNECTED           → onConnected()
 *        ↓  stop() or IO error
 *   DISCONNECTED        → onDisconnected()  (only on unexpected disconnect)
 *
 *   On IO error during CONNECTING or CONNECTED:
 *        → onError(message)
 *        → onDisconnected()
 * </pre>
 *
 * <p>Calling {@link #stop()} for an intentional disconnect does NOT fire
 * {@link Listener#onDisconnected()} — the caller is expected to update its own
 * UI (see {@code MainActivity.disconnect()}).</p>
 */
public class UdpGamepadClient {

    public static final int PORT = 5000;

    /** Target send interval: ~60 packets per second. */
    private static final int SEND_INTERVAL_MS = 16;

    // -----------------------------------------------------------------
    // Listener interface
    // -----------------------------------------------------------------

    public interface Listener {
        /** Called from the worker thread when the socket is being set up. */
        void onConnecting();

        /** Called from the worker thread once the first packet has been sent. */
        void onConnected();

        /**
         * Called from the worker thread when the connection drops unexpectedly
         * (i.e. NOT when {@link #stop()} was called deliberately).
         */
        void onDisconnected();

        /** Called from the worker thread when a network error prevents connection. */
        void onError(String message);
    }

    // -----------------------------------------------------------------
    // State
    // -----------------------------------------------------------------

    private final GamepadState state;

    private volatile boolean isRunning = false;

    /**
     * Set to {@code true} by {@link #stop()} before interrupting the worker.
     * When {@code true}, the finally-block skips {@link Listener#onDisconnected()}
     * because the caller has already handled its own UI update.
     */
    private volatile boolean stoppedIntentionally = false;

    private DatagramSocket socket;
    private InetAddress address;
    private Thread workerThread;

    private Listener listener;

    // -----------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------

    public UdpGamepadClient(GamepadState state) {
        this.state = state;
    }

    // -----------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------

    public synchronized void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Starts the sender thread. No-op if already running.
     *
     * @param pcIp the IPv4 address of the PC receiver
     */
    public synchronized void start(String pcIp) {

        if (isRunning) {
            return;
        }

        if (pcIp == null || pcIp.trim().isEmpty()) {
            notifyError("PC IP address is empty");
            return;
        }

        final String ip = pcIp.trim();

        isRunning = true;
        stoppedIntentionally = false;

        notifyConnecting();

        workerThread = new Thread(() -> {

            try {

                address = InetAddress.getByName(ip);

                socket = new DatagramSocket();
                socket.setReuseAddress(true);

                byte[] initialData = state
                        .toPacket()
                        .getBytes(StandardCharsets.UTF_8);

                DatagramPacket packet = new DatagramPacket(
                        initialData,
                        initialData.length,
                        address,
                        PORT
                );

                /*
                 * UDP has no real handshake. We consider the client "connected" once:
                 *  1. The IP was resolved
                 *  2. The socket was created
                 *  3. The first packet was successfully handed to the OS network stack
                 */
                socket.send(packet);

                notifyConnected();

                // ---------- 60 Hz send loop ----------
                while (isRunning && !Thread.currentThread().isInterrupted()) {

                    byte[] data = state
                            .toPacket()
                            .getBytes(StandardCharsets.UTF_8);

                    packet.setData(data);
                    packet.setLength(data.length);

                    socket.send(packet);

                    Thread.sleep(SEND_INTERVAL_MS);
                }
                // -------------------------------------

            } catch (InterruptedException e) {

                // Normal shutdown via stop() — restore interrupt flag but do not report error.
                Thread.currentThread().interrupt();

            } catch (IOException e) {

                if (isRunning && !stoppedIntentionally) {
                    notifyError(
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : "Network error"
                    );
                }

            } catch (Exception e) {

                if (isRunning && !stoppedIntentionally) {
                    notifyError(
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : "Connection failed"
                    );
                }

            } finally {

                isRunning = false;

                closeSocket();

                /*
                 * Only notify the UI that the connection dropped if this was
                 * NOT a deliberate stop(). When stop() is called intentionally,
                 * the caller (MainActivity.disconnect()) has already updated the UI.
                 */
                if (!stoppedIntentionally) {
                    notifyDisconnected();
                }
            }

        }, "Gamepad-UDP");

        workerThread.setDaemon(true);
        workerThread.start();
    }

    /**
     * Stops the sender thread deliberately.
     * Does NOT fire {@link Listener#onDisconnected()}.
     */
    public synchronized void stop() {

        if (!isRunning && socket == null) {
            return;
        }

        stoppedIntentionally = true;
        isRunning = false;

        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }

        closeSocket();
    }

    public boolean isRunning() {
        return isRunning;
    }

    // -----------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------

    private synchronized void closeSocket() {

        if (socket != null) {

            try {
                socket.close();
            } catch (Exception ignored) {
            }

            socket = null;
        }
    }

    private void notifyConnecting() {
        Listener l = listener;
        if (l != null) l.onConnecting();
    }

    private void notifyConnected() {
        Listener l = listener;
        if (l != null) l.onConnected();
    }

    private void notifyDisconnected() {
        Listener l = listener;
        if (l != null) l.onDisconnected();
    }

    private void notifyError(String message) {
        Listener l = listener;
        if (l != null) l.onError(message);
    }
}