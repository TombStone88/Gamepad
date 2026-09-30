package com.tombstone.wirelessgamepad;

import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private final GamepadState state = new GamepadState();

    private UdpGamepadClient client;
    private RumbleListener rumbleListener;
    private PositionStore positionStore;
    private SettingsStore settingsStore;
    private Vibrator vibrator;

    private float density;

    private boolean editMode = false;

    private View homeScreen;
    private View connectScreen;
    private View gamepadRoot;
    private TextView statusText;
    private EditText ipInput;

    /*
     * Default positions are stored in dp (density-independent pixels).
     * These are calculated AFTER gamepadRoot is measured so they use
     * the real pixel dimensions converted to dp.
     *
     * Saved positions from PositionStore always take priority over defaults.
     */
    private final Map<String, float[]> defaults = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- Immersive landscape controller UI ---
        // Edge-to-edge: let our content draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        getWindow().addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_main);

        // Hide system bars for immersive mode
        applyImmersiveMode();

        density = getResources()
                .getDisplayMetrics()
                .density;

        positionStore = new PositionStore(this);
        settingsStore = new SettingsStore(this);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        client = new UdpGamepadClient(state);

        rumbleListener = new RumbleListener();

        homeScreen    = findViewById(R.id.homeScreen);
        connectScreen = findViewById(R.id.connectScreen);
        gamepadRoot   = findViewById(R.id.gamepadRoot);

        ipInput    = findViewById(R.id.ipInput);
        statusText = findViewById(R.id.statusText);

        Button connectBtn = findViewById(R.id.connectBtn);

        /*
         * Wait for gamepadRoot to be fully measured, THEN calculate
         * responsive defaults from its real pixel dimensions.
         */
        buildDefaultPositions();

        setupClientListener();
        setupControls();
        setupHomeScreen();
        setupFindPc();
        showHomeScreen();

        if (connectBtn != null) {
            connectBtn.setOnClickListener(v -> {
                String ip = ipInput != null
                        ? ipInput.getText().toString().trim()
                        : "";
                if (ip.isEmpty()) {
                    ip = "192.168.137.1";
                }
                statusText.setText("Connecting...");
                client.start(ip);
            });
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    private void applyImmersiveMode() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
    }

    // ----------------------------------------------------------------
    // RESPONSIVE DEFAULT POSITIONS
    // ----------------------------------------------------------------
    //
    // All positions are calculated in PIXELS from the actual gamepadRoot
    // size, then converted to dp for storage in PositionStore.
    //
    // Layout targets (approximate %):
    //
    //   LT:         left=3%,  top=2%
    //   LB:         left=3%,  top=18%
    //   leftStick:  left=9%,  top=38%
    //   dpad:       left=22%, top=60%
    //   L3:         left=5%,  top=82%
    //
    //   RT:         right=3%, top=2%
    //   RB:         right=3%, top=18%
    //   rightStick: right=9%, top=38%
    //   abxy:       right=18%,top=38%
    //   R3:         right=5%, top=82%
    //
    //   backBtn:    center-left of center zone, top=4%
    //   homeBtn:    center,                     top=3%
    //   startBtn:   center-right of center,     top=4%
    // ----------------------------------------------------------------

    private void buildDefaultPositions() {
        if (gamepadRoot == null) return;

        gamepadRoot.post(() -> {
            int W = gamepadRoot.getWidth();
            int H = gamepadRoot.getHeight();

            if (W == 0 || H == 0) {
                // Layout not ready yet — try again next frame
                gamepadRoot.post(() -> computeAndApplyDefaults(
                        gamepadRoot.getWidth(),
                        gamepadRoot.getHeight()
                ));
                return;
            }

            computeAndApplyDefaults(W, H);
        });
    }

    private void computeAndApplyDefaults(int W, int H) {

        defaults.clear();

        // Helper: view size in pixels to properly clamp positions
        // We store positions in dp; conversions happen in applySavedPosition.

        // ---- LEFT SIDE ----

        // LT — 72dp x 56dp  → top-left corner
        int ltW = dp(72), ltH = dp(56);
        putPx("ltSlider",
                clamp(W * 0.025f, 0, W - ltW),
                clamp(H * 0.03f,  0, H - ltH));

        // LB — 88dp x 40dp  → below LT
        int lbW = dp(88), lbH = dp(40);
        putPx("lbBtn",
                clamp(W * 0.025f, 0, W - lbW),
                clamp(H * 0.22f,  0, H - lbH));

        // Left stick — 160dp x 160dp → left-mid
        int stickW = dp(160);
        putPx("leftStick",
                clamp(W * 0.06f, 0, W - stickW),
                clamp(H * 0.30f, 0, H - stickW));

        // D-pad — 156dp x 156dp → lower-left
        int dpadSize = dp(156);
        putPx("dpad",
                clamp(W * 0.18f, 0, W - dpadSize),
                clamp(H * 0.55f, 0, H - dpadSize));

        // L3 — 48dp → bottom-left
        int l3Size = dp(48);
        putPx("l3Btn",
                clamp(W * 0.04f, 0, W - l3Size),
                clamp(H * 0.82f, 0, H - l3Size));

        // ---- RIGHT SIDE ----

        // RT — 72dp x 56dp → top-right
        putPx("rtSlider",
                clamp(W - W * 0.025f - ltW, 0, W - ltW),
                clamp(H * 0.03f,            0, H - ltH));

        // RB — 88dp x 40dp → below RT
        putPx("rbBtn",
                clamp(W - W * 0.025f - lbW, 0, W - lbW),
                clamp(H * 0.22f,             0, H - lbH));

        // Right stick — 160dp x 160dp → right-mid
        putPx("rightStick",
                clamp(W - W * 0.06f - stickW, 0, W - stickW),
                clamp(H * 0.30f,               0, H - stickW));

        // ABXY — 156dp x 156dp → right-mid (slightly right and lower than right stick center)
        putPx("abxy",
                clamp(W - W * 0.18f - dpadSize, 0, W - dpadSize),
                clamp(H * 0.35f,                0, H - dpadSize));

        // R3 — 48dp → bottom-right
        putPx("r3Btn",
                clamp(W - W * 0.04f - l3Size, 0, W - l3Size),
                clamp(H * 0.82f,               0, H - l3Size));

        // ---- CENTER TOP ----

        // SELECT — 64dp x 32dp
        int selW = dp(64), selH = dp(32);
        putPx("backBtn",
                clamp(W * 0.5f - dp(80) - selW * 0.5f, 0, W - selW),
                clamp(H * 0.05f, 0, H - selH));

        // HOME — 44dp circle
        int homeSize = dp(44);
        putPx("homeBtn",
                clamp(W * 0.5f - homeSize * 0.5f, 0, W - homeSize),
                clamp(H * 0.04f, 0, H - homeSize));

        // START — 64dp x 32dp
        putPx("startBtn",
                clamp(W * 0.5f + dp(80) - selW * 0.5f, 0, W - selW),
                clamp(H * 0.05f, 0, H - selH));

        // Apply saved (or default) positions
        applyAllSavedPositions();
    }

    /** Convert dp → px using screen density. */
    private int dp(int dp) {
        return Math.round(dp * density);
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(val, max));
    }

    /**
     * Store a default position given in PIXELS.
     * The map stores dp (for PositionStore compatibility).
     */
    private void putPx(String key, float xPx, float yPx) {
        defaults.put(key, new float[]{xPx / density, yPx / density});
    }

    // ----------------------------------------------------------------
    // CONNECTION STATE
    // ----------------------------------------------------------------

    private void setupClientListener() {
        client.setListener(new UdpGamepadClient.Listener() {

            @Override
            public void onConnecting() {
                runOnUiThread(() -> statusText.setText("Connecting..."));
            }

            @Override
            public void onConnected() {
                rumbleListener.start(MainActivity.this::handleRumble);
                runOnUiThread(() -> {
                    statusText.setText("Connected");
                    showGamepadScreen();
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        statusText.setText("Disconnected");
                    }
                    showConnectScreen();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    String error = (message == null || message.trim().isEmpty())
                            ? "Unable to connect"
                            : message;
                    statusText.setText("Connection failed");
                    showConnectScreen();
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Connection failed")
                            .setMessage(error)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private void showHomeScreen() {
        if (homeScreen    != null) homeScreen.setVisibility(View.VISIBLE);
        if (connectScreen != null) connectScreen.setVisibility(View.GONE);
        // INVISIBLE, not GONE — GONE views get 0 width/height, which breaks
        // buildDefaultPositions()'s calculations (every button collapses to 0,0).
        if (gamepadRoot   != null) gamepadRoot.setVisibility(View.INVISIBLE);
    }

    private void showConnectScreen() {
        if (homeScreen    != null) homeScreen.setVisibility(View.GONE);
        if (connectScreen != null) connectScreen.setVisibility(View.VISIBLE);
        if (gamepadRoot   != null) gamepadRoot.setVisibility(View.INVISIBLE);
    }

    private void showGamepadScreen() {
        if (homeScreen    != null) homeScreen.setVisibility(View.GONE);
        if (connectScreen != null) connectScreen.setVisibility(View.GONE);
        if (gamepadRoot   != null) gamepadRoot.setVisibility(View.VISIBLE);
    }

    private void setupHomeScreen() {
        Button homeConnectBtn = findViewById(R.id.homeConnectBtn);
        Button homeGamepadBtn = findViewById(R.id.homeGamepadBtn);
        Button homeSettingsBtn = findViewById(R.id.homeSettingsBtn);
        Button connectHomeBtn = findViewById(R.id.connectHomeBtn);

        if (homeConnectBtn != null) {
            homeConnectBtn.setOnClickListener(v -> showConnectScreen());
        }
        if (homeGamepadBtn != null) {
            homeGamepadBtn.setOnClickListener(v -> {
                if (client != null && client.isRunning()) {
                    showGamepadScreen();
                } else {
                    android.widget.Toast.makeText(this,
                            "Connect to your PC first", android.widget.Toast.LENGTH_SHORT).show();
                    showConnectScreen();
                }
            });
        }
        if (homeSettingsBtn != null) {
            homeSettingsBtn.setOnClickListener(v -> showSettingsMenu());
        }
        if (connectHomeBtn != null) {
            connectHomeBtn.setOnClickListener(v -> showHomeScreen());
        }
    }

    private void setupFindPc() {
        Button findPcBtn = findViewById(R.id.findPcBtn);
        if (findPcBtn == null) return;

        findPcBtn.setOnClickListener(v -> {
            findPcBtn.setEnabled(false);
            findPcBtn.setText("Searching...");

            PcDiscovery.search(2000, foundList -> runOnUiThread(() -> {
                findPcBtn.setEnabled(true);
                findPcBtn.setText("🔍 Find PC");

                if (foundList.isEmpty()) {
                    android.widget.Toast.makeText(this,
                            "No PC found. Make sure gamepad_receiver.py is running, then try again — or enter the IP manually.",
                            android.widget.Toast.LENGTH_LONG).show();
                } else if (foundList.size() == 1) {
                    ipInput.setText(foundList.get(0).ip);
                    android.widget.Toast.makeText(this,
                            "Found " + foundList.get(0).name, android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    CharSequence[] labels = new CharSequence[foundList.size()];
                    for (int i = 0; i < foundList.size(); i++) {
                        labels[i] = foundList.get(i).name + " (" + foundList.get(i).ip + ")";
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Choose a PC")
                            .setItems(labels, (dialog, which) ->
                                    ipInput.setText(foundList.get(which).ip))
                            .show();
                }
            }));
        });
    }

    // ----------------------------------------------------------------
    // MENU
    // ----------------------------------------------------------------

    private void showHomeMenu() {
        String editLabel = editMode ? "Done editing layout" : "Edit layout";

        new AlertDialog.Builder(this)
                .setTitle("Gamepad Menu")
                .setItems(
                        new CharSequence[]{editLabel, "Reset layout", "Settings", "Disconnect", "Cancel"},
                        (dialog, which) -> {
                            switch (which) {
                                case 0:
                                    editMode = !editMode;
                                    updateEditMode();
                                    break;
                                case 1:
                                    resetLayout();
                                    break;
                                case 2:
                                    showSettingsMenu();
                                    break;
                                case 3:
                                    disconnect();
                                    break;
                                default:
                                    break;
                            }
                        }
                )
                .show();
    }

    private void updateEditMode() {
        Button homeBtn = findViewById(R.id.homeBtn);
        if (homeBtn != null) {
            homeBtn.setText(editMode ? "✓" : "⌂");
        }
    }

    private void showSettingsMenu() {
        boolean hard = settingsStore.isHardTriggers();
        new AlertDialog.Builder(this)
                .setTitle("Trigger style")
                .setSingleChoiceItems(
                        new CharSequence[]{"Pressure-sensitive (drag)", "Hard button (on/off)"},
                        hard ? 1 : 0,
                        (dialog, which) -> {
                            boolean newHard = (which == 1);
                            settingsStore.setHardTriggers(newHard);
                            applyTriggerMode(newHard);
                            dialog.dismiss();
                        })
                .setNegativeButton("Close", null)
                .show();
    }

    private void applyTriggerMode(boolean hard) {
        TriggerSliderView lt = findViewById(R.id.ltSlider);
        TriggerSliderView rt = findViewById(R.id.rtSlider);
        if (lt != null) lt.setHardMode(hard);
        if (rt != null) rt.setHardMode(hard);
    }

    // ----------------------------------------------------------------
    // RESET LAYOUT
    // ----------------------------------------------------------------

    private void resetLayout() {
        new AlertDialog.Builder(this)
                .setTitle("Reset layout?")
                .setMessage("All controller positions will return to their defaults.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, which) -> {
                    for (String key : defaults.keySet()) {
                        positionStore.remove(key);
                    }
                    applyAllSavedPositions();
                })
                .show();
    }

    // ----------------------------------------------------------------
    // DISCONNECT
    // ----------------------------------------------------------------

    private void disconnect() {
        if (client        != null) client.stop();
        if (rumbleListener!= null) rumbleListener.stop();
        if (vibrator      != null && vibrator.hasVibrator()) vibrator.cancel();

        state.reset();
        editMode = false;
        statusText.setText("Disconnected");
        showHomeScreen();
    }

    // ----------------------------------------------------------------
    // RUMBLE
    // ----------------------------------------------------------------

    private void handleRumble(int largeMotor, int smallMotor) {
        if (vibrator == null || !vibrator.hasVibrator()) return;

        int strength = Math.max(0, Math.min(255, Math.max(largeMotor, smallMotor)));
        if (strength <= 0) {
            vibrator.cancel();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(80, Math.max(1, strength)));
        } else {
            vibrator.vibrate(80);
        }
    }

    // ----------------------------------------------------------------
    // CONTROLS SETUP
    // ----------------------------------------------------------------

    private void setupControls() {

        JoystickView leftStick  = findViewById(R.id.leftStick);
        JoystickView rightStick = findViewById(R.id.rightStick);

        if (leftStick != null) {
            leftStick.setListener((x, y) -> {
                state.lx = x;
                state.ly = y;
            });
            makeDraggable(leftStick, "leftStick");
        }

        if (rightStick != null) {
            rightStick.setListener((x, y) -> {
                state.rx = x;
                state.ry = y;
            });
            makeDraggable(rightStick, "rightStick");
        }

        // D-pad group
        View dpad = findViewById(R.id.dpad);
        if (dpad != null) makeDraggable(dpad, "dpad");

        // ABXY group
        View abxy = findViewById(R.id.abxy);
        if (abxy != null) makeDraggable(abxy, "abxy");

        // Triggers
        TriggerSliderView ltSlider = findViewById(R.id.ltSlider);
        TriggerSliderView rtSlider = findViewById(R.id.rtSlider);

        if (ltSlider != null) {
            ltSlider.setListener(val -> state.lt = val);
            makeDraggable(ltSlider, "ltSlider");
        }

        if (rtSlider != null) {
            rtSlider.setListener(val -> state.rt = val);
            makeDraggable(rtSlider, "rtSlider");
        }

        applyTriggerMode(settingsStore.isHardTriggers());

        // ABXY buttons (inside group — no individual dragging)
        bindButton(R.id.btnA, v -> state.a = v);
        bindButton(R.id.btnB, v -> state.b = v);
        bindButton(R.id.btnX, v -> state.x = v);
        bindButton(R.id.btnY, v -> state.y = v);

        // D-pad buttons (inside group — no individual dragging)
        bindButton(R.id.dUp,    v -> state.dUp    = v);
        bindButton(R.id.dDown,  v -> state.dDown  = v);
        bindButton(R.id.dLeft,  v -> state.dLeft  = v);
        bindButton(R.id.dRight, v -> state.dRight = v);

        // Individual draggable buttons
        bindDraggableButton(R.id.lbBtn,    "lbBtn",    v -> state.lb    = v);
        bindDraggableButton(R.id.rbBtn,    "rbBtn",    v -> state.rb    = v);
        bindDraggableButton(R.id.l3Btn,    "l3Btn",    v -> state.l3    = v);
        bindDraggableButton(R.id.r3Btn,    "r3Btn",    v -> state.r3    = v);
        bindDraggableButton(R.id.startBtn, "startBtn", v -> state.start = v);
        bindDraggableButton(R.id.backBtn,  "backBtn",  v -> state.back  = v);

        // Home button — intentionally NEVER draggable, so there is always a
        // guaranteed way back out of edit mode no matter what else is going on.
        Button homeBtn = findViewById(R.id.homeBtn);
        if (homeBtn != null) {
            homeBtn.setOnClickListener(v -> {
                if (editMode) {
                    editMode = false;
                    updateEditMode();
                } else {
                    showHomeMenu();
                }
            });
        }

        Button settingsBtn = findViewById(R.id.settingsBtn);
        if (settingsBtn != null) {
            settingsBtn.setOnClickListener(v -> showSettingsMenu());
        }
    }

    // ----------------------------------------------------------------
    // POSITION MANAGEMENT
    // ----------------------------------------------------------------

    private void applyAllSavedPositions() {
        if (defaults.isEmpty()) return;

        for (String key : defaults.keySet()) {
            int id = getResources().getIdentifier(key, "id", getPackageName());
            if (id == 0) continue;
            View view = findViewById(id);
            if (view != null) applySavedPosition(view, key);
        }
    }

    private void applySavedPosition(View v, String key) {
        if (v == null || key == null) return;

        float[] def = defaults.get(key);
        if (def == null) return;

        float[] pos = positionStore.load(key, def[0], def[1]);

        v.post(() -> {
            float x = pos[0] * density;
            float y = pos[1] * density;

            View parent = (View) v.getParent();
            if (parent != null) {
                float maxX = Math.max(0, parent.getWidth()  - v.getWidth());
                float maxY = Math.max(0, parent.getHeight() - v.getHeight());
                x = clamp(x, 0, maxX);
                y = clamp(y, 0, maxY);
            }

            v.setX(x);
            v.setY(y);
        });
    }

    // ----------------------------------------------------------------
    // DRAGGABLE BUTTONS
    // ----------------------------------------------------------------

    private void bindDraggableButton(int id, String key, IntSetter setter) {
        Button btn = findViewById(id);
        if (btn == null) return;

        btn.setOnTouchListener((v, event) -> {
            if (editMode) {
                return handleDrag(v, event, key);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setter.set(1);
                    return true;
                case MotionEvent.ACTION_UP:
                    setter.set(0);
                    v.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    setter.set(0);
                    return true;
                default:
                    return false;
            }
        });
    }

    // ----------------------------------------------------------------
    // BUTTONS INSIDE D-PAD / ABXY GROUPS
    // ----------------------------------------------------------------

    private void bindButton(int id, IntSetter setter) {
        Button btn = findViewById(id);
        if (btn == null) return;

        btn.setOnTouchListener((v, event) -> {
            // In edit mode the parent group owns dragging
            if (editMode) return true;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setter.set(1);
                    return true;
                case MotionEvent.ACTION_UP:
                    setter.set(0);
                    v.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    setter.set(0);
                    return true;
                default:
                    return false;
            }
        });
    }

    // ----------------------------------------------------------------
    // GROUP / JOYSTICK / TRIGGER DRAGGING
    // ----------------------------------------------------------------

    private void makeDraggable(View v, String key) {
        if (v == null) return;
        v.setOnTouchListener((view, event) -> {
            if (editMode) {
                return handleDrag(view, event, key);
            }
            // Normal mode: pass through to the custom view's own touch handling
            return false;
        });
    }

    // ----------------------------------------------------------------
    // DRAG IMPLEMENTATION
    //
    // Uses event.getX()/getY() (relative to the dragged view) so there
    // is no jump on first touch. Raw coordinates are NOT used.
    // ----------------------------------------------------------------

    private float dragStartX;
    private float dragStartY;
    private float viewStartX;
    private float viewStartY;

    private boolean handleDrag(View v, MotionEvent event, String key) {
        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:
                dragStartX = event.getX();
                dragStartY = event.getY();
                viewStartX = v.getX();
                viewStartY = v.getY();
                return true;

            case MotionEvent.ACTION_MOVE:
                float newX = viewStartX + (event.getX() - dragStartX);
                float newY = viewStartY + (event.getY() - dragStartY);

                View parent = (View) v.getParent();
                if (parent != null) {
                    float maxX = Math.max(0, parent.getWidth()  - v.getWidth());
                    float maxY = Math.max(0, parent.getHeight() - v.getHeight());
                    newX = clamp(newX, 0, maxX);
                    newY = clamp(newY, 0, maxY);
                }

                v.setX(newX);
                v.setY(newY);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                positionStore.save(key, v.getX() / density, v.getY() / density);
                return true;

            default:
                return false;
        }
    }

    // ----------------------------------------------------------------
    // LIFECYCLE
    // ----------------------------------------------------------------

    @Override
    protected void onPause() {
        super.onPause();
        // Clear all held buttons so nothing stays "stuck"
        state.reset();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (client        != null) client.stop();
        if (rumbleListener!= null) rumbleListener.stop();
        if (vibrator      != null) vibrator.cancel();
    }

    interface IntSetter {
        void set(int value);
    }
}