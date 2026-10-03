// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion;

import helium314.keyboard.latin.context.SenseContextCache;

/** Frozen editor and conversation state. Never reuse a result in a newer editor session. */
public final class SenseCompletionRequest {
    public final long editorSession;
    public final int fieldId;
    public final String packageName;
    public final int cursor;
    public final String draft;
    public final String context;
    public final String visibleContext;
    /** Exactly the bounded context placed in the HTTP payload, also used for UI counts. */
    public final String payloadContext;
    public final String conversationLabel;
    public final long contextGeneration;
    public final String replyLanguage;

    public SenseCompletionRequest(final long editorSession, final int fieldId,
            final String packageName, final int cursor, final String draft,
            final SenseContextCache.Snapshot snapshot) {
        this(editorSession, fieldId, packageName, cursor, draft, snapshot, "");
    }

    public SenseCompletionRequest(final long editorSession, final int fieldId,
            final String packageName, final int cursor, final String draft,
            final SenseContextCache.Snapshot snapshot, final String replyLanguage) {
        this.editorSession = editorSession;
        this.fieldId = fieldId;
        this.packageName = packageName;
        this.cursor = cursor;
        this.draft = draft;
        context = snapshot.text;
        visibleContext = snapshot.visibleText;
        payloadContext = SenseCompletionClient.packContext(context, visibleContext);
        conversationLabel = snapshot.conversationLabel;
        contextGeneration = snapshot.generation;
        this.replyLanguage = replyLanguage;
    }

    public boolean matchesEditor(final long session, final int field,
            final String pkg, final int selectionStart, final int selectionEnd,
            final String beforeCursor) {
        return editorSession == session && fieldId == field && packageName.equals(pkg)
                && cursor >= 0 && cursor == selectionStart && cursor == selectionEnd
                && draft.equals(beforeCursor);
    }

    public boolean matchesContext(final SenseContextCache.Snapshot snapshot) {
        return snapshot.isRecentFor(packageName, 15_000L)
                && contextGeneration == snapshot.generation && context.equals(snapshot.text)
                && visibleContext.equals(snapshot.visibleText)
                && conversationLabel.equals(snapshot.conversationLabel);
    }
}
