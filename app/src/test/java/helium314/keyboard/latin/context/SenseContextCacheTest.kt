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
}
