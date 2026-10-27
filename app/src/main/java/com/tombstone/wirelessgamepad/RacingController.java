package com.tombstone.wirelessgamepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

/**
 * Racing mode. Sits on top of the normal gamepad screen and writes into the SAME
 * GamepadState the rest of the app already sends, so nothing on the PC side changes:
 *
 *   steering  -> left stick X   (touch wheel, or phone tilt)
 *   gas       -> right trigger
 *   brake     -> left trigger
 *   gear up / gear down -> configurable buttons (default RB / LB)
 */
public class RacingController {

    private static final int MODE_WHEEL = 0;
    private static final int MODE_TILT = 1;

    private static final CharSequence[] GEAR_LABELS = {
            "RB (up) / LB (down)",
            "A (up) / X (down)",
            "B (up) / X (down)"
    };
    private static final CharSequence[] TILT_LABELS = {
            "High sensitivity (25° to full lock)",
            "Medium (35°)",
            "Low (45°)",
            "Very low (60°)"
    };
    private static final int[] TILT_RANGES = {25, 35, 45, 60};
    private static final float TILT_DEADZONE = 2f;

    private final Activity host;
    private final GamepadState state;
    private final View root;
    private final SharedPreferences prefs;
    private final SensorManager sensorManager;
    private final Sensor tiltSensor;

    private View wheelLayout;
    private View tiltLayout;
    private Button modeBtn;
    private Button recenterBtn;
    private SteerBarView steerBar;

    private int mode;
    private int gearPreset;
    private int tiltIndex;

    private boolean sensorRunning = false;
    private boolean calibrate = true;
    private float tiltOffset = 0f;
    private float tiltFiltered = 0f;

    public RacingController(Activity host, GamepadState state, View root) {
        this.host = host;
        this.state = state;
        this.root = root;
        this.prefs = host.getSharedPreferences("racing_prefs", Context.MODE_PRIVATE);

        mode = prefs.getInt("mode", MODE_WHEEL);
        gearPreset = prefs.getInt("gear", 0);
        tiltIndex = prefs.getInt("tilt", 1);

        sensorManager = (SensorManager) host.getSystemService(Context.SENSOR_SERVICE);
        Sensor s = null;
        if (sensorManager != null) {
            s = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (s == null) s = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        tiltSensor = s;

        if (root == null) return;
        bindViews();
        registerLifecycle();
    }

    // ----------------------------------------------------------------
    // Public API (called from MainActivity)
    // ----------------------------------------------------------------

    public void show() {
        if (root == null) return;
        state.reset();
        root.setVisibility(View.VISIBLE);
        applyMode();
    }

    public void hide() {
        if (root == null) return;
        stopSensor();
        if (root.getVisibility() == View.VISIBLE) {
            root.setVisibility(View.GONE);
            state.reset();
        }
    }

    // ----------------------------------------------------------------
    // Wiring
    // ----------------------------------------------------------------

    private void bindViews() {
        wheelLayout = root.findViewById(R.id.racingWheelLayout);
        tiltLayout = root.findViewById(R.id.racingTiltLayout);
        modeBtn = root.findViewById(R.id.racingModeBtn);
        recenterBtn = root.findViewById(R.id.racingRecenterBtn);
        steerBar = root.findViewById(R.id.racingSteerBar);

        int gasColor = ContextCompat.getColor(host, R.color.racing_gas);
        int brakeColor = ContextCompat.getColor(host, R.color.racing_brake);
        bindPedal(R.id.wheelGas, "GAS", gasColor, true);
        bindPedal(R.id.wheelBrake, "BRAKE", brakeColor, false);
        bindPedal(R.id.tiltGas, "GAS", gasColor, true);
        bindPedal(R.id.tiltBrake, "BRAKE", brakeColor, false);

        SteeringWheelView wheel = root.findViewById(R.id.racingWheel);
        if (wheel != null) wheel.setListener(v -> state.lx = v);

        bindGear((Button) root.findViewById(R.id.racingGearUp), true);
        bindGear((Button) root.findViewById(R.id.racingGearDown), false);

        View back = root.findViewById(R.id.racingBackBtn);
        if (back != null) back.setOnClickListener(v -> hide());

        if (modeBtn != null) {
            modeBtn.setOnClickListener(v -> {
                int next = (mode == MODE_WHEEL) ? MODE_TILT : MODE_WHEEL;
                if (next == MODE_TILT && tiltSensor == null) {
                    Toast.makeText(host, "This phone has no tilt sensor. Use Wheel mode.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                mode = next;
                prefs.edit().putInt("mode", mode).apply();
                applyMode();
            });
        }

        if (recenterBtn != null) {
            recenterBtn.setOnClickListener(v -> calibrate = true);
        }

        View options = root.findViewById(R.id.racingSettingsBtn);
        if (options != null) options.setOnClickListener(v -> showOptions());
    }

    private void bindPedal(int id, String label, int color, boolean isGas) {
        PedalView pedal = root.findViewById(id);
        if (pedal == null) return;
        pedal.setLabelAndColor(label, color);
        if (isGas) {
            pedal.setListener(v -> state.rt = v);
        } else {
            pedal.setListener(v -> state.lt = v);
        }
    }

    private void bindGear(Button btn, boolean up) {
        if (btn == null) return;
        btn.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    setGear(up, 1);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    setGear(up, 0);
                    return true;
            }
            return true;
        });
    }

