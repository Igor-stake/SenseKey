// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion

import helium314.keyboard.latin.context.SenseContextCache
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class SenseCompletionRequestTest {
    private lateinit var request: SenseCompletionRequest
    @Before fun setUp() {
        SenseContextCache.setServiceConnected(true)
        SenseContextCache.update("chat.test", 1, "A question from this conversation.")
        request = SenseCompletionRequest(4, 8, "chat.test", 3, "Да,", SenseContextCache.getSnapshot())
    }
    @After fun tearDown() = SenseContextCache.setServiceConnected(false)

    @Test fun unchangedEditorAndConversationCanAcceptResult() {
        assertTrue(request.matchesEditor(4, 8, "chat.test", 3, 3, "Да,"))
        assertTrue(request.matchesContext(SenseContextCache.getSnapshot()))
    }
    @Test fun anotherAppFieldOrEditorSessionRejectsResult() {
        assertFalse(request.matchesEditor(4, 8, "another.app", 3, 3, "Да,"))
        assertFalse(request.matchesEditor(4, 9, "chat.test", 3, 3, "Да,"))
        assertFalse(request.matchesEditor(5, 8, "chat.test", 3, 3, "Да,"))
    }
    @Test fun newTextCursorOrSelectionRejectsResult() {
        assertFalse(request.matchesEditor(4, 8, "chat.test", 4, 4, "Да, "))
        assertFalse(request.matchesEditor(4, 8, "chat.test", 2, 2, "Да,"))
        assertFalse(request.matchesEditor(4, 8, "chat.test", 3, 4, "Да,"))
        assertFalse(request.matchesEditor(4, 8, "chat.test", 3, 3, "Нет"))
    }
    @Test fun navigationRejectsEvenIdenticalTextInSameApp() {
        SenseContextCache.clear()
        SenseContextCache.update("chat.test", 1, "A question from this conversation.")
        assertFalse(request.matchesContext(SenseContextCache.getSnapshot()))
    }
    @Test fun incomingMessageRejectsEarlierPrediction() {
        SenseContextCache.update("chat.test", 1, "A changed question from this conversation.")
        assertFalse(request.matchesContext(SenseContextCache.getSnapshot()))
    }
    @Test fun repeatedIdenticalCaptureKeepsPredictionValid() {
        SenseContextCache.update("chat.test", 1, "A question from this conversation.")
        assertTrue(request.matchesContext(SenseContextCache.getSnapshot()))
    }
    @Test fun expiredOrDisconnectedContextRejectsResult() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(16))
        assertFalse(request.matchesContext(SenseContextCache.getSnapshot()))
        SenseContextCache.setServiceConnected(false)
        assertFalse(request.matchesContext(SenseContextCache.getSnapshot()))
    }
}
