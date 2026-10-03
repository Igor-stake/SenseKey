// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion;

/** Complete an empty reply or a phrase boundary; leave unfinished words to the dictionary. */
public final class SenseCompletionTrigger {
    private SenseCompletionTrigger() {}

    public static boolean shouldRequest(final String draft) {
        if (draft.trim().isEmpty()) return true;
        boolean hasWord = false;
        for (int i = 0; i < draft.length(); i++) {
            if (Character.isLetterOrDigit(draft.charAt(i))) { hasWord = true; break; }
        }
        if (!hasWord) return false;
        final char last = draft.charAt(draft.length() - 1);
        return Character.isWhitespace(last) || ",.;:!?…".indexOf(last) >= 0;
    }
}
