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
    /** One immutable value prevents mixing text, package and age from different captures. */
    public static final class Snapshot {
        public final String packageName;
        public final String text;
        public final int windowId;
        public final long updatedAt;
        public final boolean serviceConnected;

        private Snapshot(final String packageName, final String text, final int windowId,
                final long updatedAt, final boolean serviceConnected) {
            this.packageName = packageName;
            this.text = text;
            this.windowId = windowId;
            this.updatedAt = updatedAt;
            this.serviceConnected = serviceConnected;
        }

        public boolean isRecentFor(final String editorPackage, final long maxAgeMillis) {
            final long age = android.os.SystemClock.elapsedRealtime() - updatedAt;
            return serviceConnected && !packageName.isEmpty()
                    && packageName.equals(editorPackage) && age >= 0 && age <= maxAgeMillis;
        }
    }

    private static volatile Snapshot sSnapshot = new Snapshot("", "", -1, 0L, false);

    private SenseContextCache() {}

    public static void setServiceConnected(final boolean connected) {
        sSnapshot = new Snapshot("", "", -1, 0L, connected);
    }

    public static void update(final String packageName, final int windowId, final String text) {
        sSnapshot = new Snapshot(packageName == null ? "" : packageName,
                text == null ? "" : text, windowId,
                android.os.SystemClock.elapsedRealtime(), sSnapshot.serviceConnected);
    }

    public static void clear() {
        sSnapshot = new Snapshot("", "", -1, 0L, sSnapshot.serviceConnected);
    }

    public static Snapshot getSnapshot() {
        return sSnapshot;
    }
}
