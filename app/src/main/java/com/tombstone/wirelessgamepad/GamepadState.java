package com.tombstone.wirelessgamepad;

/**
 * Holds the full controller state that is sent to the PC at ~60 Hz.
 *
 * <p>Packet format — 20 comma-separated fields, in order:</p>
 * <pre>LX,LY,RX,RY,A,B,X,Y,LB,RB,LT,RT,DUP,DDOWN,DLEFT,DRIGHT,START,BACK,L3,R3</pre>
 *
 * <p>Sticks and triggers are floats in the range [-1, 1] and [0, 1] respectively.
 * Buttons and D-pad values are integers: 0 (released) or 1 (pressed).</p>
 *
 * <p>Thread-safety: every field is {@code volatile}. Individual field reads and
 * writes are therefore atomic on all supported Android ABIs.  {@code toPacket()}
 * reads a snapshot; it is NOT {@code synchronized} to avoid lock contention with
 * the 60 Hz sender thread. On the very rare occasion that a field changes mid-read
 * the worst outcome is a single stale byte in one UDP packet — acceptable for a
 * real-time gamepad.</p>
 */
public class GamepadState {

    public volatile float lx = 0f, ly = 0f, rx = 0f, ry = 0f;
    public volatile int a = 0, b = 0, x = 0, y = 0;
    public volatile int lb = 0, rb = 0;
    public volatile float lt = 0f, rt = 0f;
    public volatile int dUp = 0, dDown = 0, dLeft = 0, dRight = 0;
    public volatile int start = 0, back = 0;
    public volatile int l3 = 0, r3 = 0;

    /**
     * Resets every controller field to its neutral (released / centered) state.
     * Call this when the Activity pauses, the connection drops, or the user
     * disconnects — so no button, axis, or trigger remains "stuck".
     */
    public void reset() {
        lx = 0f;
        ly = 0f;
        rx = 0f;
        ry = 0f;
        a = 0;
        b = 0;
        x = 0;
        y = 0;
        lb = 0;
        rb = 0;
        lt = 0f;
        rt = 0f;
        dUp = 0;
        dDown = 0;
        dLeft = 0;
        dRight = 0;
        start = 0;
        back = 0;
        l3 = 0;
        r3 = 0;
    }

    /**
     * Returns the current state as a UDP packet string.
     * Field order matches the documented 20-field format exactly.
     */
    public String toPacket() {
        return lx + "," + ly + "," + rx + "," + ry + ","
                + a + "," + b + "," + x + "," + y + ","
                + lb + "," + rb + ","
                + lt + "," + rt + ","
                + dUp + "," + dDown + "," + dLeft + "," + dRight + ","
                + start + "," + back + ","
                + l3 + "," + r3;
    }
}
