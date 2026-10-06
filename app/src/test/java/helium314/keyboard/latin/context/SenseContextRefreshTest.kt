// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.context

import android.app.Application
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import helium314.keyboard.latin.completion.SenseCompletionRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SenseContextRefreshTest {
    private lateinit var service: SenseContextAccessibilityService
    private lateinit var root: AccessibilityNodeInfo

    @Before fun setUp() {
        service = Robolectric.buildService(SenseContextAccessibilityService::class.java).create().get()
        root = mock(AccessibilityNodeInfo::class.java)
        `when`(root.packageName).thenReturn("chat.test")
        `when`(root.windowId).thenReturn(1)
        `when`(root.isVisibleToUser).thenReturn(true)
        `when`(root.text).thenReturn("A visible question.")
        val window = mock(AccessibilityWindowInfo::class.java)
        `when`(window.type).thenReturn(AccessibilityWindowInfo.TYPE_APPLICATION)
        `when`(window.isFocused).thenReturn(true)
        `when`(window.root).thenReturn(root)
        Shadow.extract<ShadowAccessibilityService>(service).setWindows(listOf(window))
        ReflectionHelpers.callInstanceMethod<Any?>(service, "onServiceConnected")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        assertEquals("A visible question.", SenseContextCache.getSnapshot().text)
    }

    @After fun tearDown() = service.onDestroy()

    @Test fun idleConversationCanBeReverifiedWithoutDiscardingItsPrediction() {
        val request = SenseCompletionRequest(4, 8, "chat.test", 3, "Да,", SenseContextCache.getSnapshot())
        repeat(4) {
            ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
            SenseContextAccessibilityService.refreshIfNeeded("chat.test")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
            assertTrue(request.matchesContext(SenseContextCache.getSnapshot()))
            assertTrue(SenseContextCache.getSnapshot().isRecentFor("chat.test", 5000))
        }
    }

    @Test fun rereadingTheScreenDetectsAMessageEvenWithoutAnAccessibilityEvent() {
        val request = SenseCompletionRequest(4, 8, "chat.test", 3, "Да,", SenseContextCache.getSnapshot())
        `when`(root.text).thenReturn("A different visible question.")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        SenseContextAccessibilityService.refreshIfNeeded("chat.test")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        assertEquals("A different visible question.", SenseContextCache.getSnapshot().text)
        assertFalse(request.matchesContext(SenseContextCache.getSnapshot()))
    }

    @Test fun burstOfRequestsUsesOneScreenCaptureAndFreshSnapshotNeedsNone() {
        clearInvocations(root)
        SenseContextAccessibilityService.refreshIfNeeded("chat.test")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        verify(root, never()).text
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        repeat(100) { SenseContextAccessibilityService.refreshIfNeeded("chat.test") }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        verify(root, times(1)).text
    }

    @Test fun destroyedServiceCannotRenewOrRecaptureItsContext() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        SenseContextAccessibilityService.refreshIfNeeded("chat.test")
        service.onDestroy()
        clearInvocations(root)
        SenseContextAccessibilityService.refreshIfNeeded("chat.test")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        verify(root, never()).text
        assertFalse(SenseContextCache.getSnapshot().serviceConnected)
        assertEquals("", SenseContextCache.getSnapshot().text)
    }

    private fun windowsChanged() = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOWS_CHANGED)

    @Test fun repeatedWindowEventsDoNotInvalidateAnUnchangedConversation() {
        val request = SenseCompletionRequest(4, 8, "chat.test", 3, "Да,", SenseContextCache.getSnapshot())
        val generation = SenseContextCache.getSnapshot().generation
        repeat(100) {
            service.onAccessibilityEvent(windowsChanged())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            assertEquals(generation, SenseContextCache.getSnapshot().generation)
            assertTrue(request.matchesContext(SenseContextCache.getSnapshot()))
        }
    }

    @Test fun windowChangeReverifiesChangedMessagesBeforeReturning() {
        val request = SenseCompletionRequest(4, 8, "chat.test", 3, "Да,", SenseContextCache.getSnapshot())
        `when`(root.text).thenReturn("A new question exposed after the keyboard resized.")
        service.onAccessibilityEvent(windowsChanged())
        assertEquals("A new question exposed after the keyboard resized.", SenseContextCache.getSnapshot().text)
        assertFalse(request.matchesContext(SenseContextCache.getSnapshot()))
    }

    @Test fun changedApplicationWindowRejectsEvenIdenticalMessagesImmediately() {
        val request = SenseCompletionRequest(4, 8, "chat.test", 3, "Да,", SenseContextCache.getSnapshot())
        `when`(root.windowId).thenReturn(2)
        service.onAccessibilityEvent(windowsChanged())
        assertEquals(2, SenseContextCache.getSnapshot().windowId)
        assertFalse(request.matchesContext(SenseContextCache.getSnapshot()))
    }

    @Test fun missingApplicationWindowRemovesTheOldContextImmediately() {
        val request = SenseCompletionRequest(4, 8, "chat.test", 3, "Да,", SenseContextCache.getSnapshot())
        Shadow.extract<ShadowAccessibilityService>(service).setWindows(emptyList())
        service.onAccessibilityEvent(windowsChanged())
        assertEquals("", SenseContextCache.getSnapshot().text)
        assertFalse(request.matchesContext(SenseContextCache.getSnapshot()))
    }

    @Test fun navigationStillRejectsIdenticalConversationAfterRecapture() {
        val request = SenseCompletionRequest(4, 8, "chat.test", 3, "Да,", SenseContextCache.getSnapshot())
        service.onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
        assertFalse(request.matchesContext(SenseContextCache.getSnapshot()))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        assertEquals("A visible question.", SenseContextCache.getSnapshot().text)
        assertFalse(request.matchesContext(SenseContextCache.getSnapshot()))
    }
}
