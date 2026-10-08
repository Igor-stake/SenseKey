// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.context

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class SenseContextCacheTest {
    @Before fun connect() = SenseContextCache.setServiceConnected(true)
    @After fun disconnect() = SenseContextCache.setServiceConnected(false)

    @Test fun contextCannotCrossIntoAnotherApp() {
        SenseContextCache.update("chat.a", 1, "First conversation")
        val snapshot = SenseContextCache.getSnapshot()
        assertTrue(snapshot.isRecentFor("chat.a", 15_000))
        assertFalse(snapshot.isRecentFor("chat.b", 15_000))
    }

    @Test fun emptyCaptureReplacesPreviousConversation() {
        SenseContextCache.update("chat.a", 1, "Old conversation")
        SenseContextCache.update("chat.a", 2, "")
        val snapshot = SenseContextCache.getSnapshot()
        assertEquals("", snapshot.text)
        assertEquals(2, snapshot.windowId)
        assertTrue(snapshot.isRecentFor("chat.a", 15_000))
    }

    @Test fun snapshotKeepsTextAndPackageTogetherAcrossUpdates() {
        SenseContextCache.update("chat.a", 1, "A")
        val first = SenseContextCache.getSnapshot()
        SenseContextCache.update("chat.b", 2, "B")
        assertEquals("chat.a", first.packageName)
        assertEquals("A", first.text)
        assertEquals("chat.b", SenseContextCache.getSnapshot().packageName)
        assertEquals("B", SenseContextCache.getSnapshot().text)
    }

    @Test fun staleContextExpires() {
        SenseContextCache.update("chat.a", 1, "Old message")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(16))
        assertFalse(SenseContextCache.getSnapshot().isRecentFor("chat.a", 15_000))
    }

    @Test fun clearingAWindowKeepsServiceStateButRemovesItsText() {
        SenseContextCache.update("chat.a", 1, "Message")
        SenseContextCache.clear()
        val snapshot = SenseContextCache.getSnapshot()
        assertTrue(snapshot.serviceConnected)
        assertEquals("", snapshot.text)
        assertFalse(snapshot.isRecentFor("chat.a", 15_000))
    }

    @Test fun disconnectRemovesContext() {
        SenseContextCache.update("chat.a", 1, "Message")
        SenseContextCache.setServiceConnected(false)
        assertFalse(SenseContextCache.getSnapshot().serviceConnected)
        assertEquals("", SenseContextCache.getSnapshot().text)
    }

    private fun chat(text: String, label: String = "Test contact") =
        SenseContextExtractor.Screen("Toolbar\n$text", label, text)

    @Test fun repeatedCaptureKeepsConversationGenerationButContactChangeDoesNot() {
        SenseContextCache.update("com.whatsapp", 1, chat("Same message", "First contact"))
        val first = SenseContextCache.getSnapshot().generation
        SenseContextCache.update("com.whatsapp", 1, chat("Updated message", "First contact"))
        assertEquals(first, SenseContextCache.getSnapshot().generation)
        SenseContextCache.update("com.whatsapp", 1, chat("Same message", "Second contact"))
        assertNotEquals(first, SenseContextCache.getSnapshot().generation)
    }

    @Test fun navigationRejectsPreviousGenerationEvenForSameNameAndText() {
        SenseContextCache.update("com.whatsapp", 1, chat("Same message"))
        val first = SenseContextCache.getSnapshot().generation
        SenseContextCache.clear()
        SenseContextCache.update("com.whatsapp", 1, chat("Same message"))
        assertNotEquals(first, SenseContextCache.getSnapshot().generation)
    }

    @Test fun manualResetInvalidatesPredictionsEvenWhenTextDoesNotChange() {
        SenseContextCache.update("com.whatsapp", 1, chat("Same message"))
        val first = SenseContextCache.getSnapshot().generation
        SenseContextCache.resetHistory()
        assertNotEquals(first, SenseContextCache.getSnapshot().generation)
    }

    @Test fun snapshotExposesBothCurrentScreenAndAccumulatedMessages() {
        val first = "Earlier message with enough text for overlap."
        val second = "Later message with enough text for overlap."
        SenseContextCache.update("com.whatsapp", 1, chat(first))
        val original = SenseContextCache.getSnapshot()
        SenseContextCache.update("com.whatsapp", 1, chat("$first\n$second"))
        val current = SenseContextCache.getSnapshot()
        assertEquals(first, original.text)
        assertEquals("Toolbar\n$first\n$second", current.screenText)
        assertEquals("$first\n$second", current.visibleText)
        assertEquals("$first\n$second", current.text)
        assertEquals("Test contact", current.conversationLabel)
        assertTrue(current.historySupported)
    }

    @Test fun scrollingWithinStoredHistoryChangesCurrentViewportAndInvalidatesPrediction() {
        val earlier = "Earlier message with enough text for reliable overlap."
        val current = "Current question with enough text for reliable overlap."
        SenseContextCache.update("com.whatsapp", 1, chat("$earlier\n$current"))
        val before = SenseContextCache.getSnapshot()
        val request = helium314.keyboard.latin.completion.SenseCompletionRequest(
            1, 7, "com.whatsapp", 3, "Да,", before, "ru")
        SenseContextCache.update("com.whatsapp", 1, chat(current))
        val after = SenseContextCache.getSnapshot()
        assertEquals(before.text, after.text)
        assertEquals(before.generation, after.generation)
        assertEquals(current, after.visibleText)
        assertFalse(request.matchesContext(after))
        val fresh = helium314.keyboard.latin.completion.SenseCompletionRequest(
            1, 7, "com.whatsapp", 3, "Да,", after, "ru")
        assertTrue(fresh.payloadContext.endsWith("CURRENTLY_VISIBLE:\n$current"))
    }

    @Test fun keyboardGeometryInvalidationRequiresReverificationButKeepsHistory() {
        SenseContextCache.update("com.whatsapp", 1, chat("Earlier message from this chat."))
        SenseContextCache.invalidateWindow()
        assertEquals("", SenseContextCache.getSnapshot().text)
        SenseContextCache.update("com.whatsapp", 1, chat("Another message from this chat."))
        assertEquals(2, SenseContextCache.getSnapshot().screenCount)
    }

    @Test fun navigationClearDropsHistoryEvenForIdenticalDisplayNames() {
        SenseContextCache.update("com.whatsapp", 1, chat("First contact's private message."))
        SenseContextCache.clear()
        SenseContextCache.update("com.whatsapp", 1, chat("Second contact's different message."))
        val current = SenseContextCache.getSnapshot()
        assertEquals(1, current.screenCount)
        assertFalse(current.text.contains("First contact"))
    }

    @Test fun resetKeepsOnlyCurrentScreenAndDoesNotRenewItsFreshness() {
        SenseContextCache.update("com.whatsapp", 1, chat("Earlier message from this chat."))
        SenseContextCache.update("com.whatsapp", 1, chat("Current message from this chat."))
        val timestamp = SenseContextCache.getSnapshot().updatedAt
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        SenseContextCache.resetHistory()
        val current = SenseContextCache.getSnapshot()
        assertEquals(1, current.screenCount)
        assertEquals("Current message from this chat.", current.text)
        assertEquals(timestamp, current.updatedAt)
    }

    @Test fun changedHeaderAndMissingHeaderDiscardHistory() {
        SenseContextCache.update("com.whatsapp", 1, chat("First message", "Alice"))
        SenseContextCache.update("com.whatsapp", 1, chat("Second message", "Bob"))
        assertEquals("Second message", SenseContextCache.getSnapshot().text)
        SenseContextCache.update("com.whatsapp", 1, "Unrecognized screen")
        val current = SenseContextCache.getSnapshot()
        assertEquals("Unrecognized screen", current.text)
        assertFalse(current.historySupported)
        assertEquals("", current.conversationLabel)
    }
}
