// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion;

import android.content.SharedPreferences;

import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.settings.SettingsKt;

/** Shared, adjustable dimensions. Apply the smaller initial layout only once. */
public final class SenseCompletionLayout {
    public static final String PREF_PANEL_HEIGHT = "sense_completion_panel_height_dp";
    private static final String PREF_COMPACT_APPLIED = "sense_compact_layout_v1";
    public static final int DEFAULT_PANEL_DP = 96;
    public static final int MIN_PANEL_DP = 80;
    public static final int MAX_PANEL_DP = 144;

    private SenseCompletionLayout() {}

    public static String keyboardHeightKey(final boolean landscape) {
        return SettingsKt.createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX,
                landscape ? 1 : 0, 1);
    }

    public static void applyInitialCompactLayout(final SharedPreferences prefs) {
        if (prefs.getBoolean(PREF_COMPACT_APPLIED, false)) return;
        final SharedPreferences.Editor edit = prefs.edit();
        for (boolean landscape : new boolean[] { false, true }) {
            final float current = Settings.readHeightScale(prefs, landscape);
            edit.putFloat(keyboardHeightKey(landscape), Float.isFinite(current)
                    ? Math.max(0.3f, Math.min(current, 0.85f)) : 0.85f);
        }
        edit.putBoolean(PREF_COMPACT_APPLIED, true).apply();
    }

    public static int panelHeightDp(final SharedPreferences prefs) {
        return Math.max(MIN_PANEL_DP, Math.min(MAX_PANEL_DP,
                prefs.getInt(PREF_PANEL_HEIGHT, DEFAULT_PANEL_DP)));
    }
}
