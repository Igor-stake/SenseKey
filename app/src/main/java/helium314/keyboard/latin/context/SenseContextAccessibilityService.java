/*
 * SenseKey pre-alpha screen-context reader.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.context;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

/**
 * Experimental AccessibilityService that extracts visible text from the foreground
 * application and exposes it to the SenseKey IME through {@link SenseContextCache}.
 *
 * This prototype intentionally does not take screenshots or run OCR yet. First we
 * measure how much useful context Android apps expose through the accessibility tree.
 */
public final class SenseContextAccessibilityService extends AccessibilityService {
    private static final long DEBOUNCE_MS = 120L;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRefreshRunnable = this::refreshContext;
    private boolean mRefreshScheduled;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        SenseContextCache.setServiceConnected(true);
        final AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
        scheduleRefresh();
    }

    @Override
    public void onAccessibilityEvent(final AccessibilityEvent event) {
        if (event != null && event.getPackageName() != null
                && getPackageName().contentEquals(event.getPackageName())) {
            return;
        }
        if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // A navigation/dialog boundary must not join contacts with the same display name.
            SenseContextCache.clear();
        } else if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // Keyboard geometry changed; the same conversation must be reverified.
            SenseContextCache.invalidateWindow();
        }
        scheduleRefresh();
    }

    @Override
    public void onInterrupt() {
        cancelRefresh();
        SenseContextCache.clear();
    }

    @Override
    public void onDestroy() {
        cancelRefresh();
        SenseContextCache.setServiceConnected(false);
        super.onDestroy();
    }

    private void cancelRefresh() {
        mHandler.removeCallbacks(mRefreshRunnable);
        mRefreshScheduled = false;
    }

    private void scheduleRefresh() {
        // Coalesce an event burst without postponing forever during continuous scrolling.
        if (mRefreshScheduled) return;
        mRefreshScheduled = true;
        mHandler.postDelayed(mRefreshRunnable, DEBOUNCE_MS);
    }

    private void refreshContext() {
        mRefreshScheduled = false;
        try {
            final AccessibilityNodeInfo root = findApplicationRoot();
            if (root == null) {
                SenseContextCache.clear();
                return;
            }
            final CharSequence packageNameCs = root.getPackageName();
            final String packageName = packageNameCs == null ? "" : packageNameCs.toString();
            final int windowId = root.getWindowId();
            final SenseContextExtractor.Screen extracted = SenseContextExtractor.extractScreen(root);
            // Empty captures must replace the previous screen too.
            SenseContextCache.update(packageName, windowId, extracted);
        } catch (IllegalStateException | SecurityException e) {
            // Windows may disappear between an event and this delayed capture.
            SenseContextCache.clear();
        }
    }

    private AccessibilityNodeInfo findApplicationRoot() {
        try {
            final List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                try {
                    for (final AccessibilityWindowInfo window : windows) {
                        if (window != null && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                            // A null selection here means an app is present but cannot be read
                            // (or is SenseKey itself). Do not fall back to a background window.
                            return selectApplicationRoot(windows, getPackageName());
                        }
                    }
                } finally {
                    recycleWindows(windows);
                }
            }
        } catch (IllegalStateException | SecurityException ignored) {
            // Fall back to rootInActiveWindow below.
        }

        final AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        final CharSequence pkg = root.getPackageName();
        if (pkg == null || getPackageName().contentEquals(pkg)) {
            SenseContextExtractor.recycle(root);
            return null;
        }
        return root;
    }

    static AccessibilityNodeInfo selectApplicationRoot(final List<AccessibilityWindowInfo> windows,
            final String ownPackage) {
        // getWindows() is already ordered from the top layer down. Prefer input focus
        // (important in split screen), then the active window, then the top application.
        for (int priority = 0; priority < 3; priority++) {
            for (final AccessibilityWindowInfo window : windows) {
                if (window == null || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION
                        || (priority == 0 && !window.isFocused())
                        || (priority == 1 && !window.isActive())) continue;
                final AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                final CharSequence pkg = root.getPackageName();
                if (pkg != null && !ownPackage.contentEquals(pkg)) return root;
                SenseContextExtractor.recycle(root);
                // Do not read a background application behind SenseKey settings/dialogs.
                if (window.isFocused() || window.isActive()) return null;
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static void recycleWindows(final List<AccessibilityWindowInfo> windows) {
        if (Build.VERSION.SDK_INT < 33) {
            for (final AccessibilityWindowInfo window : windows) {
                if (window != null) window.recycle();
            }
        }
    }
}
