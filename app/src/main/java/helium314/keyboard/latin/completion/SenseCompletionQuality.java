// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reject clear copies, not a semantic relevance score or a substitute for a trained model. */
public final class SenseCompletionQuality {
    // Explicit Unicode categories already work on Android. UNICODE_CHARACTER_CLASS is
    // accepted by the host JDK but throws on Android 15+, breaking class initialization.
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private SenseCompletionQuality() {}

    public static boolean copiesHistory(final String suffix, final String context) {
        final List<String> answer = words(suffix);
        if (answer.size() < 4) return false; // Short acknowledgements and reused facts are legitimate.
        final List<String> history = words(context);
        final boolean[] copied = new boolean[answer.size()];
        int runs = 0;
        for (int a = 0; a < answer.size(); a++) {
            int longest = 0;
            for (int h = 0; h < history.size(); h++) {
                int length = 0;
                while (a + length < answer.size() && h + length < history.size()
                        && answer.get(a + length).equals(history.get(h + length))) length++;
                longest = Math.max(longest, length);
            }
            if (longest >= 4 && longest * 5 >= answer.size() * 4) return true;
            if (longest >= 2 && !copied[a]) {
                runs++;
                for (int i = a; i < a + longest; i++) copied[i] = true;
            }
        }
        int count = 0;
        for (boolean word : copied) if (word) count++;
        // Also catch a connector followed by a collage of two already sent phrases.
        return answer.size() >= 6 && runs >= 2 && count >= 5 && count * 5 >= answer.size() * 4;
    }

    private static List<String> words(final String text) {
        final List<String> words = new ArrayList<>();
        final Matcher matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) words.add(matcher.group());
        return words;
    }
}
