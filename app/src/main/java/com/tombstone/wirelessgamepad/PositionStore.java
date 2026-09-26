package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists each control's top-left position (in dp) so custom layouts survive app restarts. */
public class PositionStore {

    private static final String PREFS_NAME = "gamepad_positions";
    private final SharedPreferences prefs;

    public PositionStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void save(String key, float xDp, float yDp) {
        prefs.edit()
                .putFloat(key + "_x", xDp)
                .putFloat(key + "_y", yDp)
                .apply();
    }

    /** Returns {x, y} in dp — the saved position, or the given defaults if none saved yet. */
    public float[] load(String key, float defaultXDp, float defaultYDp) {
        float x = prefs.getFloat(key + "_x", defaultXDp);
        float y = prefs.getFloat(key + "_y", defaultYDp);
        return new float[]{x, y};
    }

    public void resetAll() {
        prefs.edit().clear().apply();
    }
}
