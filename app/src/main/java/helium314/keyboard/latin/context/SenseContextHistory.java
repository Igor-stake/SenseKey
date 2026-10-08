/* SPDX-License-Identifier: GPL-3.0-only */
package helium314.keyboard.latin.context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Memory for one active conversation, never an archive of everything the service can read. */
final class SenseContextHistory {
    static final int MAX_SCREENS = 6;
    static final int MAX_CHARS = 30_000;
    static final long MAX_IDLE_MS = 15 * 60_000L;
    private final ArrayList<String> screens = new ArrayList<>();
    private String packageName = "";
    private String label = "";
    private int windowId = -1;
    private long lastCaptureAt;

    void add(final String pkg, final int window, final SenseContextExtractor.Screen screen,
            final long now) {
        if (pkg.isEmpty() || !screen.supportsHistory()) {
            clear();
            return;
        }
        if (!pkg.equals(packageName) || window != windowId
                || !screen.conversationLabel.equals(label)
                || now - lastCaptureAt > MAX_IDLE_MS || now < lastCaptureAt) clear();
        packageName = pkg;
        windowId = window;
        label = screen.conversationLabel;
        lastCaptureAt = now;
        final String text = SenseContextExtractor.truncate(screen.messages,
                SenseContextExtractor.MAX_CHARS);
        final List<String> lines = lines(text);
        for (final String captured : screens) {
            // Repeated callbacks and a keyboard shrinking the viewport are not new screens.
            if (captured.equals(text) || contains(lines(captured), lines)) return;
        }
        for (int i = screens.size() - 1; i >= 0; i--) {
            if (contains(lines, lines(screens.get(i)))) screens.remove(i);
        }
        screens.add(text);
        while (screens.size() > MAX_SCREENS) screens.remove(0);
    }

    int size() {
        return screens.size();
    }

    String text() {
        final ArrayList<List<String>> blocks = new ArrayList<>();
        for (final String screen : screens) {
            final List<String> next = lines(screen);
            boolean placed = false;
            for (int i = 0; i < blocks.size(); i++) {
                final List<String> merged = merge(blocks.get(i), next);
                if (merged != null) {
                    blocks.set(i, merged);
                    placed = true;
                    break;
                }
            }
            if (!placed) blocks.add(next);
        }
        // A jump with no reliable overlap stays a separate fragment. Do not invent chronology.
        final StringBuilder out = new StringBuilder();
        for (final List<String> block : blocks) {
            if (out.length() > 0) out.append("\n\n---\n\n");
            for (int i = 0; i < block.size(); i++) {
                if (i > 0) out.append('\n');
                out.append(block.get(i));
            }
        }
        if (out.length() <= MAX_CHARS) return out.toString();
        int start = out.length() - MAX_CHARS;
        if (Character.isLowSurrogate(out.charAt(start))) start++;
        return out.substring(start);
    }

    void clear() {
        screens.clear();
        packageName = "";
        label = "";
        windowId = -1;
        lastCaptureAt = 0L;
    }

    private static List<String> lines(final String text) {
        return Arrays.asList(text.split("\n", -1));
    }

    private static boolean reliable(final List<String> sequence, final int start,
            final int count) {
        // A short repeated answer is insufficient evidence of an overlapping message.
        int chars = 0;
        for (int i = 0; i < count; i++) chars += sequence.get(start + i).length();
        return chars >= 24;
    }

    private static boolean contains(final List<String> haystack, final List<String> needle) {
        if (!reliable(needle, 0, needle.size())) return false;
        for (int i = 0; i <= haystack.size() - needle.size(); i++) {
            if (haystack.subList(i, i + needle.size()).equals(needle)) return true;
        }
        return false;
    }

    private static List<String> merge(final List<String> first, final List<String> next) {
        if (first.equals(next) || contains(first, next)) return first;
        if (contains(next, first)) return next;
        final int forward = overlap(first, next);
        final int backward = overlap(next, first);
        // Repeated patterns can make direction ambiguous. Preserve both fragments instead.
        if (forward > 0 && backward > 0) return null;
        final ArrayList<String> merged = new ArrayList<>();
        if (forward > 0) {
            merged.addAll(first);
            merged.addAll(next.subList(forward, next.size()));
        } else if (backward > 0) {
            merged.addAll(next);
            merged.addAll(first.subList(backward, first.size()));
        } else {
            return null;
        }
        return merged;
    }

    private static int overlap(final List<String> first, final List<String> second) {
        for (int count = Math.min(first.size(), second.size()); count > 0; count--) {
            if (reliable(second, 0, count)
                    && first.subList(first.size() - count, first.size())
                            .equals(second.subList(0, count))) return count;
        }
        return 0;
    }
}
