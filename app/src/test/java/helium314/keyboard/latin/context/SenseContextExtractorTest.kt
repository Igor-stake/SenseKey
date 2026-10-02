// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.context

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class SenseContextExtractorTest {
    private fun node(text: String? = null, visible: Boolean = true,
            editable: Boolean = false, password: Boolean = false,
            description: String? = null, children: List<AccessibilityNodeInfo> = emptyList()
    ): AccessibilityNodeInfo = mock(AccessibilityNodeInfo::class.java).also {
        `when`(it.text).thenReturn(text)
        `when`(it.contentDescription).thenReturn(description)
        `when`(it.isVisibleToUser).thenReturn(visible)
        `when`(it.isEditable).thenReturn(editable)
        `when`(it.isPassword).thenReturn(password)
        `when`(it.childCount).thenReturn(children.size)
        children.forEachIndexed { index, child -> `when`(it.getChild(index)).thenReturn(child) }
    }

    @Test fun hiddenTextIsExcludedButVisibleDescendantsCanBeRead() {
        val root = node("Hidden history", visible = false,
            children = listOf(node("Visible message"), node("Offscreen", visible = false)))
        assertEquals("Visible message", SenseContextExtractor.extract(root))
    }

    @Test fun editableAndPasswordSubtreesAreExcluded() {
        val draft = node("Draft", editable = true, children = listOf(node("Draft child")))
        val password = node("Secret", password = true, children = listOf(node("Secret child")))
        val root = node(children = listOf(draft, password, node("Incoming message")))
        assertEquals("Incoming message", SenseContextExtractor.extract(root))
        verify(draft, never()).getChild(anyInt())
        verify(password, never()).getChild(anyInt())
    }

    @Test fun contentDescriptionIsUsedWhenVisibleTextIsMissing() {
        assertEquals("Message from Alice", SenseContextExtractor.extract(
            node(" ", description = "  Message  from Alice  ")))
    }

    @Test fun consecutiveDuplicatesAreCollapsedWithoutDroppingLaterReplies() {
        val root = node(children = listOf(node("Yes"), node("Yes"), node("Question"), node("Yes")))
        assertEquals("Yes\nQuestion\nYes", SenseContextExtractor.extract(root))
    }

    @Test fun messageContainingPipeCharactersIsPreserved() {
        assertEquals("A | B", SenseContextExtractor.extract(node(" A | B ")))
    }

    @Test fun singleCharacterRepliesArePreserved() {
        assertEquals("?", SenseContextExtractor.extract(node("?")))
    }

    @Test fun characterLimitDoesNotSplitEmojiSurrogatePairs() {
        val text = "x".repeat(SenseContextExtractor.MAX_CHARS - 1) + "\uD83D\uDE00"
        val output = SenseContextExtractor.extract(node(text))
        assertTrue(output.length <= SenseContextExtractor.MAX_CHARS)
        assertFalse(output.last().isHighSurrogate())
        assertEquals(SenseContextExtractor.MAX_CHARS - 1, output.length)
    }

    @Test fun extremelyWideTreeCannotCreateAnUnboundedQueue() {
        val root = node()
        val child = node("Repeated label")
        `when`(root.childCount).thenReturn(100_000)
        `when`(root.getChild(anyInt())).thenReturn(child)
        assertEquals("Repeated label", SenseContextExtractor.extract(root))
        verify(root, times(SenseContextExtractor.MAX_NODES - 1)).getChild(anyInt())
    }
}
