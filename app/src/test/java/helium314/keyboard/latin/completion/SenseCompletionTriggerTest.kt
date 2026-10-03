// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SenseCompletionTriggerTest {
    @Test fun emptyReplyAndOrdinaryPhraseBoundariesRequestCompletions() {
        for (draft in listOf("", "  ", "Я думаю,", "Я думаю, ", "Завтра ",
            "Спасибо!", "Не знаю…", "I think ", "да,")) {
            assertTrue(SenseCompletionTrigger.shouldRequest(draft), draft)
        }
    }

    @Test fun unfinishedWordsAndNonTextDoNotRequestPhraseSuffixes() {
        for (draft in listOf("Я ду", "Привет", "tomorr", ",", "!!!", "😀")) {
            assertFalse(SenseCompletionTrigger.shouldRequest(draft), draft)
        }
    }
}
