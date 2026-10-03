// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SenseCompletionQualityTest {
    @Test fun copiedSentenceIsRejectedDespiteCasePunctuationOrAnAddedConnector() {
        assertTrue(SenseCompletionQuality.copiesHistory("что мы обсудили этот вопрос вчера.",
            "Мы обсудили этот вопрос вчера!"))
        assertTrue(SenseCompletionQuality.copiesHistory("WE DISCUSSED THIS TOPIC YESTERDAY.",
            "We discussed this topic yesterday."))
    }

    @Test fun gluedQuestionAndOldAnswerAreRejected() {
        assertTrue(SenseCompletionQuality.copiesHistory("что чай готов уже давно готов.",
            "Чай готов?\nУже давно готов!"))
        assertTrue(SenseCompletionQuality.copiesHistory("that tea is ready already completely ready.",
            "Tea is ready?\nAlready completely ready!"))
    }

    @Test fun shortAcknowledgementsAndNewAnswersMayReuseFacts() {
        assertFalse(SenseCompletionQuality.copiesHistory("Спасибо!", "Спасибо за помощь!"))
        assertFalse(SenseCompletionQuality.copiesHistory("после работы.", "Я свободен после работы."))
        assertFalse(SenseCompletionQuality.copiesHistory("буду завтра к 11.", "Сможешь приехать завтра к 11?"))
        assertFalse(SenseCompletionQuality.copiesHistory("что встреча начнётся завтра в 11 утра.",
            "Сможем встретиться завтра в 11 утра?"))
        assertFalse(SenseCompletionQuality.copiesHistory("что чай стоит заварить покрепче.",
            "Чай готов?\nУже давно готов!"))
    }

    @Test fun scatteredCommonWordsAreNotMistakenForTwoCopiedPhrases() {
        assertFalse(SenseCompletionQuality.copiesHistory("что лучше поговорить об этом после работы.",
            "Что случилось?\nПосле дождя.\nОб этом ещё не говорили.\nМного работы."))
        assertFalse(SenseCompletionQuality.copiesHistory("", "Текст"))
    }

    @Test fun unicodeWordsNumbersAndSupplementaryLettersStillDetectCopies() {
        assertTrue(SenseCompletionQuality.copiesHistory("ЗАВТРА встретимся ровно в 11:30!",
            "Завтра встретимся ровно в 11:30."))
        assertTrue(SenseCompletionQuality.copiesHistory("𐐨𐐩 𐐪𐐫 𐐬𐐭 𐐮𐐯", "𐐨𐐩 𐐪𐐫 𐐬𐐭 𐐮𐐯"))
        assertFalse(SenseCompletionQuality.copiesHistory("что это стоит обсудить лично.",
            "Когда встретимся?"))
    }
}
