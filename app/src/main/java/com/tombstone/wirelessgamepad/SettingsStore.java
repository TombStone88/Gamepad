package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists user-facing app settings (currently: trigger style). */
public class SettingsStore {

    private static final String PREFS_NAME = "gamepad_settings";
    private static final String KEY_HARD_TRIGGERS = "hard_triggers";

    private final SharedPreferences prefs;

    public SettingsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** True = LT/RT act as simple on/off buttons. False (default) = pressure-sensitive drag. */
    public boolean isHardTriggers() {
        return prefs.getBoolean(KEY_HARD_TRIGGERS, false);
    }

    public void setHardTriggers(boolean hard) {
        prefs.edit().putBoolean(KEY_HARD_TRIGGERS, hard).apply();
    }
}
