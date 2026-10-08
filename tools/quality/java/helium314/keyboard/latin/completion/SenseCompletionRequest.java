// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion;

/** Host fixture only. Android editor freshness is covered by the app's tests. */
public final class SenseCompletionRequest {
    public final String draft;
    public final String payloadContext;
    public final String replyLanguage;
    public final String conversationLabel = "";

    public SenseCompletionRequest(String draft, String history, String visible, String language) {
        this.draft = draft;
        payloadContext = SenseCompletionClient.packContext(history, visible);
        replyLanguage = language;
    }
}
