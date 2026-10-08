// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion

import helium314.keyboard.latin.context.SenseContextCache
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class SenseCompletionBudgetTest {
    @Before fun setUp() {
        SenseContextCache.setServiceConnected(true)
        SenseContextCache.update("chat.test", 1, "An earlier question")
    }
    @After fun tearDown() = SenseContextCache.setServiceConnected(false)
    private fun request(draft: String = "Я думаю, ") = SenseCompletionRequest(
        4, 8, "chat.test", draft.length, draft, SenseContextCache.getSnapshot())

    @Test fun incomingContextDoesNotResetTheDeadline() {
        val first = request()
        val budget = SenseCompletionBudget(first, 1_000)
        assertEquals(40_000L, budget.remainingMillis(11_000))
        SenseContextCache.clear()
        SenseContextCache.update("chat.test", 1, "A changed question")
        val restarted = request()
        assertFalse(first.matchesContext(SenseContextCache.getSnapshot()))
        assertTrue(budget.matchesEditor(restarted.editorSession, restarted.fieldId,
            restarted.packageName, restarted.cursor, restarted.draft))
        assertEquals(0L, budget.remainingMillis(51_000))
        assertEquals(0L, budget.remainingMillis(60_000))
    }

    @Test fun newDraftOrEditorCannotReusePreviousDeadline() {
        val first = request()
        val budget = SenseCompletionBudget(first, 1_000)
        assertFalse(budget.matchesEditor(4, 8, "chat.test", 7, "Завтра "))
        assertFalse(budget.matchesEditor(5, 8, "chat.test", first.cursor, first.draft))
        assertFalse(budget.matchesEditor(4, 9, "chat.test", first.cursor, first.draft))
        assertEquals(50_000L, SenseCompletionBudget(request("Завтра "), 30_000).remainingMillis(30_000))
    }
}
