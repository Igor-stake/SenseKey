// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.context

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SenseContextHistoryTest {
    private val first = "First message with enough text for overlap."
    private val second = "Second message with enough text for overlap."
    private val third = "Third message with enough text for overlap."
    private fun screen(text: String, label: String = "Test contact") =
        SenseContextExtractor.Screen(text, label, text)
    private fun add(history: SenseContextHistory, text: String, label: String = "Test contact",
            pkg: String = "com.whatsapp", window: Int = 1, now: Long = 1000) =
        history.add(pkg, window, screen(text, label), now)

    @Test fun repeatedCallbacksDoNotFillTheSixSlots() {
        val h = SenseContextHistory()
        repeat(100) { add(h, first) }
        assertEquals(1, h.size())
        assertEquals(first, h.text())
    }

    @Test fun scrollingBackPrependsOverlappingOlderMessages() {
        val h = SenseContextHistory()
        add(h, "$second\n$third")
        add(h, "$first\n$second")
        assertEquals("$first\n$second\n$third", h.text())
        assertEquals(2, h.size())
    }

    @Test fun scrollingForwardAppendsOverlappingNewerMessages() {
        val h = SenseContextHistory()
        add(h, "$first\n$second")
        add(h, "$second\n$third")
        assertEquals("$first\n$second\n$third", h.text())
    }

    @Test fun viewportShrinkDoesNotConsumeAnotherSlot() {
        val h = SenseContextHistory()
        add(h, "$first\n$second\n$third")
        add(h, second)
        assertEquals(1, h.size())
        assertTrue(h.text().contains(first))
    }

    @Test fun viewportExpansionReplacesTheSmallerCapture() {
        val h = SenseContextHistory()
        add(h, second)
        add(h, "$first\n$second\n$third")
        assertEquals(1, h.size())
        assertEquals("$first\n$second\n$third", h.text())
    }

    @Test fun aShortRepeatedReplyCannotProveAnOverlap() {
        val h = SenseContextHistory()
        add(h, "$first\nYes")
        add(h, "Yes\n$third")
        assertEquals("$first\nYes\n\n---\n\nYes\n$third", h.text())
    }

    @Test fun repeatedPatternsDoNotInventScrollDirection() {
        val h = SenseContextHistory()
        add(h, "$first\n$second")
        add(h, "$second\n$first")
        assertTrue(h.text().contains("\n\n---\n\n"))
    }

    @Test fun conversationSwitchDiscardsPreviousMessages() {
        val h = SenseContextHistory()
        add(h, first, label = "Alice")
        add(h, second, label = "Bob")
        assertEquals(second, h.text())
        assertEquals(1, h.size())
    }

    @Test fun appAndWindowBoundariesDiscardPreviousMessages() {
        val h = SenseContextHistory()
        add(h, first)
        add(h, second, pkg = "com.whatsapp.w4b")
        assertEquals(second, h.text())
        add(h, third, pkg = "com.whatsapp.w4b", window = 2)
        assertEquals(third, h.text())
    }

    @Test fun unidentifiedOrEmptyScreenDropsHistory() {
        val h = SenseContextHistory()
        add(h, first)
        h.add("com.whatsapp", 1, SenseContextExtractor.Screen("Chat list", "", ""), 1000)
        assertEquals("", h.text())
        add(h, first)
        h.add("com.whatsapp", 1, screen(""), 1000)
        assertEquals(0, h.size())
    }

    @Test fun sixthSlotIsTheLimitAndOldestCaptureIsEvicted() {
        val h = SenseContextHistory()
        repeat(7) { add(h, "Distinct captured message $it - no overlap") }
        assertEquals(6, h.size())
        assertFalse(h.text().contains("message 0"))
        assertTrue(h.text().contains("message 6"))
    }

    @Test fun inactiveSessionExpiresBeforeNextCapture() {
        val h = SenseContextHistory()
        add(h, first, now = 1000)
        add(h, second, now = 1001 + SenseContextHistory.MAX_IDLE_MS)
        assertEquals(second, h.text())
    }

    @Test fun historyAndEmojiRemainBounded() {
        val h = SenseContextHistory()
        repeat(6) { add(h, "$it:" + "x".repeat(4996) + "\uD83D\uDE00") }
        val text = h.text()
        assertTrue(text.length <= SenseContextHistory.MAX_CHARS)
        assertFalse(text.first().isLowSurrogate())
        assertFalse(text.last().isHighSurrogate())
    }
}
