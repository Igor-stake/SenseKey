/*
 * SenseKey pre-alpha context cache.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.context;

/**
 * Small in-process bridge between the AccessibilityService and the IME.
 * This is deliberately minimal for the pre-alpha feasibility test.
 */
public final class SenseContextCache {
    private static volatile String sText = "";
    private static volatile String sPackageName = "";
    private static volatile long sUpdatedAt = 0L;

    private SenseContextCache() {}

    public static void update(final String packageName, final String text) {
        sPackageName = packageName == null ? "" : packageName;
        sText = text == null ? "" : text;
        sUpdatedAt = android.os.SystemClock.elapsedRealtime();
    }

    public static String getRecentText(final long maxAgeMillis) {
        if (sText.isEmpty()) return "";
        final long age = android.os.SystemClock.elapsedRealtime() - sUpdatedAt;
        return age <= maxAgeMillis ? sText : "";
    }

    public static String getPackageName() {
        return sPackageName;
    }
}
