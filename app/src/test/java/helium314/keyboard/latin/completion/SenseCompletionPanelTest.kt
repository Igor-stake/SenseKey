// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion

import android.app.Application
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.CompletionInfo
import android.widget.TextView
import helium314.keyboard.latin.SuggestedWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class SenseCompletionPanelTest {
    private fun measure(panel: SenseCompletionPanel, height: Int) {
        panel.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
    }

    @Test fun panelAndHeaderBoundsStayStableAcrossTypingWaitingReplyAndError() {
        val context = RuntimeEnvironment.getApplication()
        val panel = SenseCompletionPanel(context, Color.BLACK, {}, {})
        val height = (96 * context.resources.displayMetrics.density).toInt()
        panel.showWords(SuggestedWords.getEmptyInstance()) {}
        measure(panel, height)
        val headerHeight = (panel.getChildAt(0) as ViewGroup).height
        panel.showContinuation()
        panel.status.text = "Waiting for the model to complete the current message"
        panel.suffix.text = "Long continuation with several words ".repeat(5)
        panel.retry.visibility = View.VISIBLE
        measure(panel, height)
        assertEquals(height, panel.height)
        assertEquals(headerHeight, panel.getChildAt(0).height)
        panel.retry.visibility = View.INVISIBLE
        panel.showWords(SuggestedWords.getEmptyInstance()) {}
        measure(panel, height)
        assertEquals(height, panel.height)
        assertEquals(headerHeight, panel.getChildAt(0).height)
    }

    @Test fun visibleWordChoiceKeepsItsOriginalMetadataAndCompletionDoesNotDuplicateIt() {
        val context = RuntimeEnvironment.getApplication()
        val panel = SenseCompletionPanel(context, Color.BLACK, {}, {})
        val info = SuggestedWords.SuggestedWordInfo(CompletionInfo(1, 0, "Example"))
        val suggestions = SuggestedWords(arrayListOf(info), null, null, false, false,
            false, SuggestedWords.INPUT_STYLE_APPLICATION_SPECIFIED, 1)
        var picked: SuggestedWords.SuggestedWordInfo? = null
        panel.showWords(suggestions) { picked = it }
        measure(panel, 96)
        fun findWord(view: View): TextView? {
            if (view is TextView && view.text.toString() == "Example") return view
            if (view is ViewGroup) for (i in 0 until view.childCount) findWord(view.getChildAt(i))?.let { return it }
            return null
        }
        val word = findWord(panel)!!
        assertTrue(word.performClick())
        assertSame(info, picked)
        panel.showContinuation()
        assertEquals(View.GONE, (word.parent as View).visibility)
    }
}
