package com.tombstone.wirelessgamepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Racing mode controller matching the RetroPad / PocketPad specifications in wheel.png & gyro.png.
 * Writes into the shared GamepadState:
 *   steering     -> left stick X (rotary wheel, or phone gyro tilt)
 *   gas          -> right trigger (metallic fluted throttle)
 *   brake        -> left trigger (hydraulic ribbed pedal)
 *   shift down   -> left bumper LB
 *   shift up     -> right bumper RB
 *   emergency    -> A button (e-brake drift lock)
 *   aux buttons  -> nitro (B), horn (L3), camera (Y), lights (X)
 */
public class RacingController {

    public static final int MODE_WHEEL = 0;
    public static final int MODE_GYRO = 1;
    private static final String[] MODE_TAG = {"w", "g"};

    private static final CharSequence[] BTN_LABELS = {
            "A", "B", "X", "Y", "LB", "RB", "L3 (left stick press)", "R3 (right stick press)",
            "Start", "Back", "D-pad Up", "D-pad Down", "D-pad Left", "D-pad Right"
    };
    private static final CharSequence[] GYRO_LABELS = {
            "High sensitivity (25° to full lock)",
            "Medium (35°)",
            "Low (45°)",
            "Very low (60°)"
    };
    private static final int[] GYRO_RANGES = {25, 35, 45, 60};
    private static final float GYRO_DEADZONE = 2f;

    private static final CharSequence[] ROT_LABELS = {
            "180° each side (arcade)",
            "270° each side (default)",
            "360° each side (1 full turn)",
            "540° each side (1.5 turns)"
    };
    private static final int[] ROT_VALUES = {180, 270, 360, 540};

    private final Activity host;
    private final GamepadState state;
    private final View root;
    private final SharedPreferences prefs;
    private final SensorManager sensorManager;
    private final Sensor tiltSensor;
    private final float density;

    private FrameLayout stage;
    private SteeringWheelView wheelView;
    private SteerBarView steerBar;
    private TransmissionBoxView transmissionBox;
    private TextView steerAxisText;
    private View calibrateBtn;
    private final List<Control> controls = new ArrayList<>();

    private int mode;
    private int gyroIndex;
    private int rotIndex;
    private boolean editMode = false;
    private boolean externalGyroActive = false;

    private boolean sensorRunning = false;
    private boolean calibrate = true;
    private float tiltOffset = 0f;
    private float tiltFiltered = 0f;

    /** One movable, resizable thing on the racing stage. */
    private final class Control {
        final String key;
        final String title;
        final View view;
        final float baseW, baseH; // dp at scale 1
        final float[][] defs = new float[2][];   // per mode: {fx, fy, scale}
        final boolean[] visible = new boolean[2];
        boolean isButton;
        int map;
        int defMap;
        boolean toggle;

        Control(String key, String title, View view, float bw, float bh) {
            this.key = key;
            this.title = title;
            this.view = view;
            this.baseW = bw;
            this.baseH = bh;
        }
    }

