# Wireless Gamepad

An Android app that turns your phone into a wireless game controller. Controller state is sent to a PC over UDP on the local network.

---

## Features

- **Analog sticks** — left and right, with clamped output in `[-1.0, +1.0]`
- **Triggers** — LT and RT, analog output `[0.0, 1.0]`
- **Shoulder buttons** — LB, RB
- **Face buttons** — A, B, X, Y
- **D-pad** — Up, Down, Left, Right
- **Stick click buttons** — L3, R3
- **Center buttons** — Start, Back, Home (Home opens the in-app menu)
- **Editable layout** — drag any control to any position; positions are saved
- **Reset layout** — restores all controls to responsive default positions
- **Rumble** — the PC can send rumble commands back to the Android vibrator
- **Screen always on** — the screen will not dim while the gamepad is connected

---

## Network

| Direction | Protocol | Port | Purpose |
|---|---|---|---|
| Android → PC | UDP | **5000** | Controller state packets (~60 Hz) |
| PC → Android | UDP | **5001** | Rumble command packets |

Both devices must be on the **same Wi-Fi network** (or connected via USB tethering / hotspot).

---

## Packet Format

### Controller state (Android → PC, port 5000)

A single UDP datagram containing **20 comma-separated fields** encoded as UTF-8 text, sent at approximately 60 packets per second:

```
LX,LY,RX,RY,A,B,X,Y,LB,RB,LT,RT,DUP,DDOWN,DLEFT,DRIGHT,START,BACK,L3,R3
```

| Field | Type | Range | Description |
|---|---|---|---|
| LX | float | -1.0 to +1.0 | Left stick horizontal (left = -1, right = +1) |
| LY | float | -1.0 to +1.0 | Left stick vertical (down = -1, up = +1) |
| RX | float | -1.0 to +1.0 | Right stick horizontal |
| RY | float | -1.0 to +1.0 | Right stick vertical |
| A | int | 0 or 1 | A button |
| B | int | 0 or 1 | B button |
| X | int | 0 or 1 | X button |
| Y | int | 0 or 1 | Y button |
| LB | int | 0 or 1 | Left shoulder button |
| RB | int | 0 or 1 | Right shoulder button |
| LT | float | 0.0 to 1.0 | Left trigger |
| RT | float | 0.0 to 1.0 | Right trigger |
| DUP | int | 0 or 1 | D-pad up |
| DDOWN | int | 0 or 1 | D-pad down |
| DLEFT | int | 0 or 1 | D-pad left |
| DRIGHT | int | 0 or 1 | D-pad right |
| START | int | 0 or 1 | Start button |
| BACK | int | 0 or 1 | Back / Select button |
| L3 | int | 0 or 1 | Left stick click |
| R3 | int | 0 or 1 | Right stick click |

**Example packet:**
```
0.0,0.0,0.0,0.0,0,0,0,0,0,0,0.0,0.0,0,0,0,0,0,0,0,0
```

### Rumble command (PC → Android, port 5001)

```
VIBRATE,<largeMotor>,<smallMotor>
```

- `largeMotor` and `smallMotor` are integers in the range `[0, 255]`
- Values outside `[0, 255]` are clamped automatically by the Android app
- Malformed packets are silently discarded; the listener continues running

**Example:**
```
VIBRATE,200,100
```

---

## Setup

### Android

1. Install the app on your Android phone (API 24 / Android 7.0 or higher).
2. Grant any permissions the OS requests (network access is required).
3. Connect your phone and PC to the **same Wi-Fi network**.

### Connecting

1. Open the app. The connection screen will appear.
2. Enter the **IP address of your PC** in the text field.
   - If you are using a Windows mobile hotspot, the default gateway IP is usually `192.168.137.1`.
3. Tap **Connect**.
4. The gamepad screen appears once the first packet is successfully sent.
5. If the connection fails, an error dialog will describe the problem.

### PC

The PC must run a UDP receiver listening on **port 5000** that reads and parses the 20-field packet format described above.

To send rumble commands back, the PC must send UDP datagrams in the `VIBRATE,large,small` format to the Android device's IP address on **port 5001**.

> **Note:** The PC-side software is not part of this repository.

---

## Edit Mode

1. While connected, tap the **Home (⌂)** button to open the menu.
2. Select **Edit layout**.
3. Drag any control to a new position. Controls are clamped inside the screen.
4. Positions are saved automatically when you release a control.
5. Tap Home → **Done editing layout** when finished.
6. Tap Home → **Reset layout** to restore all defaults.

---

## Building

Requires Android Studio (Hedgehog or newer) with the JBR bundled JDK.

```powershell
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Configuration

| Setting | Value |
|---|---|
| Minimum SDK | API 24 (Android 7.0) |
| Target SDK | API 37 |
| Compile SDK | API 37 |
| Build tools | Gradle 9.5 / AGP 9.3.3 |
| Language | Java 11 |

---

## Architecture

```
MainActivity
  ├── GamepadState          — volatile field store for all 20 control values
  ├── UdpGamepadClient      — sends GamepadState packets at ~60 Hz (Gamepad-UDP thread)
  ├── RumbleListener        — receives VIBRATE packets and triggers the vibrator (Gamepad-Rumble thread)
  ├── PositionStore         — SharedPreferences wrapper; saves control positions in dp
  ├── JoystickView          — custom View for the analog sticks
  └── TriggerSliderView     — custom View for LT/RT triggers
```

---

## Permissions

| Permission | Reason |
|---|---|
| `INTERNET` | Send/receive UDP packets |
| `ACCESS_WIFI_STATE` | Network state awareness |
| `ACCESS_NETWORK_STATE` | Network state awareness |
| `VIBRATE` | Rumble feedback |
