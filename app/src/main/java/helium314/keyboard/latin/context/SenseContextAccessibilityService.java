/*
 * SenseKey pre-alpha screen-context reader.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.context;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;

/**
 * Experimental AccessibilityService that extracts visible text from the foreground
 * application and exposes it to the SenseKey IME through {@link SenseContextCache}.
 *
 * This prototype intentionally does not take screenshots or run OCR yet. First we
 * measure how much useful context Android apps expose through the accessibility tree.
 */
public final class SenseContextAccessibilityService extends AccessibilityService {
    private static final int MAX_NODES = 500;
    private static final int MAX_CHARS = 5000;
    private static final long DEBOUNCE_MS = 120L;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRefreshRunnable = this::refreshContext;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
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
        scheduleRefresh();
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt in this lightweight prototype.
    }

    private void scheduleRefresh() {
        mHandler.removeCallbacks(mRefreshRunnable);
        mHandler.postDelayed(mRefreshRunnable, DEBOUNCE_MS);
    }

    private void refreshContext() {
        final AccessibilityNodeInfo root = findApplicationRoot();
        if (root == null) return;

        final CharSequence packageNameCs = root.getPackageName();
        final String packageName = packageNameCs == null ? "" : packageNameCs.toString();
        if (getPackageName().equals(packageName)) return;

        final String extracted = extractVisibleText(root);
        if (!TextUtils.isEmpty(extracted)) {
            SenseContextCache.update(packageName, extracted);
        }
    }

    private AccessibilityNodeInfo findApplicationRoot() {
        try {
            final List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (int i = windows.size() - 1; i >= 0; i--) {
                    final AccessibilityWindowInfo window = windows.get(i);
                    if (window == null || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                        continue;
                    }
                    final AccessibilityNodeInfo root = window.getRoot();
                    if (root == null) continue;
                    final CharSequence pkg = root.getPackageName();
                    if (pkg != null && !getPackageName().contentEquals(pkg)) {
                        return root;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall back to rootInActiveWindow below.
        }

        final AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        final CharSequence pkg = root.getPackageName();
        if (pkg != null && getPackageName().contentEquals(pkg)) return null;
        return root;
    }

    private String extractVisibleText(final AccessibilityNodeInfo root) {
        final ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        final HashSet<String> seen = new HashSet<>();
        final StringBuilder out = new StringBuilder();
        queue.add(root);

        int visited = 0;
        while (!queue.isEmpty() && visited < MAX_NODES && out.length() < MAX_CHARS) {
            final AccessibilityNodeInfo node = queue.removeFirst();
            visited++;

            if (!node.isPassword() && !node.isEditable()) {
                appendIfUseful(out, seen, node.getText());
            }

            final int childCount = node.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }

        return out.toString().trim();
    }

    private void appendIfUseful(final StringBuilder out, final HashSet<String> seen,
            final CharSequence value) {
        if (value == null) return;
        final String normalized = value.toString().replaceAll("\\s+", " ").trim();
        if (normalized.length() < 2 || !seen.add(normalized)) return;

        if (out.length() > 0) out.append(" | ");
        final int remaining = MAX_CHARS - out.length();
        if (remaining <= 0) return;
        out.append(normalized, 0, Math.min(normalized.length(), remaining));
    }
}