    private void setGear(boolean up, int value) {
        switch (gearPreset) {
            case 1:
                if (up) state.a = value; else state.x = value;
                break;
            case 2:
                if (up) state.b = value; else state.x = value;
                break;
            default:
                if (up) state.rb = value; else state.lb = value;
                break;
        }
    }

    private void applyMode() {
        if (mode == MODE_TILT && tiltSensor == null) mode = MODE_WHEEL;
        boolean tilt = (mode == MODE_TILT);

        if (wheelLayout != null) wheelLayout.setVisibility(tilt ? View.GONE : View.VISIBLE);
        if (tiltLayout != null) tiltLayout.setVisibility(tilt ? View.VISIBLE : View.GONE);
        if (recenterBtn != null) recenterBtn.setVisibility(tilt ? View.VISIBLE : View.GONE);
        if (modeBtn != null) modeBtn.setText(tilt ? "Mode: Tilt" : "Mode: Wheel");
        if (steerBar != null) steerBar.setValue(0f);

        state.reset();
        tiltFiltered = 0f;
        if (tilt) {
            calibrate = true;
            startSensor();
        } else {
            stopSensor();
        }
    }

    // ----------------------------------------------------------------
    // Options dialogs
    // ----------------------------------------------------------------

    private void showOptions() {
        new AlertDialog.Builder(host)
                .setTitle("Racing options")
                .setItems(new CharSequence[]{"Gear buttons", "Tilt sensitivity", "Close"},
                        (d, which) -> {
                            if (which == 0) showGearOptions();
                            else if (which == 1) showTiltOptions();
                        })
                .show();
    }

    private void showGearOptions() {
        new AlertDialog.Builder(host)
                .setTitle("Gear buttons send")
                .setSingleChoiceItems(GEAR_LABELS, gearPreset, (d, which) -> {
                    state.reset();
                    gearPreset = which;
                    prefs.edit().putInt("gear", which).apply();
                    d.dismiss();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showTiltOptions() {
        new AlertDialog.Builder(host)
                .setTitle("Tilt sensitivity")
                .setSingleChoiceItems(TILT_LABELS, tiltIndex, (d, which) -> {
                    tiltIndex = which;
                    prefs.edit().putInt("tilt", which).apply();
                    d.dismiss();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // ----------------------------------------------------------------
    // Tilt steering
    // ----------------------------------------------------------------

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float ax = event.values[0];
            float ay = event.values[1];

            // Rotate the sensor axes into the screen's own axes (X right, Y up).
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

            // Phone lying flat: roll angle is undefined, keep the last value.
            if (Math.hypot(sx, sy) < 3f) return;

            // Clockwise tilt (steer right) is positive.
            float angle = (float) Math.toDegrees(Math.atan2(-sx, sy));
            if (calibrate) {
                tiltOffset = angle;
                tiltFiltered = 0f;
                calibrate = false;
            }

            float rel = angle - tiltOffset;
            while (rel > 180f) rel -= 360f;
            while (rel <= -180f) rel += 360f;

            tiltFiltered += 0.3f * (rel - tiltFiltered);

            float range = TILT_RANGES[tiltIndex];
            float mag = Math.abs(tiltFiltered);
            float v = mag < TILT_DEADZONE
                    ? 0f
                    : Math.min(1f, (mag - TILT_DEADZONE) / (range - TILT_DEADZONE));
            if (tiltFiltered < 0f) v = -v;

            state.lx = v;
            if (steerBar != null) steerBar.setValue(v);
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
        if (sensorRunning || sensorManager == null || tiltSensor == null) return;
        sensorManager.registerListener(sensorListener, tiltSensor, SensorManager.SENSOR_DELAY_GAME);
        sensorRunning = true;
    }

    private void stopSensor() {
        if (!sensorRunning || sensorManager == null) return;
        sensorManager.unregisterListener(sensorListener);
        sensorRunning = false;
    }

    // ----------------------------------------------------------------
    // Pause the sensor with the activity (no change needed in MainActivity)
    // ----------------------------------------------------------------

    private void registerLifecycle() {
        host.getApplication().registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityPaused(Activity a) {
                        if (a == host) stopSensor();
                    }

                    @Override
                    public void onActivityResumed(Activity a) {
                        if (a == host && root.getVisibility() == View.VISIBLE && mode == MODE_TILT) {
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