    public RacingController(Activity host, GamepadState state, View root) {
        this.host = host;
        this.state = state;
        this.root = root;
        this.density = host.getResources().getDisplayMetrics().density;
        this.prefs = host.getSharedPreferences("racing_prefs", Context.MODE_PRIVATE);

        int layoutVer = prefs.getInt("layout_version", 0);
        if (layoutVer < 5) {
            prefs.edit().clear().putInt("layout_version", 5).apply();
        }

        mode = prefs.getInt("mode", MODE_WHEEL);
        gyroIndex = prefs.getInt("tilt", 1);
        rotIndex = prefs.getInt("rot", 1);

        sensorManager = (SensorManager) host.getSystemService(Context.SENSOR_SERVICE);
        tiltSensor = resolveSensor();

        if (root == null) return;
        stage = root.findViewById(R.id.racingStage);
        steerAxisText = root.findViewById(R.id.racingSteerAxisText);
        steerBar = root.findViewById(R.id.racingSteerBar);
        calibrateBtn = root.findViewById(R.id.racingCalibrateBtn);

        if (calibrateBtn != null) {
            calibrateBtn.setOnClickListener(v -> calibrate());
        }

        if (stage == null) return;
        buildControls();
        stage.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if ((r - l) != (or - ol) || (b - t) != (ob - ot)) layoutAll();
        });
        registerLifecycle();
    }

    // ----------------------------------------------------------------
    // Public API (called from MainActivity)
    // ----------------------------------------------------------------

    public void show() {
        if (root == null || stage == null) return;
        state.reset();
        root.setVisibility(View.VISIBLE);
        applyMode();
    }

    public void hide() {
        if (root == null || stage == null) return;
        if (!externalGyroActive) stopSensor();
        setEditMode(false);
        if (root.getVisibility() == View.VISIBLE) {
            root.setVisibility(View.GONE);
            state.reset();
            resetButtons();
        }
    }

    public boolean isShowing() {
        return root != null && root.getVisibility() == View.VISIBLE;
    }

    public void setMode(int newMode) {
        mode = newMode;
        prefs.edit().putInt("mode", mode).apply();
        applyMode();
    }

    public int getMode() {
        return mode;
    }

    public void setExternalGyroActive(boolean active) {
        this.externalGyroActive = active;
        setMode(active ? MODE_GYRO : MODE_WHEEL);
    }

    public boolean isGyroActive() {
        return (mode == MODE_GYRO);
    }

    public void calibrate() {
        calibrate = true;
        tiltOffset = 0f;
        tiltFiltered = 0f;
        state.lx = 0f;
        if (wheelView != null) wheelView.setSteer(0f);
        if (steerBar != null) steerBar.setValue(0f);
        updateSteerAngleDisplay(0f);
        Toast.makeText(host, "Steering centered", Toast.LENGTH_SHORT).show();
    }

    public void setEditMode(boolean edit) {
        if (this.editMode == edit) return;
        this.editMode = edit;
        for (Control c : controls) {
            updateControlBorder(c);
        }
    }

    public boolean isEditMode() {
        return editMode;
    }

    // ----------------------------------------------------------------
    // Build the controls
    // ----------------------------------------------------------------

    private static float[] d(float x, float y, float s) {
        return new float[]{x, y, s};
    }

    private void buildControls() {
        // Steering wheel: touchable in Wheel mode unless gyro is active
        wheelView = new SteeringWheelView(host, null);
        wheelView.setListener(v -> {
            if (mode == MODE_WHEEL && !isGyroActive()) {
                state.lx = v;
                updateSteerAngleDisplay(v * ROT_VALUES[rotIndex]);
                if (steerBar != null) steerBar.setValue(v);
            }
        });
        wheelView.setMaxDegrees(ROT_VALUES[rotIndex]);
        add("wheel", "Steering wheel", wheelView, 220, 220,
                d(0.04f, 0.55f, 1f), d(0.04f, 0.55f, 1f), true, false);

        // Transmission box (Shift Down, Shift Up, E-Brake)
        transmissionBox = new TransmissionBoxView(host);
        transmissionBox.setListener(new TransmissionBoxView.Listener() {
            @Override
            public void onShiftDown(boolean pressed) {
                state.lb = pressed ? 1 : 0;
            }

            @Override
            public void onShiftUp(boolean pressed) {
                state.rb = pressed ? 1 : 0;
            }

            @Override
            public void onEBrake(boolean pressed) {
                state.a = pressed ? 1 : 0;
            }
        });
        add("transmission", "Transmission box", transmissionBox, 185, 170,
                d(0.48f, 0.55f, 1f), d(0.50f, 0.55f, 1.05f), true, true);

        // Hydraulic Brake Pedal (Red) - Right in Wheel mode, LEFT in Gyro mode
        PedalView brake = new PedalView(host, null);
        brake.setPedalType(PedalView.TYPE_BRAKE);
        brake.setListener(v -> state.lt = v);
        brake.setContentDescription("Brake pedal");
        add("brake", "Brake pedal", brake, 100, 180,
                d(0.74f, 0.88f, 1f), d(0.04f, 0.70f, 1.4f), true, true);

        // Anodized Fluted Gas Pedal (Metallic) - Far Right in Wheel mode, RIGHT in Gyro mode
        PedalView gas = new PedalView(host, null);
        gas.setPedalType(PedalView.TYPE_GAS);
        gas.setListener(v -> state.rt = v);
        gas.setContentDescription("Gas pedal");
        add("gas", "Gas pedal", gas, 105, 195,
                d(0.96f, 0.88f, 1f), d(0.96f, 0.70f, 1.4f), true, true);
    }

    private Control add(String key, String title, View v, float bw, float bh,
                        float[] wheelDef, float[] gyroDef, boolean inWheel, boolean inGyro) {
        Control c = new Control(key, title, v, bw, bh);
        c.defs[MODE_WHEEL] = wheelDef;
        c.defs[MODE_GYRO] = gyroDef;
        c.visible[MODE_WHEEL] = inWheel;
        c.visible[MODE_GYRO] = inGyro;
        stage.addView(v, new FrameLayout.LayoutParams(
                Math.round(bw * density), Math.round(bh * density), Gravity.TOP | Gravity.START));
        attachEditTouch(c);
        controls.add(c);
        return c;
    }

    private void addButton(String key, String title, int icon, float size, int defMap,
                           float[] wheelDef, float[] gyroDef) {
        RacingButtonView bv = new RacingButtonView(host, null);
        bv.setIcon(icon);
        bv.setContentDescription(title);
        final Control c = add(key, title, bv, size, size, wheelDef, gyroDef, true, true);
        c.isButton = true;
        c.defMap = defMap;
        c.map = prefs.getInt("map_" + key, defMap);
        c.toggle = prefs.getBoolean("toggle_" + key, false);
        bv.setToggleMode(c.toggle);
        bv.setListener(active -> setBtn(c.map, active ? 1 : 0));
    }

    private void setBtn(int idx, int v) {
        switch (idx) {
            case 0: state.a = v; break;
            case 1: state.b = v; break;
            case 2: state.x = v; break;
            case 3: state.y = v; break;
            case 4: state.lb = v; break;
            case 5: state.rb = v; break;
            case 6: state.l3 = v; break;
            case 7: state.r3 = v; break;
            case 8: state.start = v; break;
            case 9: state.back = v; break;
            case 10: state.dUp = v; break;
            case 11: state.dDown = v; break;
            case 12: state.dLeft = v; break;
            case 13: state.dRight = v; break;
        }
    }

    private void resetButtons() {
        for (Control c : controls) {
            if (c.view instanceof RacingButtonView) ((RacingButtonView) c.view).reset();
        }
    }

    private void updateSteerAngleDisplay(float deg) {
        if (steerAxisText == null) return;
        boolean isGyro = (mode == MODE_GYRO);
        String prefix = isGyro ? "GYRO STEER AXIS: " : "STEERING AXIS: ";
        host.runOnUiThread(() -> {
            if (Math.abs(deg) < 1.0f) {
                steerAxisText.setText(prefix + "0.0° CENTER");
            } else if (deg > 0) {
                steerAxisText.setText(String.format(Locale.US, "%s%.1f° RIGHT", prefix, deg));
            } else {
                steerAxisText.setText(String.format(Locale.US, "%s%.1f° LEFT", prefix, -deg));
            }
        });
    }

    private Sensor resolveSensor() {
        if (sensorManager == null) return null;
        Sensor s = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (s == null) s = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (s == null) s = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (s == null) s = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        return s;
    }

    private void applyMode() {
        boolean isGyro = (mode == MODE_GYRO);

        state.reset();
        resetButtons();
        if (wheelView != null) {
            wheelView.setReadOnly(isGyro);
            wheelView.setSteer(0f);
        }
        if (steerBar != null) steerBar.setValue(0f);
        updateSteerAngleDisplay(0f);

        if (calibrateBtn != null) {
            calibrateBtn.setVisibility(isGyro ? View.VISIBLE : View.GONE);
        }

        for (Control c : controls) {
            c.view.setVisibility(c.visible[mode] ? View.VISIBLE : View.GONE);
        }

        tiltFiltered = 0f;
        layoutAll();
        if (stage != null) {
            stage.post(() -> layoutAll());
        }
        if (isGyro) {
            calibrate = true;
            startSensor();
        } else {
            stopSensor();
        }
    }

    // ----------------------------------------------------------------
    // Layout: positions are stored per mode as fractions of the free space
    // ----------------------------------------------------------------

    private int dp(float v) {
        return Math.round(v * density);
    }

    private float[] loadGeom(Control c) {
        float[] def = c.defs[mode];
        String p = "pos_" + MODE_TAG[mode] + "_" + c.key;
        return new float[]{
                prefs.getFloat(p + "_x", def[0]),
                prefs.getFloat(p + "_y", def[1]),
                prefs.getFloat(p + "_s", def[2])};
    }

    private void saveGeom(Control c, float fx, float fy, float s) {
        String p = "pos_" + MODE_TAG[mode] + "_" + c.key;
        prefs.edit().putFloat(p + "_x", fx).putFloat(p + "_y", fy).putFloat(p + "_s", s).apply();
    }

    private void layoutAll() {
        for (Control c : controls) layoutControl(c);
    }

    private void layoutControl(Control c) {
        int W = stage.getWidth();
        int H = stage.getHeight();
        if (W <= 0 || H <= 0) return;

        float[] g = loadGeom(c);
        float s = g[2];
        int w = Math.round(c.baseW * density * s);
        int h = Math.round(c.baseH * density * s);

        ViewGroup.LayoutParams lp = c.view.getLayoutParams();
        if (lp.width != w || lp.height != h) {
            lp.width = w;
            lp.height = h;
            c.view.setLayoutParams(lp);
        }

        int maxLeft = Math.max(0, W - w);
        int maxTop = Math.max(0, H - h);
        c.view.setX(Math.round(g[0] * maxLeft));
        c.view.setY(Math.round(g[1] * maxTop));
    }

    // ----------------------------------------------------------------
    // Edit mode (drag, resize, remap)
    // ----------------------------------------------------------------

    private void updateControlBorder(Control c) {
        if (!editMode) {
            c.view.setForeground(null);
            return;
        }
        GradientDrawable gd = new GradientDrawable();
        gd.setStroke(dp(1.5f), 0x99FF6B1A);
        gd.setColor(0x22FF6B1A);
        c.view.setForeground(gd);
    }

    private void attachEditTouch(final Control c) {
        c.view.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX, downRawY;
            private float viewStartX, viewStartY;
            private boolean moved = false;
            private int touchSlop = ViewConfiguration.get(host).getScaledTouchSlop();

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!editMode) return false;

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        viewStartX = v.getX();
                        viewStartY = v.getY();
                        moved = false;
                        if (stage != null) stage.requestDisallowInterceptTouchEvent(true);
                        return true;

                    case MotionEvent.ACTION_MOVE: {
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (!moved && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            moved = true;
                        }
                        int maxLeft = Math.max(0, stage.getWidth() - v.getWidth());
                        int maxTop = Math.max(0, stage.getHeight() - v.getHeight());
                        float nx = Math.max(0, Math.min(maxLeft, viewStartX + dx));
                        float ny = Math.max(0, Math.min(maxTop, viewStartY + dy));
                        v.setX(nx);
                        v.setY(ny);
                        return true;
                    }

                    case MotionEvent.ACTION_UP: {
                        if (stage != null) stage.requestDisallowInterceptTouchEvent(false);
                        int maxLeft = Math.max(0, stage.getWidth() - v.getWidth());
                        int maxTop = Math.max(0, stage.getHeight() - v.getHeight());
                        float fx = maxLeft > 0 ? v.getX() / (float) maxLeft : 0f;
                        float fy = maxTop > 0 ? v.getY() / (float) maxTop : 0f;
                        float s = loadGeom(c)[2];
                        saveGeom(c, fx, fy, s);
                        if (!moved) showControlDialog(c);
                        return true;
                    }

                    case MotionEvent.ACTION_CANCEL:
                        if (stage != null) stage.requestDisallowInterceptTouchEvent(false);
                        return true;
                }
                return false;
            }
        });
    }

    private void showControlDialog(final Control c) {
        List<CharSequence> items = new ArrayList<>();
        items.add("Size: Smaller (-15%)");
        items.add("Size: Larger (+15%)");
        if (c.isButton) {
            items.add("Sends: " + BTN_LABELS[c.map]);
            items.add("Mode: " + (c.toggle ? "Toggle (tap on/off)" : "Hold (press)"));
        }
        final int resetIdx = items.size();
        items.add("Reset this control");

        new AlertDialog.Builder(host)
                .setTitle(c.title)
                .setItems(items.toArray(new CharSequence[0]), (dlg, which) -> {
                    if (which == 0) {
                        resizeControl(c, -0.15f);
                    } else if (which == 1) {
                        resizeControl(c, 0.15f);
                    } else if (c.isButton && which == 2) {
                        showAssignDialog(c);
                    } else if (c.isButton && which == 3) {
                        setBtn(c.map, 0);
                        c.toggle = !c.toggle;
                        prefs.edit().putBoolean("toggle_" + c.key, c.toggle).apply();
                        ((RacingButtonView) c.view).setToggleMode(c.toggle);
                        showControlDialog(c);
                    } else if (which == resetIdx) {
                        resetControl(c);
                    }
                })
                .show();
    }

    private void resizeControl(Control c, float delta) {
        float[] g = loadGeom(c);
        float s = Math.max(0.6f, Math.min(2.0f, g[2] + delta));
        saveGeom(c, g[0], g[1], s);
        layoutControl(c);
    }

    private void resetControl(Control c) {
        String p = "pos_" + MODE_TAG[mode] + "_" + c.key;
        prefs.edit().remove(p + "_x").remove(p + "_y").remove(p + "_s").apply();
        layoutControl(c);
    }

    private void showAssignDialog(final Control c) {
        new AlertDialog.Builder(host)
                .setTitle(c.title + " sends")
                .setSingleChoiceItems(BTN_LABELS, c.map, (dlg, which) -> {
                    setBtn(c.map, 0);
                    c.map = which;
                    prefs.edit().putInt("map_" + c.key, which).apply();
                    dlg.dismiss();
                    showControlDialog(c);
                })
                .setNegativeButton("Back", (dlg, w) -> showControlDialog(c))
                .show();
    }

    // ----------------------------------------------------------------
    // Gyro / tilt steering
    // ----------------------------------------------------------------

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mode != MODE_GYRO) return;
            float ax = event.values[0];
            float ay = event.values[1];

            // Rotate the sensor axes into the screen's own axes
            float sx;
            float sy;
            switch (displayRotation()) {
                case Surface.ROTATION_90:
                    sx = -ay;
                    sy = ax;
                    break;
                case Surface.ROTATION_180:
                    sx = -ax;
                    sy = -ay;
                    break;
                case Surface.ROTATION_270:
                    sx = ay;
                    sy = -ax;
                    break;
                default:
                    sx = ax;
                    sy = ay;
                    break;
            }

            if (Math.hypot(sx, sy) < 1.0f) return;

            // Clockwise tilt is positive
            float angle = (float) Math.toDegrees(Math.atan2(-sx, sy));
            if (calibrate) {
                tiltOffset = angle;
                tiltFiltered = 0f;
                calibrate = false;
            }

            float rel = angle - tiltOffset;
            while (rel > 180f) rel -= 360f;
            while (rel <= -180f) rel += 360f;

            tiltFiltered += 0.35f * (rel - tiltFiltered);

            float range = GYRO_RANGES[gyroIndex];
            float mag = Math.abs(tiltFiltered);
            float v = mag < GYRO_DEADZONE
                    ? 0f
                    : Math.min(1f, (mag - GYRO_DEADZONE) / (range - GYRO_DEADZONE));
            if (tiltFiltered < 0f) v = -v;

            state.lx = v;
            updateSteerAngleDisplay(tiltFiltered);
            if (steerBar != null) steerBar.setValue(v);
            if (wheelView != null) wheelView.setSteer(v);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    @SuppressWarnings("deprecation")
    private int displayRotation() {
        return host.getWindowManager().getDefaultDisplay().getRotation();
    }

    private void startSensor() {
        if (sensorRunning || sensorManager == null) return;
        Sensor s = (tiltSensor != null) ? tiltSensor : resolveSensor();
        if (s == null) return;
        sensorManager.registerListener(sensorListener, s, SensorManager.SENSOR_DELAY_GAME);
        sensorRunning = true;
    }

    private void stopSensor() {
        if (!sensorRunning || sensorManager == null) return;
        sensorManager.unregisterListener(sensorListener);
        sensorRunning = false;
    }

    private void registerLifecycle() {
        host.getApplication().registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityPaused(Activity a) {
                        if (a == host) {
                            stopSensor();
                            resetButtons();
                        }
                    }

                    @Override
                    public void onActivityResumed(Activity a) {
                        if (a == host && (isGyroActive() || (root != null && root.getVisibility() == View.VISIBLE && mode == MODE_GYRO))) {
                            calibrate = true;
                            startSensor();
                        }
                    }

                    @Override
                    public void onActivityDestroyed(Activity a) {
                        if (a == host) {
                            stopSensor();
                            host.getApplication().unregisterActivityLifecycleCallbacks(this);
                        }
                    }

                    @Override public void onActivityCreated(Activity a, Bundle b) { }
                    @Override public void onActivityStarted(Activity a) { }
                    @Override public void onActivityStopped(Activity a) { }
                    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                });
    }
}
