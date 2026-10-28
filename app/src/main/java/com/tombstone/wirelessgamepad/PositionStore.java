package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists each control's top-left position and scale/size in dp so custom layouts
 * survive app restarts.
 */
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

    public float[] load(String key, float defaultXDp, float defaultYDp) {
        float x = prefs.getFloat(key + "_x", defaultXDp);
        float y = prefs.getFloat(key + "_y", defaultYDp);
        return new float[]{x, y};
    }

    public void saveScale(String key, float scale) {
        prefs.edit()
                .putFloat(key + "_scale", scale)
                .apply();
    }

    public float loadScale(String key, float defaultScale) {
        return prefs.getFloat(key + "_scale", defaultScale);
    }

    public void remove(String key) {
        prefs.edit()
                .remove(key + "_x")
                .remove(key + "_y")
                .remove(key + "_scale")
                .apply();
    }

    public void resetAll() {
        prefs.edit().clear().apply();
    }
}
