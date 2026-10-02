/* SPDX-License-Identifier: GPL-3.0-only */
package helium314.keyboard.latin.context;

import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Pattern;

/** Bounded traversal. Owns and releases the root and every child it obtains. */
final class SenseContextExtractor {
    static final int MAX_NODES = 500;
    static final int MAX_CHARS = 5000;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    static final class Screen {
        final String text;
        final String conversationLabel;
        final String messages;

        Screen(final String text, final String conversationLabel, final String messages) {
            this.text = text;
            this.conversationLabel = conversationLabel;
            this.messages = messages;
        }

        boolean supportsHistory() {
            return !conversationLabel.isEmpty() && !messages.isEmpty();
        }
    }

    private static final class Entry {
        final AccessibilityNodeInfo node;
        final boolean inMessages;

        Entry(final AccessibilityNodeInfo node, final boolean inMessages) {
            this.node = node;
            this.inMessages = inMessages;
        }
    }

    private static final class MessageText {
        final String text;
        final Rect bounds = new Rect();

        MessageText(final AccessibilityNodeInfo node, final String text) {
            this.text = text;
            node.getBoundsInScreen(bounds);
        }
    }

    private SenseContextExtractor() {}

    static String extract(final AccessibilityNodeInfo root) {
        return extractScreen(root).text;
    }

    static Screen extractScreen(final AccessibilityNodeInfo root) {
        final String pkg = root.getPackageName() == null ? "" : root.getPackageName().toString();
        // Start with the app already verified on the phone. Unknown layouts keep one screen.
        final boolean whatsapp = pkg.equals("com.whatsapp") || pkg.equals("com.whatsapp.w4b");
        final ArrayDeque<Entry> queue = new ArrayDeque<>();
        final ArrayList<MessageText> messages = new ArrayList<>();
        final StringBuilder out = new StringBuilder();
        queue.add(new Entry(root, false));
        int visited = 0;
        int messageChars = 0;
        int messageLists = 0;
        String label = "";
        String lastText = "";
        try {
            while (!queue.isEmpty() && visited < MAX_NODES
                    && (out.length() < MAX_CHARS || (whatsapp && messageChars < MAX_CHARS))) {
                final Entry entry = queue.removeFirst();
                final AccessibilityNodeInfo node = entry.node;
                visited++;
                try {
                    // Editable/password containers can expose their text again through children.
                    if (node.isPassword() || node.isEditable()) continue;
                    final boolean startsMessages = whatsapp && !entry.inMessages
                            && isMessageList(node, pkg);
                    final boolean inMessages = entry.inMessages || startsMessages;
                    if (startsMessages) messageLists++;
                    if (node.isVisibleToUser()) {
                        String text = normalize(node.getText());
                        if (text.isEmpty()) text = normalize(node.getContentDescription());
                        if (!text.isEmpty()) {
                            if (!text.equals(lastText)) appendBounded(out, text);
                            lastText = text;
                            if (whatsapp && !inMessages
                                    && (pkg + ":id/conversation_contact_name")
                                            .equals(node.getViewIdResourceName())
                                    && text.length() <= 180 && !text.endsWith("…")
                                    && !text.endsWith("...")) {
                                label = text;
                            }
                            if (inMessages && messageChars < MAX_CHARS) {
                                final String bounded = truncate(text, MAX_CHARS - messageChars);
                                messages.add(new MessageText(node, bounded));
                                messageChars += bounded.length() + 1;
                            }
                        }
                    }
                    final int count = Math.min(node.getChildCount(),
                            MAX_NODES - visited - queue.size());
                    for (int i = 0; i < count; i++) {
                        final AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) queue.addLast(new Entry(child, inMessages));
                    }
                } finally {
                    recycle(node);
                }
            }
        } finally {
            while (!queue.isEmpty()) recycle(queue.removeFirst().node);
        }
        // Visual order is more useful than breadth-first tree order for message bubbles.
        Collections.sort(messages, (first, second) -> {
            final int top = Integer.compare(first.bounds.top, second.bounds.top);
            return top == 0 ? Integer.compare(first.bounds.left, second.bounds.left) : top;
        });
        final StringBuilder body = new StringBuilder();
        MessageText previous = null;
        for (final MessageText message : messages) {
            // Collapse duplicate parent/child labels only when they occupy the same rectangle.
            // Two separate short replies must remain two messages.
            if (previous == null || message.bounds.isEmpty()
                    || !message.bounds.equals(previous.bounds)
                    || !message.text.equals(previous.text)) appendBounded(body, message.text);
            previous = message;
        }
        if (messageLists != 1 || body.length() == 0) label = "";
        return new Screen(out.toString().trim(), label,
                label.isEmpty() ? "" : body.toString().trim());
    }

    private static boolean isMessageList(final AccessibilityNodeInfo node, final String pkg) {
        final String id = node.getViewIdResourceName();
        if ((pkg + ":id/conversation_list").equals(id)) return true;
        final CharSequence className = node.getClassName();
        return className != null && (className.toString().endsWith(".ListView")
                || className.toString().endsWith(".RecyclerView"));
    }

    private static void appendBounded(final StringBuilder out, final String text) {
        final int remaining = MAX_CHARS - out.length() - (out.length() == 0 ? 0 : 1);
        if (remaining <= 0) return;
        final String bounded = truncate(text, remaining);
        if (bounded.isEmpty()) return;
        if (out.length() > 0) out.append('\n');
        out.append(bounded);
    }

    static String truncate(final String text, final int limit) {
        int count = Math.min(text.length(), Math.max(0, limit));
        if (count > 0 && Character.isHighSurrogate(text.charAt(count - 1))) count--;
        return text.substring(0, count);
    }

    private static String normalize(final CharSequence value) {
        return value == null ? "" : WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    @SuppressWarnings("deprecation")
    static void recycle(final AccessibilityNodeInfo node) {
        if (Build.VERSION.SDK_INT < 33) node.recycle();
    }
}
