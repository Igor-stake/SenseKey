// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reject clear copies and new numeric/calendar details; this is not a semantic relevance score. */
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

    /** Until profile/calendar grounding exists, new dates and numbers need a source in the input. */
    public static boolean addsUnsupportedSpecifics(final String suffix, final String context,
            final String draft) {
        final Set<String> known = new HashSet<>();
        for (String word : words(context + "\n" + draft)) known.add(specific(word));
        for (String word : words(suffix)) {
            final String detail = specific(word);
            if (!detail.isEmpty() && !known.contains(detail)) return true;
        }
        return false;
    }

    private static String specific(final String word) {
        boolean numeric = !word.isEmpty();
        for (int i = 0; i < word.length(); i++) numeric &= Character.isDigit(word.charAt(i));
        if (numeric) return word;
        switch (word) {
            case "сегодня": case "завтра": case "послезавтра": return word;
            case "понедельник": case "понедельника": case "понедельнику":
            case "понедельником": case "понедельнике": return "понедельник";
            case "вторник": case "вторника": case "вторнику": case "вторником":
            case "вторнике": return "вторник";
            case "среда": case "среды": case "среду": case "среде": case "средой": return "среда";
            case "четверг": case "четверга": case "четвергу": case "четвергом":
            case "четверге": return "четверг";
            case "пятница": case "пятницы": case "пятницу": case "пятнице":
            case "пятницей": return "пятница";
            case "суббота": case "субботы": case "субботу": case "субботе":
            case "субботой": return "суббота";
            case "воскресенье": case "воскресенья": case "воскресенью":
            case "воскресеньем": case "воскресеньи": return "воскресенье";
            default: return "";
        }
    }

    private static List<String> words(final String text) {
        final List<String> words = new ArrayList<>();
        final Matcher matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) words.add(matcher.group());
        return words;
    }
}
