// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.context

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class SenseContextWindowTest {
    private fun window(pkg: String, focused: Boolean = false, active: Boolean = false,
            type: Int = AccessibilityWindowInfo.TYPE_APPLICATION
    ): Pair<AccessibilityWindowInfo, AccessibilityNodeInfo> {
        val root = mock(AccessibilityNodeInfo::class.java)
        `when`(root.packageName).thenReturn(pkg)
        val window = mock(AccessibilityWindowInfo::class.java)
        `when`(window.type).thenReturn(type)
        `when`(window.isFocused).thenReturn(focused)
        `when`(window.isActive).thenReturn(active)
        `when`(window.root).thenReturn(root)
        return window to root
    }

    @Test fun focusedAppWinsInSplitScreenEvenWhenItIsBelowAnotherApp() {
        val top = window("other.app", active = true)
        val editor = window("chat.app", focused = true)
        assertSame(editor.second, SenseContextAccessibilityService.selectApplicationRoot(
            listOf(top.first, editor.first), "sensekey"))
        verify(top.first, never()).root
    }

    @Test fun topAppWinsWhenNoWindowIsFocusedOrActive() {
        val top = window("top.app")
        val bottom = window("background.app")
        assertSame(top.second, SenseContextAccessibilityService.selectApplicationRoot(
            listOf(top.first, bottom.first), "sensekey"))
    }

    @Test fun inputMethodWindowIsSkipped() {
        val keyboard = window("sensekey", active = true, type = AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        val editor = window("chat.app", focused = true)
        assertSame(editor.second, SenseContextAccessibilityService.selectApplicationRoot(
            listOf(keyboard.first, editor.first), "sensekey"))
        verify(keyboard.first, never()).root
    }

    @Test fun sensekeySettingsDoNotExposeAnApplicationBehindThem() {
        val settings = window("sensekey", focused = true)
        val background = window("chat.app")
        assertNull(SenseContextAccessibilityService.selectApplicationRoot(
            listOf(settings.first, background.first), "sensekey"))
        verify(background.first, never()).root
    }
}
