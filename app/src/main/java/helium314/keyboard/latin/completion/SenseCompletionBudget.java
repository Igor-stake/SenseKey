// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion;

/** One deadline for a draft, including queueing and automatic restarts after context changes. */
public final class SenseCompletionBudget {
    public static final long TIMEOUT_MILLIS = 50_000L;
    private final SenseCompletionRequest editor;
    private final long startedAt;

    public SenseCompletionBudget(final SenseCompletionRequest request, final long now) {
        editor = request;
        startedAt = now;
    }

    public boolean matchesEditor(final long session, final int field, final String pkg,
            final int cursor, final String draft) {
        return editor.matchesEditor(session, field, pkg, cursor, cursor, draft);
    }

    public long remainingMillis(final long now) {
        return Math.max(0L, TIMEOUT_MILLIS - Math.max(0L, now - startedAt));
    }

    public long elapsedMillis(final long now) {
        return Math.max(0L, now - startedAt);
    }
}
