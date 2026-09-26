package com.tombstone.wirelessgamepad;

/**
 * Thread-safe holder for the full controller state.
 * Packet format (20 comma-separated fields, sent at 60Hz):
 * LX,LY,RX,RY,A,B,X,Y,LB,RB,LT,RT,DUP,DDOWN,DLEFT,DRIGHT,START,BACK,L3,R3
 * Sticks/triggers are floats; buttons/dpad are 0 or 1.
 */
public class GamepadState {
    public volatile float lx = 0f, ly = 0f, rx = 0f, ry = 0f;
    public volatile int a = 0, b = 0, x = 0, y = 0;
    public volatile int lb = 0, rb = 0;
    public volatile float lt = 0f, rt = 0f;
    public volatile int dUp = 0, dDown = 0, dLeft = 0, dRight = 0;
    public volatile int start = 0, back = 0;
    public volatile int l3 = 0, r3 = 0;

    public synchronized String toPacket() {
        return lx + "," + ly + "," + rx + "," + ry + ","
                + a + "," + b + "," + x + "," + y + ","
                + lb + "," + rb + ","
                + lt + "," + rt + ","
                + dUp + "," + dDown + "," + dLeft + "," + dRight + ","
                + start + "," + back + ","
                + l3 + "," + r3;
    }
}
