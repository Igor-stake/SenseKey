// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion;

/** Verify the production JSON parser and context packing against Android's own org.json. */
public final class SenseCompletionProtocolSmoke {
    public static void run() throws Exception {
        check(" приеду.".equals(SenseCompletionClient.parseCompletion(
                "{\"text\":\"Да, приеду.\"}", "Да,")), "Exact draft must produce a suffix");
        check(SenseCompletionClient.parseCompletion("{\"text\":\"Нет, не приеду.\"}", "Да,").isEmpty(),
                "Changed draft must be rejected");
        check(SenseCompletionClient.parseCompletion("{\"text\":\"\"}", "Да,").isEmpty(),
                "Model abstention must stay empty");
        boolean invalid = false;
        try { SenseCompletionClient.parseCompletion("{\"text\":12}", "Да,"); }
        catch (org.json.JSONException expected) { invalid = true; }
        check(invalid, "Numeric text must not be coerced to a string");
        String context = SenseCompletionClient.packContext("Old unrelated fragment", "Current question");
        check(context.endsWith("CURRENTLY_VISIBLE:\nCurrent question"), "Viewport priority must survive packing");
        check(SenseCompletionQuality.addsUnsupportedSpecifics("приехать в пятницу в 19.",
                "Когда удобно?", "Давай "), "Unknown date/time must be withheld");
        check(!SenseCompletionQuality.addsUnsupportedSpecifics("встретимся в пятницу в 19.",
                "В пятницу свободен после 19.", "Давай "), "Known date/time must be reusable");
        check(SenseCompletionQuality.addsUnsupportedSpecifics("завтра.", "Сможешь сегодня?", "Не смогу, "),
                "An ungrounded alternative date must be withheld");
        System.out.println("PASS: production structured protocol, 8 cases");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
