// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.Consumer;

import helium314.keyboard.latin.R;
import helium314.keyboard.latin.SuggestedWords;

/** The body changes, but the header and outer bounds stay fixed while typing. */
public final class SenseCompletionPanel extends LinearLayout {
    public final TextView status;
    public final TextView contextInfo;
    public final TextView suffix;
    public final TextView retry;
    private final LinearLayout words;
    private final ScrollView continuation;
    private final int color;

    public SenseCompletionPanel(final Context context, final int textColor,
            final Runnable onRetry, final Runnable onCancel) {
        super(context);
        color = textColor;
        setOrientation(VERTICAL);
        setPadding(dp(8), 0, dp(8), dp(4));
        setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        final LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        status = label(12);
        status.setMaxLines(2);
        status.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(status, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        retry = action(R.string.sense_completion_retry, onRetry);
        retry.setVisibility(INVISIBLE);
        header.addView(retry);
        header.addView(action(R.string.sense_completion_cancel, onCancel));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, dp(40)));
        contextInfo = label(11);
        contextInfo.setMaxLines(1);
        contextInfo.setEllipsize(TextUtils.TruncateAt.END);
        addView(contextInfo, new LayoutParams(LayoutParams.MATCH_PARENT, dp(16)));
        final FrameLayout body = new FrameLayout(context);
        continuation = new ScrollView(context);
        suffix = label(17);
        suffix.setPadding(0, 0, 0, dp(4));
        continuation.addView(suffix, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        body.addView(continuation, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        words = new LinearLayout(context);
        words.setGravity(Gravity.CENTER_VERTICAL);
        words.setVisibility(GONE);
        body.addView(words, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private TextView label(final int size) {
        final TextView view = new TextView(getContext());
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView action(final int title, final Runnable callback) {
        final TextView view = label(12);
        view.setText(title);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(6), 0, dp(6), 0);
        view.setMinWidth(dp(56));
        view.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        view.setOnClickListener(ignored -> callback.run());
        return view;
    }

    public void showContinuation() {
        words.setVisibility(GONE);
        continuation.setVisibility(VISIBLE);
    }

    /** Ordinary word choices remain usable between phrase completions. */
    public void showWords(final SuggestedWords suggestions,
            final Consumer<SuggestedWords.SuggestedWordInfo> onPick) {
        words.removeAllViews();
        for (int i = 0; i < Math.min(3, suggestions.size()); i++) {
            final SuggestedWords.SuggestedWordInfo info = suggestions.getInfo(i);
            final TextView word = label(16);
            word.setText(info.mWord);
            word.setMaxLines(1);
            word.setEllipsize(TextUtils.TruncateAt.END);
            word.setGravity(Gravity.CENTER);
            word.setOnClickListener(ignored -> onPick.accept(info));
            words.addView(word, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        }
        continuation.setVisibility(GONE);
        words.setVisibility(VISIBLE);
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
