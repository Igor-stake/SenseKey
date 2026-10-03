// SPDX-License-Identifier: GPL-3.0-only
import helium314.keyboard.latin.completion.SenseCompletionQuality;

import java.util.regex.Pattern;

/** Runs the production filter in ART, not the host JDK used by unit tests. */
public final class SenseCompletionAndroidSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            boolean rejected = false;
            try {
                Pattern.compile("[\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "Expected the Android 15 unsupported-flag behavior");
            System.out.println("Android rejects UNICODE_CHARACTER_CLASS as expected");
        }
        // This first valid model reply initialized the broken filter and crashed build 21.
        check(!SenseCompletionQuality.copiesHistory("что это стоит обсудить лично.",
                "Когда встретимся?"), "A new continuation must be allowed");
        check(SenseCompletionQuality.copiesHistory("что мы обсудили этот вопрос вчера.",
                "Мы обсудили этот вопрос вчера!"), "A copied Cyrillic sentence must be rejected");
        check(SenseCompletionQuality.copiesHistory("что чай готов уже давно готов.",
                "Чай готов?\nУже давно готов!"), "A collage must be rejected");
        check(SenseCompletionQuality.copiesHistory("ЗАВТРА встретимся ровно в 11:30!",
                "Завтра встретимся ровно в 11:30."), "Unicode words and numbers must be preserved");
        check(SenseCompletionQuality.copiesHistory("𐐨𐐩 𐐪𐐫 𐐬𐐭 𐐮𐐯",
                "𐐨𐐩 𐐪𐐫 𐐬𐐭 𐐮𐐯"), "Supplementary letters must be preserved");
        check(!SenseCompletionQuality.copiesHistory("Спасибо!", "Спасибо за помощь!"),
                "A short acknowledgement must be allowed");
        System.out.println("PASS: production completion filter, 6 cases");
        helium314.keyboard.latin.completion.SenseCompletionProtocolSmoke.run();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
