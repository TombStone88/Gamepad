package com.tombstone.wirelessgamepad;

import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private final GamepadState state = new GamepadState();
    private UdpGamepadClient client;
    private RumbleListener rumbleListener;
    private PositionStore positionStore;
    private Vibrator vibrator;
    private float density;

    private boolean editMode = false;
    private float dragOffsetX, dragOffsetY;

    private View connectScreen, gamepadRoot;
    private TextView statusText;

    // key -> default {xDp, yDp}. Positions are top-left corners in a landscape layout.
    // Rough starting layout; the whole point of edit mode is the user can drag these anywhere.
    private final Map<String, float[]> defaults = new HashMap<String, float[]>() {{
        put("leftStick", new float[]{20, 160});
        put("rightStick", new float[]{620, 150});
        put("dpad", new float[]{210, 120});
        put("abxy", new float[]{420, 120});
        put("ltSlider", new float[]{20, 10});
        put("rtSlider", new float[]{720, 10});
        put("lbBtn", new float[]{20, 105});
        put("rbBtn", new float[]{690, 105});
        put("l3Btn", new float[]{100, 300});
        put("r3Btn", new float[]{600, 300});
        put("backBtn", new float[]{270, 10});
        put("homeBtn", new float[]{380, 6});
        put("startBtn", new float[]{460, 10});
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        density = getResources().getDisplayMetrics().density;
        positionStore = new PositionStore(this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        client = new UdpGamepadClient(state);
        rumbleListener = new RumbleListener();

        connectScreen = findViewById(R.id.connectScreen);
        gamepadRoot = findViewById(R.id.gamepadRoot);
        EditText ipInput = findViewById(R.id.ipInput);
        statusText = findViewById(R.id.statusText);
        Button connectBtn = findViewById(R.id.connectBtn);

        setupControls();

        if (connectBtn != null) {
            connectBtn.setOnClickListener(v -> {
                String ip = ipInput != null ? ipInput.getText().toString().trim() : "";
                if (ip.isEmpty()) ip = "192.168.137.1";
                client.start(ip);
                rumbleListener.start(this::handleRumble);
                if (connectScreen != null) connectScreen.setVisibility(View.GONE);
                if (gamepadRoot != null) gamepadRoot.setVisibility(View.VISIBLE);
            });
        }
    }

    private void showHomeMenu() {
        String editLabel = editMode ? "Done editing layout" : "Edit layout";
        new AlertDialog.Builder(this)
                .setTitle("Menu")
                .setItems(new CharSequence[]{editLabel, "Disconnect", "Cancel"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            editMode = !editMode;
                            break;
                        case 1:
                            disconnect();
                            break;
                        default:
                            // Cancel — do nothing
                    }
                })
                .show();
    }

    private void disconnect() {
        client.stop();
        rumbleListener.stop();
        gamepadRoot.setVisibility(View.GONE);
        connectScreen.setVisibility(View.VISIBLE);
        statusText.setText("Disconnected");
    }

    private void handleRumble(int largeMotor, int smallMotor) {
        int strength = Math.max(largeMotor, smallMotor);
        if (strength <= 0 || vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int amplitude = Math.max(1, Math.min(255, strength));
            vibrator.vibrate(VibrationEffect.createOneShot(80, amplitude));
        } else {
            vibrator.vibrate(80);
        }
    }

    private void setupControls() {
        JoystickView leftStick = findViewById(R.id.leftStick);
        JoystickView rightStick = findViewById(R.id.rightStick);
        if (leftStick != null) {
            leftStick.setListener((x, y) -> { state.lx = x; state.ly = y; });
            makeDraggable(leftStick, "leftStick");
        }
        if (rightStick != null) {
            rightStick.setListener((x, y) -> { state.rx = x; state.ry = y; });
            makeDraggable(rightStick, "rightStick");
        }

        View dpad = findViewById(R.id.dpad);
        View abxy = findViewById(R.id.abxy);
        makeDraggable(dpad, "dpad");
        makeDraggable(abxy, "abxy");

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

        bindButton(R.id.btnA, v -> state.a = v);
        bindButton(R.id.btnB, v -> state.b = v);
        bindButton(R.id.btnX, v -> state.x = v);
        bindButton(R.id.btnY, v -> state.y = v);
        bindButton(R.id.dUp, v -> state.dUp = v);
        bindButton(R.id.dDown, v -> state.dDown = v);
        bindButton(R.id.dLeft, v -> state.dLeft = v);
        bindButton(R.id.dRight, v -> state.dRight = v);

        bindDraggableButton(R.id.lbBtn, "lbBtn", v -> state.lb = v);
        bindDraggableButton(R.id.rbBtn, "rbBtn", v -> state.rb = v);
        bindDraggableButton(R.id.l3Btn, "l3Btn", v -> state.l3 = v);
        bindDraggableButton(R.id.r3Btn, "r3Btn", v -> state.r3 = v);
        bindDraggableButton(R.id.startBtn, "startBtn", v -> state.start = v);
        bindDraggableButton(R.id.backBtn, "backBtn", v -> state.back = v);

        // Home button: no gamepad signal — opens the menu (edit layout / disconnect) instead.
        Button homeBtn = findViewById(R.id.homeBtn);
        if (homeBtn != null) {
            applySavedPosition(homeBtn, "homeBtn");
            homeBtn.setOnTouchListener((v, event) -> {
                if (editMode) return handleDrag(v, event, "homeBtn");
                return false; // let the normal click listener below handle it
            });
            homeBtn.setOnClickListener(v -> showHomeMenu());
        }

        // Apply saved (or default) positions once views are laid out and have real sizes.
        for (String key : defaults.keySet()) {
            View v = findViewById(getResources().getIdentifier(key, "id", getPackageName()));
            if (v != null) applySavedPosition(v, key);
        }
    }

    private void applySavedPosition(View v, String key) {
        if (v == null || key == null) return;
        float[] def = defaults.get(key);
        if (def == null) return;
        float[] pos = positionStore.load(key, def[0], def[1]);
        v.post(() -> {
            v.setX(pos[0] * density);
            v.setY(pos[1] * density);
        });
    }

    /** Buttons whose action is a press/release AND can be dragged in edit mode. */
    private void bindDraggableButton(int id, String key, IntSetter setter) {
        Button btn = findViewById(id);
        if (btn == null) return;
        applySavedPosition(btn, key);
        btn.setOnTouchListener((v, event) -> {
            if (editMode) return handleDrag(v, event, key);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    setter.set(1);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    setter.set(0);
                    v.performClick();
                    return true;
            }
            return false;
        });
    }

    /** Simple press/release buttons that live inside a draggable group (dpad/abxy) — not individually draggable. */
    private void bindButton(int id, IntSetter setter) {
        Button btn = findViewById(id);
        if (btn == null) return;
        btn.setOnTouchListener((v, event) -> {
            if (editMode) return true; // swallow touches on children while parent group is being dragged
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    setter.set(1);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    setter.set(0);
                    v.performClick();
                    return true;
            }
            return false;
        });
    }

    /** Views with their own internal touch handling (joysticks, trigger sliders, groups) that also need to be draggable. */
    private void makeDraggable(View v, String key) {
        if (v == null) return;
        v.setOnTouchListener((view, event) -> {
            if (editMode) return handleDrag(view, event, key);
            return false; // let the view's own onTouchEvent (joystick drag, etc.) run normally
        });
    }

    private boolean handleDrag(View v, MotionEvent event, String key) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragOffsetX = v.getX() - event.getRawX();
                dragOffsetY = v.getY() - event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                v.setX(event.getRawX() + dragOffsetX);
                v.setY(event.getRawY() + dragOffsetY);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                positionStore.save(key, v.getX() / density, v.getY() / density);
                return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (client != null) client.stop();
        if (rumbleListener != null) rumbleListener.stop();
    }

    interface IntSetter { void set(int value); }
}
