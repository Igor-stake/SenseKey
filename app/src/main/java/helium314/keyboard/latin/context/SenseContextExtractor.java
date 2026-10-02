/* SPDX-License-Identifier: GPL-3.0-only */
package helium314.keyboard.latin.context;

import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.regex.Pattern;

/** Bounded traversal. Owns and releases the root and every child it obtains. */
final class SenseContextExtractor {
    static final int MAX_NODES = 500;
    static final int MAX_CHARS = 5000;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private SenseContextExtractor() {}

    static String extract(final AccessibilityNodeInfo root) {
        final ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        final StringBuilder out = new StringBuilder();
        queue.add(root);
        int visited = 0;
        String lastText = "";
        try {
            while (!queue.isEmpty() && visited < MAX_NODES && out.length() < MAX_CHARS) {
                final AccessibilityNodeInfo node = queue.removeFirst();
                visited++;
                try {
                    // Editable/password containers can expose their text again through children.
                    if (node.isPassword() || node.isEditable()) continue;
                    if (node.isVisibleToUser()) {
                        String text = normalize(node.getText());
                        if (text.isEmpty()) text = normalize(node.getContentDescription());
                        if (!text.isEmpty() && !text.equals(lastText)) {
                            if (out.length() > 0) out.append('\n');
                            int count = Math.min(text.length(), MAX_CHARS - out.length());
                            if (count > 0 && Character.isHighSurrogate(text.charAt(count - 1))) {
                                count--;
                            }
                            out.append(text, 0, count);
                            lastText = text;
                        }
                    }
                    // Bound child fetches and the queue too, including unusually wide trees.
                    final int count = Math.min(node.getChildCount(),
                            MAX_NODES - visited - queue.size());
                    for (int i = 0; i < count; i++) {
                        final AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) queue.addLast(child);
                    }
                } finally {
                    recycle(node);
                }
            }
        } finally {
            while (!queue.isEmpty()) recycle(queue.removeFirst());
        }
        return out.toString().trim();
    }

    private static String normalize(final CharSequence value) {
        return value == null ? "" : WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    @SuppressWarnings("deprecation")
    static void recycle(final AccessibilityNodeInfo node) {
        // API 33 removed pooling. Older supported Android releases still need this.
        if (Build.VERSION.SDK_INT < 33) node.recycle();
    }
}
