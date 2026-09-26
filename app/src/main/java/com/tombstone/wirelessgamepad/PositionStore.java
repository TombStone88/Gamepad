package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists each control's top-left position in dp so custom layouts survive app restarts.
 *
 * <p>All positions are stored in dp (density-independent pixels). The caller is
 * responsible for converting to/from px using the screen density.</p>
 */
public class PositionStore {

    private static final String PREFS_NAME = "gamepad_positions";
    private final SharedPreferences prefs;

    public PositionStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Saves the position of a single control (in dp).
     *
     * @param key  the unique control identifier (e.g. "leftStick")
     * @param xDp  left edge in dp
     * @param yDp  top edge in dp
     */
    public void save(String key, float xDp, float yDp) {
        prefs.edit()
                .putFloat(key + "_x", xDp)
                .putFloat(key + "_y", yDp)
                .apply();
    }

    /**
     * Loads the saved position for a control (in dp).
     * Returns the supplied defaults if no position has been saved yet.
     *
     * @param key          the unique control identifier
     * @param defaultXDp   default left edge in dp
     * @param defaultYDp   default top edge in dp
     * @return float[]{x, y} in dp
     */
    public float[] load(String key, float defaultXDp, float defaultYDp) {
        float x = prefs.getFloat(key + "_x", defaultXDp);
        float y = prefs.getFloat(key + "_y", defaultYDp);
        return new float[]{x, y};
    }

    /**
     * Removes the saved position for a single control so that the
     * default position is used next time the app starts.
     *
     * @param key the unique control identifier
     */
    public void remove(String key) {
        prefs.edit()
                .remove(key + "_x")
                .remove(key + "_y")
                .apply();
    }

    /**
     * Removes saved positions for all controls. Equivalent to calling
     * {@link #remove(String)} for every known key.
     */
    public void resetAll() {
        prefs.edit().clear().apply();
    }
}
