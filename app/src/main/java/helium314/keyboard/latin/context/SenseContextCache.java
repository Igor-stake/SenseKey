/*
 * SenseKey pre-alpha context cache.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.context;

/** Small in-process bridge between the AccessibilityService and the IME. */
public final class SenseContextCache {
    /** One immutable value prevents mixing text, package and age from different captures. */
    public static final class Snapshot {
        public final String packageName;
        public final String text;
        public final String screenText;
        /** Current chat viewport without toolbar text when the layout is recognized. */
        public final String visibleText;
        public final String conversationLabel;
        public final int screenCount;
        public final boolean historySupported;
        public final int windowId;
        public final long updatedAt;
        public final boolean serviceConnected;
        /** Changes even when navigation returns to an identically named conversation. */
        public final long generation;

        private Snapshot(final String packageName, final int windowId, final long updatedAt,
                final boolean connected, final SenseContextExtractor.Screen screen) {
            this.packageName = packageName;
            this.windowId = windowId;
            this.updatedAt = updatedAt;
            serviceConnected = connected;
            generation = sGeneration;
            screenText = screen == null ? "" : screen.text;
            historySupported = screen != null && screen.supportsHistory();
            visibleText = historySupported ? screen.messages : screenText;
            conversationLabel = historySupported ? screen.conversationLabel : "";
            screenCount = historySupported ? sHistory.size() : (screenText.isEmpty() ? 0 : 1);
            text = historySupported ? sHistory.text() : screenText;
        }

        public boolean isRecentFor(final String editorPackage, final long maxAgeMillis) {
            final long age = android.os.SystemClock.elapsedRealtime() - updatedAt;
            return serviceConnected && !packageName.isEmpty()
                    && packageName.equals(editorPackage) && age >= 0 && age <= maxAgeMillis;
        }
    }

    private static long sGeneration;
    private static final SenseContextHistory sHistory = new SenseContextHistory();
    private static SenseContextExtractor.Screen sScreen;
    private static volatile Snapshot sSnapshot = new Snapshot("", -1, 0L, false, null);

    private SenseContextCache() {}

    public static synchronized void setServiceConnected(final boolean connected) {
        sGeneration++;
        sHistory.clear();
        sScreen = null;
        sSnapshot = new Snapshot("", -1, 0L, connected, null);
    }

    public static void update(final String packageName, final int windowId, final String text) {
        update(packageName, windowId, new SenseContextExtractor.Screen(
                text == null ? "" : text, "", ""));
    }

    static synchronized void update(final String packageName, final int windowId,
            final SenseContextExtractor.Screen screen) {
        final String pkg = packageName == null ? "" : packageName;
        final long now = android.os.SystemClock.elapsedRealtime();
        final Snapshot old = sSnapshot;
        final boolean recognized = screen != null && screen.supportsHistory();
        final String label = recognized ? screen.conversationLabel : "";
        if (!pkg.equals(old.packageName) || windowId != old.windowId
                || recognized != old.historySupported || !label.equals(old.conversationLabel)) {
            sGeneration++;
        }
        sHistory.add(pkg, windowId, screen, now);
        sScreen = screen;
        sSnapshot = new Snapshot(pkg, windowId, now, sSnapshot.serviceConnected, screen);
    }

    public static synchronized void clear() {
        sHistory.clear();
        invalidateWindow();
    }

    /** Hide the snapshot until the same chat is reverified after a geometry change. */
    static synchronized void invalidateWindow() {
        sGeneration++;
        sScreen = null;
        sSnapshot = new Snapshot("", -1, 0L, sSnapshot.serviceConnected, null);
    }

    /** Keep the current screen as the first fragment after a user-requested reset. */
    public static synchronized void resetHistory() {
        sGeneration++;
        sHistory.clear();
        if (sScreen == null) return;
        final Snapshot old = sSnapshot;
        sHistory.add(old.packageName, old.windowId, sScreen, old.updatedAt);
        sSnapshot = new Snapshot(old.packageName, old.windowId, old.updatedAt,
                old.serviceConnected, sScreen);
    }

    public static Snapshot getSnapshot() {
        return sSnapshot;
    }
}
