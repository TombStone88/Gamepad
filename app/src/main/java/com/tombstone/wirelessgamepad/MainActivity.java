package com.tombstone.wirelessgamepad;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public static final int MODE_ARCADE = 0;
    public static final int MODE_RACING = 1;

    private final GamepadState state = new GamepadState();

    private UdpGamepadClient client;
    private RumbleListener rumbleListener;
    private PositionStore positionStore;
    private SettingsStore settingsStore;
    private Vibrator vibrator;

    private float density;

    private int currentControllerMode = MODE_ARCADE;
    private boolean gyroActive = false;
    private boolean editMode = false;
    private boolean isDrawerOpen = false;
    private int drawerWidth = 0;

    private View homeScreen;
    private View connectScreen;
    private View gamepadRoot;
    private View racingRoot;
    private RacingController racing;
    private TextView statusText;
    private EditText ipInput;

    // Slide-out Drawer Views
    private View drawerScrim;
    private View slideMenuDrawer;
    private Button menuBtn;
    private Button drawerCloseBtn;
    private View drawerCustomizationCard;
    private View drawerSettingCard;
    private View drawerModeCard;
    private Button drawerModeSwitchBtn;
    private TextView drawerModeTitle;
    private TextView drawerModeSub;
    private View drawerExitCard;

    // Top Bar & Edit Mode Views
    private View controllerTopBar;
    private Button gyroToggleBtn;
    private TextView controllerStatusPill;
    private TextView controllerBatteryText;
    private Button customizationDoneBtn;
    private View editModeBanner;
    private Button doneEditBtn;

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
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        getWindow().addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        setContentView(R.layout.activity_main);

        applyImmersiveMode();

        density = getResources().getDisplayMetrics().density;

        positionStore = new PositionStore(this);
        settingsStore = new SettingsStore(this);
        initVibrator();

        client = new UdpGamepadClient(state);
        rumbleListener = new RumbleListener();

        homeScreen    = findViewById(R.id.homeScreen);
        connectScreen = findViewById(R.id.connectScreen);
        gamepadRoot   = findViewById(R.id.gamepadRoot);
        racingRoot    = findViewById(R.id.racingRoot);

        padForCutout(homeScreen);
        padForCutout(connectScreen);

        ipInput    = findViewById(R.id.ipInput);
        statusText = findViewById(R.id.statusText);

        Button connectBtn = findViewById(R.id.connectBtn);

        buildDefaultPositions();
        setupClientListener();
        setupControls();
        setupHomeScreen();
        setupConnectScreen();
        setupFindPc();
        setupSlideMenu();
        setupTopBar();

        racing = new RacingController(this, state, racingRoot);
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
                showGamepadScreen();
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
    // RESPONSIVE DEFAULT POSITIONS FOR ARCADE CONTROLS
    // ----------------------------------------------------------------

    private void buildDefaultPositions() {
        if (gamepadRoot == null) return;

        gamepadRoot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int W = r - l;
            int H = b - t;
            if (W > H && (W != or - ol || H != ob - ot)) {
                computeAndApplyDefaults(W, H);
            }
        });
    }

    private void computeAndApplyDefaults(int W, int H) {
        defaults.clear();

        // ---- LEFT SIDE ----
        int ltW = dp(68), ltH = dp(56);
        putPx("ltSlider",
                clamp(W * 0.03f, 0, W - ltW),
                clamp(H * 0.12f, 0, H - ltH));

        int lbW = dp(80), lbH = dp(42);
        putPx("lbBtn",
                clamp(W * 0.03f, 0, W - lbW),
                clamp(H * 0.28f, 0, H - lbH));

        int stickW = dp(160);
        putPx("leftStick",
                clamp(W * 0.06f, 0, W - stickW),
                clamp(H * 0.44f, 0, H - stickW));

        int dpadSize = dp(156);
        putPx("dpad",
                clamp(W * 0.22f, 0, W - dpadSize),
                clamp(H * 0.46f, 0, H - dpadSize));

        // ---- RIGHT SIDE ----
        putPx("rtSlider",
                clamp(W - W * 0.03f - ltW, 0, W - ltW),
                clamp(H * 0.12f,           0, H - ltH));

        putPx("rbBtn",
                clamp(W - W * 0.03f - lbW, 0, W - lbW),
                clamp(H * 0.28f,           0, H - lbH));

        putPx("rightStick",
                clamp(W - W * 0.06f - stickW, 0, W - stickW),
                clamp(H * 0.44f,              0, H - stickW));

        putPx("abxy",
                clamp(W - W * 0.22f - dpadSize, 0, W - dpadSize),
                clamp(H * 0.46f,               0, H - dpadSize));

        // ---- CENTER TOP ----
        int selW = dp(64), selH = dp(28);
        putPx("backBtn",
                clamp(W * 0.5f - dp(54) - selW, 0, W - selW),
                clamp(dp(6), 0, H - selH));

        putPx("startBtn",
                clamp(W * 0.5f + dp(54), 0, W - selW),
                clamp(dp(6), 0, H - selH));

        applyAllSavedPositions();
    }

    private int dp(int dp) {
        return Math.round(dp * density);
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(val, max));
    }

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
                Log.i("RumbleListener", "onConnected(): Starting RumbleListener on UDP port 5001...");
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

    private void padForCutout(View v) {
        if (v == null) return;
        final int l = v.getPaddingLeft(), t = v.getPaddingTop(), r = v.getPaddingRight(), b = v.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            androidx.core.graphics.Insets c = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            view.setPadding(l + c.left, t + c.top, r + c.right, b + c.bottom);
            return insets;
        });
    }

    private void showHomeScreen() {
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (racing != null) racing.hide();
        if (homeScreen    != null) homeScreen.setVisibility(View.VISIBLE);
        if (connectScreen != null) connectScreen.setVisibility(View.GONE);
        if (gamepadRoot   != null) gamepadRoot.setVisibility(View.INVISIBLE);
        updateHomeStatus();
    }

    private void updateHomeStatus() {
        boolean linked = client != null && client.isRunning();
        TextView pill = findViewById(R.id.homeStatusPill);
        if (pill != null) pill.setText(linked ? "CONNECTED // SLOT 01" : "READY TO PLAY // SLOT 01");
        TextView sub = findViewById(R.id.homeConnectSub);
        if (sub != null) sub.setText(linked ? "Connected - tap to open controller" : "Search and link wireless controller");
        TextView badge = findViewById(R.id.homeReadyBadge);
        if (badge != null) badge.setText(linked ? "LINKED" : "READY");
        updateBatteryTelemetry();
    }

    private void showConnectScreen() {
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (racing != null) racing.hide();
        if (homeScreen    != null) homeScreen.setVisibility(View.GONE);
        if (connectScreen != null) connectScreen.setVisibility(View.VISIBLE);
        if (gamepadRoot   != null) gamepadRoot.setVisibility(View.INVISIBLE);
        updateBatteryTelemetry();
    }

    private void showGamepadScreen() {
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        if (homeScreen    != null) homeScreen.setVisibility(View.GONE);
        if (connectScreen != null) connectScreen.setVisibility(View.GONE);
        if (gamepadRoot   != null) gamepadRoot.setVisibility(View.VISIBLE);
        if (currentControllerMode == MODE_RACING) {
            setArcadeControlsVisibility(View.GONE);
            if (racing != null) {
                racing.setMode(gyroActive ? RacingController.MODE_GYRO : RacingController.MODE_WHEEL);
                racing.show();
            }
        } else {
            setArcadeControlsVisibility(View.VISIBLE);
            if (racing != null) {
                racing.hide();
            }
        }
        updateBatteryTelemetry();
        updateGyroUI();
        updateGyroButtonVisibility();
        if (controllerTopBar != null) {
            controllerTopBar.bringToFront();
        }
    }

    private void setArcadeControlsVisibility(int visibility) {
        int[] arcadeIds = {
                R.id.leftStick, R.id.rightStick,
                R.id.ltSlider, R.id.rtSlider,
                R.id.lbBtn, R.id.rbBtn,
                R.id.backBtn, R.id.startBtn,
                R.id.dpad, R.id.abxy
        };
        for (int id : arcadeIds) {
            View v = findViewById(id);
            if (v != null) {
                v.setVisibility(visibility);
            }
        }
    }

    private void setupHomeScreen() {
        View homeConnectBtn = findViewById(R.id.homeConnectBtn);
        View homeSettingsBtn = findViewById(R.id.homeSettingsBtn);
        View homeConsole = findViewById(R.id.homeConsole);
        View homeStatusPill = findViewById(R.id.homeStatusPill);

        if (homeConnectBtn != null) {
            homeConnectBtn.setOnClickListener(v -> showConnectScreen());
        }
        // Direct shortcuts to test/preview the new Controller UI & Navigation anytime
        if (homeConsole != null) {
            homeConsole.setOnClickListener(v -> showGamepadScreen());
        }
        if (homeStatusPill != null) {
            homeStatusPill.setOnClickListener(v -> showGamepadScreen());
        }
        if (homeSettingsBtn != null) {
            homeSettingsBtn.setOnClickListener(v -> showSettingsMenu());
        }
        View homeExitBtn = findViewById(R.id.homeExitBtn);
        if (homeExitBtn != null) {
            homeExitBtn.setOnClickListener(v -> {
                disconnect();
                finish();
            });
        }
    }

    private void setupConnectScreen() {
        View connectBackBtn = findViewById(R.id.connectBackBtn);
        if (connectBackBtn != null) {
            connectBackBtn.setOnClickListener(v -> showHomeScreen());
        }

        View presetTvBtn = findViewById(R.id.presetTvBtn);
        if (presetTvBtn != null) {
            presetTvBtn.setOnClickListener(v -> {
                if (ipInput != null) ipInput.setText("192.168.1.42");
            });
        }

        View presetLocalhostBtn = findViewById(R.id.presetLocalhostBtn);
        if (presetLocalhostBtn != null) {
            presetLocalhostBtn.setOnClickListener(v -> {
                if (ipInput != null) ipInput.setText("127.0.0.1");
            });
        }

        View presetPcRigBtn = findViewById(R.id.presetPcRigBtn);
        if (presetPcRigBtn != null) {
            presetPcRigBtn.setOnClickListener(v -> {
                if (ipInput != null) ipInput.setText("192.168.1.1");
            });
        }

        View beaconLinkBtn1 = findViewById(R.id.beaconLinkBtn1);
        if (beaconLinkBtn1 != null) {
            beaconLinkBtn1.setOnClickListener(v -> {
                if (ipInput != null) ipInput.setText("192.168.1.44");
                if (statusText != null) statusText.setText("Connecting...");
                client.start("192.168.1.44");
                showGamepadScreen();
            });
        }

        View beaconLinkBtn2 = findViewById(R.id.beaconLinkBtn2);
        if (beaconLinkBtn2 != null) {
            beaconLinkBtn2.setOnClickListener(v -> {
                if (ipInput != null) ipInput.setText("192.168.1.89");
                if (statusText != null) statusText.setText("Connecting...");
                client.start("192.168.1.89");
                showGamepadScreen();
            });
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
                    Toast.makeText(this,
                            "No PC found. Make sure gamepad_receiver.py is running, then try again — or enter the IP manually.",
                            Toast.LENGTH_LONG).show();
                } else if (foundList.size() == 1) {
                    ipInput.setText(foundList.get(0).ip);
                    Toast.makeText(this,
                            "Found " + foundList.get(0).name, Toast.LENGTH_SHORT).show();
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
    // TOP BAR & GYRO TOGGLE
    // ----------------------------------------------------------------

    private void setupTopBar() {
        controllerTopBar = findViewById(R.id.controllerTopBar);
        gyroToggleBtn = findViewById(R.id.gyroToggleBtn);
        controllerStatusPill = findViewById(R.id.controllerStatusPill);
        controllerBatteryText = findViewById(R.id.controllerBatteryText);
        customizationDoneBtn = findViewById(R.id.customizationDoneBtn);

        if (gyroToggleBtn != null) {
            gyroToggleBtn.setOnClickListener(v -> toggleGyro());
        }
        if (customizationDoneBtn != null) {
            customizationDoneBtn.setOnClickListener(v -> setEditMode(false));
        }
        updateGyroButtonVisibility();
    }

    private void updateGyroButtonVisibility() {
        if (gyroToggleBtn != null) {
            gyroToggleBtn.setVisibility(currentControllerMode == MODE_RACING ? View.VISIBLE : View.GONE);
        }
    }

    private void toggleGyro() {
        gyroActive = !gyroActive;
        updateGyroUI();
        if (racing != null) {
            racing.setExternalGyroActive(gyroActive);
        }
        Toast.makeText(this, gyroActive ? "Gyroscope: ON" : "Gyroscope: OFF", Toast.LENGTH_SHORT).show();
    }

    private void updateGyroUI() {
        if (gyroToggleBtn != null) {
            gyroToggleBtn.setSelected(gyroActive);
            gyroToggleBtn.setText(gyroActive ? "⟲ GYRO: ON" : "⟲ GYRO: OFF");
        }
        if (controllerStatusPill != null) {
            if (currentControllerMode == MODE_RACING) {
                controllerStatusPill.setText(gyroActive ? "● GYRO ACTIVE" : "● RACING ACTIVE");
            } else {
                controllerStatusPill.setText("● CONTROLLER ACTIVE");
            }
            controllerStatusPill.setTextColor(gyroActive ? 0xFFFF5E62 : 0xFF34C759);
        }
    }

    private void updateBatteryTelemetry() {
        Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i != null) {
            int level = i.getIntExtra("level", -1);
            int scale = i.getIntExtra("scale", 100);
            if (level >= 0 && scale > 0) {
                int pct = (level * 100 / scale);
                TextView bat = findViewById(R.id.homeBatteryText);
                if (bat != null) bat.setText(pct + "%");
                TextView connectBat = findViewById(R.id.connectBatteryText);
                if (connectBat != null) connectBat.setText(pct + "%");
                if (controllerBatteryText != null) controllerBatteryText.setText("🔋 " + pct + "%");
            }
        }
    }

    // ----------------------------------------------------------------
    // SLIDE-OUT MENU DRAWER (slid-window.png)
    // ----------------------------------------------------------------

    private void setupSlideMenu() {
        menuBtn = findViewById(R.id.menuBtn);
        drawerScrim = findViewById(R.id.drawerScrim);
        slideMenuDrawer = findViewById(R.id.slideMenuDrawer);
        drawerCloseBtn = findViewById(R.id.drawerCloseBtn);
        drawerCustomizationCard = findViewById(R.id.drawerCustomizationCard);
        drawerSettingCard = findViewById(R.id.drawerSettingCard);
        drawerModeCard = findViewById(R.id.drawerModeCard);
        drawerModeSwitchBtn = findViewById(R.id.drawerModeSwitchBtn);
        drawerModeTitle = findViewById(R.id.drawerModeTitle);
        drawerModeSub = findViewById(R.id.drawerModeSub);
        drawerExitCard = findViewById(R.id.drawerExitCard);
        editModeBanner = findViewById(R.id.editModeBanner);
        doneEditBtn = findViewById(R.id.doneEditBtn);

        // Position drawer offscreen to the right
        if (slideMenuDrawer != null) {
            slideMenuDrawer.post(() -> {
                drawerWidth = slideMenuDrawer.getWidth();
                if (drawerWidth <= 0) drawerWidth = dp(320);
                slideMenuDrawer.setTranslationX(drawerWidth);
            });
        }

        if (menuBtn != null) {
            menuBtn.setOnClickListener(v -> toggleSlideMenu());
        }
        if (drawerCloseBtn != null) {
            drawerCloseBtn.setOnClickListener(v -> closeSlideMenu());
        }
        if (drawerScrim != null) {
            drawerScrim.setOnClickListener(v -> closeSlideMenu());
        }
        if (drawerCustomizationCard != null) {
            drawerCustomizationCard.setOnClickListener(v -> {
                closeSlideMenu();
                setEditMode(true);
            });
        }
        if (drawerSettingCard != null) {
            drawerSettingCard.setOnClickListener(v -> {
                closeSlideMenu();
                showSettingsMenu();
            });
        }
        if (drawerModeCard != null) {
            drawerModeCard.setOnClickListener(v -> toggleControllerMode());
        }
        if (drawerModeSwitchBtn != null) {
            drawerModeSwitchBtn.setOnClickListener(v -> toggleControllerMode());
        }
        if (drawerExitCard != null) {
            drawerExitCard.setOnClickListener(v -> {
                closeSlideMenu();
                disconnect();
            });
        }
        if (doneEditBtn != null) {
            doneEditBtn.setOnClickListener(v -> setEditMode(false));
        }
    }

    private void toggleSlideMenu() {
        if (isDrawerOpen) closeSlideMenu();
        else openSlideMenu();
    }

    private void openSlideMenu() {
        if (isDrawerOpen || slideMenuDrawer == null) return;
        isDrawerOpen = true;
        updateDrawerModeDisplay(currentControllerMode == MODE_RACING);

        if (drawerScrim != null) {
            drawerScrim.setVisibility(View.VISIBLE);
            drawerScrim.bringToFront();
            drawerScrim.setAlpha(0f);
            drawerScrim.animate().alpha(1f).setDuration(320).start();
        }

        // Hide overlapping right-side controls so they never appear over or interfere with the menu
        View rbBtn = findViewById(R.id.rbBtn);
        View rtSlider = findViewById(R.id.rtSlider);
        if (rbBtn != null) rbBtn.setVisibility(View.INVISIBLE);
        if (rtSlider != null) rtSlider.setVisibility(View.INVISIBLE);

        slideMenuDrawer.bringToFront();
        slideMenuDrawer.animate()
                .translationX(0f)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                .setDuration(380)
                .start();
    }

    private void closeSlideMenu() {
        if (!isDrawerOpen || slideMenuDrawer == null) return;
        isDrawerOpen = false;

        if (drawerWidth <= 0) drawerWidth = slideMenuDrawer.getWidth();
        if (drawerWidth <= 0) drawerWidth = dp(320);

        slideMenuDrawer.animate()
                .translationX(drawerWidth)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .setDuration(320)
                .start();

        if (drawerScrim != null) {
            drawerScrim.animate()
                    .alpha(0f)
                    .setDuration(280)
                    .withEndAction(() -> {
                        drawerScrim.setVisibility(View.GONE);
                        // Restore controller buttons if currently in arcade mode
                        if (currentControllerMode == MODE_ARCADE) {
                            View rbBtn = findViewById(R.id.rbBtn);
                            View rtSlider = findViewById(R.id.rtSlider);
                            if (rbBtn != null) rbBtn.setVisibility(View.VISIBLE);
                            if (rtSlider != null) rtSlider.setVisibility(View.VISIBLE);
                        }
                    })
                    .start();
        }
    }

    private void toggleControllerMode() {
        int nextMode = (currentControllerMode == MODE_ARCADE) ? MODE_RACING : MODE_ARCADE;
        setControllerMode(nextMode);
        closeSlideMenu();
    }

    private void setControllerMode(int newMode) {
        currentControllerMode = newMode;
        updateGyroButtonVisibility();
        if (currentControllerMode == MODE_RACING) {
            setArcadeControlsVisibility(View.GONE);
            if (racing != null) {
                racing.setMode(gyroActive ? RacingController.MODE_GYRO : RacingController.MODE_WHEEL);
                racing.show();
            }
            updateDrawerModeDisplay(true);
        } else {
            setArcadeControlsVisibility(View.VISIBLE);
            if (racing != null) racing.hide();
            updateDrawerModeDisplay(false);
        }
        if (controllerTopBar != null) {
            controllerTopBar.bringToFront();
        }
    }

    private void updateDrawerModeDisplay(boolean isRacingActive) {
        if (drawerModeTitle != null) {
            drawerModeTitle.setText(isRacingActive ? "Arcade Mode" : "Racing Mode");
        }
        if (drawerModeSub != null) {
            drawerModeSub.setText(isRacingActive ? "Classic D-Pad & Action Diamonds" : "Steering & Transmission System");
        }
        if (drawerModeSwitchBtn != null) {
            drawerModeSwitchBtn.setText(isRacingActive ? "🕹 SWITCH TO ARCADE MODE" : "🏎 SWITCH TO RACING MODE");
        }
    }

    // Edge Swipe Gesture Listener (Right-to-Left opens drawer, Left-to-Right closes drawer)
    private float touchDownX = 0f;
    private float touchDownY = 0f;
    private boolean isSwipeCandidate = false;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gamepadRoot != null && gamepadRoot.getVisibility() == View.VISIBLE) {
            float x = ev.getRawX();
            float y = ev.getRawY();
            int screenWidth = getResources().getDisplayMetrics().widthPixels;

            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownX = x;
                    touchDownY = y;
                    // Require swipe to begin at the very edge (within 24dp) so normal gameplay is never disrupted
                    if (!isDrawerOpen && x >= screenWidth - dp(24)) {
                        isSwipeCandidate = true;
                    } else if (isDrawerOpen) {
                        isSwipeCandidate = true;
                    } else {
                        isSwipeCandidate = false;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (isSwipeCandidate) {
                        float dx = x - touchDownX;
                        float dy = y - touchDownY;
                        // Require deliberate horizontal swipe (at least 65dp) to avoid accidental triggers
                        if (!isDrawerOpen && dx < -dp(65) && Math.abs(dx) > Math.abs(dy) * 2f) {
                            isSwipeCandidate = false;
                            openSlideMenu();
                            return true;
                        } else if (isDrawerOpen && dx > dp(65) && Math.abs(dx) > Math.abs(dy) * 2f) {
                            isSwipeCandidate = false;
                            closeSlideMenu();
                            return true;
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isSwipeCandidate = false;
                    break;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // ----------------------------------------------------------------
    // CUSTOMIZATION & EDIT MODE
    // ----------------------------------------------------------------

    private void setEditMode(boolean edit) {
        this.editMode = edit;
        if (edit) {
            state.reset();
        }
        if (customizationDoneBtn != null) {
            customizationDoneBtn.setVisibility(edit ? View.VISIBLE : View.GONE);
        }
        if (editModeBanner != null) {
            editModeBanner.setVisibility(edit ? View.VISIBLE : View.GONE);
        }
        if (racing != null && currentControllerMode == MODE_RACING) {
            racing.setEditMode(edit);
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
    // DISCONNECT
    // ----------------------------------------------------------------

    private void disconnect() {
        if (client        != null) client.stop();
        if (rumbleListener!= null) rumbleListener.stop();
        if (vibrator      != null && vibrator.hasVibrator()) vibrator.cancel();

        state.reset();
        setEditMode(false);
        if (isDrawerOpen) closeSlideMenu();
        if (statusText != null) statusText.setText("Disconnected");
        showHomeScreen();
    }

    // ----------------------------------------------------------------
    // RUMBLE
    // ----------------------------------------------------------------

    private void initVibrator() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.os.VibratorManager vm = (android.os.VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vibrator = vm.getDefaultVibrator();
                }
            }
            if (vibrator == null) {
                vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            }
            boolean exists = (vibrator != null);
            boolean hasVib = exists && vibrator.hasVibrator();
            Log.i("RumbleListener", "Vibrator service initialized: vibrator=" + vibrator + ", hasVibrator=" + hasVib);
        } catch (Exception e) {
            Log.e("RumbleListener", "Error initializing vibrator service: " + e.getMessage(), e);
        }
    }

    private void handleRumble(int largeMotor, int smallMotor) {
        if (vibrator == null) {
            initVibrator();
        }

        boolean vibratorExists = (vibrator != null);
        boolean hasVibrator = vibratorExists && vibrator.hasVibrator();
        boolean hasAmplitude = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibratorExists) {
            try {
                hasAmplitude = vibrator.hasAmplitudeControl();
            } catch (Exception ignored) {}
        }
        int strength = Math.max(0, Math.min(255, Math.max(largeMotor, smallMotor)));
        boolean vibrationRequested = (strength > 0);

        Log.i("RumbleListener", "handleRumble() invoked: "
                + "vibratorExists=" + vibratorExists
                + ", hasVibrator=" + hasVibrator
                + ", hasAmplitudeControl=" + hasAmplitude
                + ", largeMotor=" + largeMotor
                + ", smallMotor=" + smallMotor
                + ", calculatedStrength=" + strength
                + ", vibrationRequested=" + vibrationRequested);

        if (!vibratorExists || !hasVibrator) {
            Log.w("RumbleListener", "handleRumble() skipped: Device does not have vibrator hardware or vibrator service is null.");
            return;
        }

        if (!vibrationRequested) {
            try {
                vibrator.cancel();
                Log.d("RumbleListener", "handleRumble(): Motor strength is 0, vibration cancelled.");
            } catch (Exception e) {
                Log.e("RumbleListener", "Error cancelling vibration: " + e.getMessage(), e);
            }
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int amp = hasAmplitude ? Math.max(1, strength) : VibrationEffect.DEFAULT_AMPLITUDE;
                VibrationEffect effect = VibrationEffect.createOneShot(80, amp);
                vibrator.vibrate(effect);
                Log.i("RumbleListener", "handleRumble(): vibrator.vibrate(VibrationEffect) successfully executed [duration=80ms, amplitude=" + amp + "].");
            } else {
                vibrator.vibrate(80);
                Log.i("RumbleListener", "handleRumble(): vibrator.vibrate(80) successfully executed (pre-Oreo).");
            }
        } catch (Exception e) {
            Log.e("RumbleListener", "Exception in vibrator.vibrate(): " + e.getMessage(), e);
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
                if (currentControllerMode == MODE_ARCADE) {
                    state.lx = x;
                    state.ly = y;
                }
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

        View dpad = findViewById(R.id.dpad);
        if (dpad != null) makeDraggable(dpad, "dpad");

        View abxy = findViewById(R.id.abxy);
        if (abxy != null) makeDraggable(abxy, "abxy");

        TriggerSliderView ltSlider = findViewById(R.id.ltSlider);
        TriggerSliderView rtSlider = findViewById(R.id.rtSlider);

        if (ltSlider != null) {
            ltSlider.setLabel("L2");
            ltSlider.setListener(val -> state.lt = val);
            makeDraggable(ltSlider, "ltSlider");
        }

        if (rtSlider != null) {
            rtSlider.setLabel("R2");
            rtSlider.setListener(val -> state.rt = val);
            makeDraggable(rtSlider, "rtSlider");
        }

        applyTriggerMode(settingsStore.isHardTriggers());

        // ABXY buttons (inside group)
        bindButton(R.id.btnA, v -> state.a = v);
        bindButton(R.id.btnB, v -> state.b = v);
        bindButton(R.id.btnX, v -> state.x = v);
        bindButton(R.id.btnY, v -> state.y = v);

        // D-pad buttons (inside group)
        bindButton(R.id.dUp,    v -> state.dUp    = v);
        bindButton(R.id.dDown,  v -> state.dDown  = v);
        bindButton(R.id.dLeft,  v -> state.dLeft  = v);
        bindButton(R.id.dRight, v -> state.dRight = v);

        // Individual draggable buttons
        bindDraggableButton(R.id.lbBtn,    "lbBtn",    v -> state.lb    = v);
        bindDraggableButton(R.id.rbBtn,    "rbBtn",    v -> state.rb    = v);
        bindDraggableButton(R.id.startBtn, "startBtn", v -> state.start = v);
        bindDraggableButton(R.id.backBtn,  "backBtn",  v -> state.back  = v);
    }

    // ----------------------------------------------------------------
    // POSITION & SIZE MANAGEMENT
    // ----------------------------------------------------------------

    private void applyAllSavedPositions() {
        if (defaults.isEmpty()) return;

        for (String key : defaults.keySet()) {
            int id = getResources().getIdentifier(key, "id", getPackageName());
            if (id == 0) continue;
            View view = findViewById(id);
            if (view != null) applySavedPosition(view, key);
        }

        // Apply saved scales for individual action & dpad buttons
        String[] childKeys = {"btnA", "btnB", "btnX", "btnY", "dUp", "dDown", "dLeft", "dRight"};
        for (String ck : childKeys) {
            int id = getResources().getIdentifier(ck, "id", getPackageName());
            if (id == 0) continue;
            View child = findViewById(id);
            if (child != null) {
                float s = positionStore.loadScale(ck, 1.0f);
                child.setScaleX(s);
                child.setScaleY(s);
            }
        }
    }

    private void applySavedPosition(View v, String key) {
        if (v == null || key == null) return;

        float[] def = defaults.get(key);
        if (def == null) return;

        float[] pos = positionStore.load(key, def[0], def[1]);
        float scale = positionStore.loadScale(key, 1.0f);

        v.post(() -> {
            v.setScaleX(scale);
            v.setScaleY(scale);

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
    // DRAGGABLE & CUSTOMIZABLE BUTTONS
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

    private void bindButton(int id, IntSetter setter) {
        Button btn = findViewById(id);
        if (btn == null) return;

        btn.setOnTouchListener((v, event) -> {
            if (editMode) return false;

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

    private void makeDraggable(View v, String key) {
        if (v == null) return;
        v.setOnTouchListener((view, event) -> {
            if (editMode) {
                return handleDrag(view, event, key);
            }
            return false;
        });
    }

    private float dragStartX;
    private float dragStartY;
    private float viewStartX;
    private float viewStartY;
    private boolean hasMoved = false;

    private boolean handleDrag(View v, MotionEvent event, String key) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartX = event.getX();
                dragStartY = event.getY();
                viewStartX = v.getX();
                viewStartY = v.getY();
                hasMoved = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - dragStartX;
                float dy = event.getY() - dragStartY;
                if (!hasMoved && Math.hypot(dx, dy) > dp(6)) {
                    hasMoved = true;
                }
                float newX = viewStartX + dx;
                float newY = viewStartY + dy;

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
                if (!hasMoved && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    showCustomizationControlDialog(v, key);
                }
                return true;

            default:
                return false;
        }
    }

    private void showCustomizationControlDialog(View v, String key) {
        String title;
        switch (key) {
            case "dpad": title = "D-Pad"; break;
            case "abxy": title = "Action Buttons (△ ◯ ✕ □)"; break;
            case "lbBtn": title = "L1 Shoulder Button"; break;
            case "rbBtn": title = "R1 Shoulder Button"; break;
            case "ltSlider": title = "L2 Trigger Slider"; break;
            case "rtSlider": title = "R2 Trigger Slider"; break;
            case "leftStick": title = "Left Analog Stick"; break;
            case "rightStick": title = "Right Analog Stick"; break;
            case "startBtn": title = "START Button"; break;
            case "backBtn": title = "SELECT Button"; break;
            default: title = key; break;
        }

        float currentScale = positionStore.loadScale(key, 1.0f);
        int pct = Math.round(currentScale * 100);

        List<CharSequence> options = new ArrayList<>();
        options.add("Size: Smaller (-15%) [" + pct + "%]");
        options.add("Size: Larger (+15%) [" + pct + "%]");
        if ("abxy".equals(key)) {
            options.add("Adjust Individual Buttons (△, ◯, ✕, □)...");
        }
        options.add("Reset Size & Position to Default");

        new AlertDialog.Builder(this)
                .setTitle(title + " Customization")
                .setItems(options.toArray(new CharSequence[0]), (dialog, which) -> {
                    if (which == 0) {
                        float newScale = Math.max(0.5f, currentScale - 0.15f);
                        v.setScaleX(newScale);
                        v.setScaleY(newScale);
                        positionStore.saveScale(key, newScale);
                    } else if (which == 1) {
                        float newScale = Math.min(2.0f, currentScale + 0.15f);
                        v.setScaleX(newScale);
                        v.setScaleY(newScale);
                        positionStore.saveScale(key, newScale);
                    } else if ("abxy".equals(key) && which == 2) {
                        showIndividualButtonsDialog();
                    } else {
                        // Reset
                        positionStore.remove(key);
                        v.setScaleX(1.0f);
                        v.setScaleY(1.0f);
                        applySavedPosition(v, key);
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showIndividualButtonsDialog() {
        CharSequence[] buttons = new CharSequence[]{"△ (Y Button)", "◯ (B Button)", "✕ (A Button)", "□ (X Button)"};
        String[] keys = new String[]{"btnY", "btnB", "btnA", "btnX"};
        int[] ids = new int[]{R.id.btnY, R.id.btnB, R.id.btnA, R.id.btnX};

        new AlertDialog.Builder(this)
                .setTitle("Select Button to Resize")
                .setItems(buttons, (dialog, which) -> {
                    String btnKey = keys[which];
                    View btnView = findViewById(ids[which]);
                    if (btnView == null) return;

                    float s = positionStore.loadScale(btnKey, 1.0f);
                    int pct = Math.round(s * 100);
                    CharSequence[] subOptions = new CharSequence[]{
                            "Smaller (-15%) [" + pct + "%]",
                            "Larger (+15%) [" + pct + "%]",
                            "Reset to 100%"
                    };
                    new AlertDialog.Builder(this)
                            .setTitle(buttons[which] + " Size")
                            .setItems(subOptions, (d, w) -> {
                                if (w == 0) {
                                    float newS = Math.max(0.5f, s - 0.15f);
                                    btnView.setScaleX(newS);
                                    btnView.setScaleY(newS);
                                    positionStore.saveScale(btnKey, newS);
                                } else if (w == 1) {
                                    float newS = Math.min(2.0f, s + 0.15f);
                                    btnView.setScaleX(newS);
                                    btnView.setScaleY(newS);
                                    positionStore.saveScale(btnKey, newS);
                                } else {
                                    btnView.setScaleX(1.0f);
                                    btnView.setScaleY(1.0f);
                                    positionStore.remove(btnKey);
                                }
                            })
                            .setNegativeButton("Close", null)
                            .show();
                })
                .setNegativeButton("Back", null)
                .show();
    }

    // ----------------------------------------------------------------
    // LIFECYCLE
    // ----------------------------------------------------------------

    @Override
    protected void onPause() {
        super.onPause();
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
