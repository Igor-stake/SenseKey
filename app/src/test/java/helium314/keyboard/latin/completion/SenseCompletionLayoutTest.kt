// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion

import android.app.Application
import android.content.Context
import helium314.keyboard.latin.settings.Settings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class SenseCompletionLayoutTest {
    @Test fun compactMigrationKeepsAlreadyShorterKeyboardAndDoesNotResetLaterChoices() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("layout-migration", Context.MODE_PRIVATE)
        prefs.edit().clear().putFloat(SenseCompletionLayout.keyboardHeightKey(true), .7f).commit()
        SenseCompletionLayout.applyInitialCompactLayout(prefs)
        assertEquals(.85f, Settings.readHeightScale(prefs, false))
        assertEquals(.7f, Settings.readHeightScale(prefs, true))
        prefs.edit().putFloat(SenseCompletionLayout.keyboardHeightKey(false), 1.1f).commit()
        SenseCompletionLayout.applyInitialCompactLayout(prefs)
        assertEquals(1.1f, Settings.readHeightScale(prefs, false))
    }

    @Test fun panelBoundsProtectAgainstCorruptedOrImportedPreferences() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("layout-panel", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertEquals(96, SenseCompletionLayout.panelHeightDp(prefs))
        prefs.edit().putInt(SenseCompletionLayout.PREF_PANEL_HEIGHT, 1).commit()
        assertEquals(80, SenseCompletionLayout.panelHeightDp(prefs))
        prefs.edit().putInt(SenseCompletionLayout.PREF_PANEL_HEIGHT, 99999).commit()
        assertEquals(144, SenseCompletionLayout.panelHeightDp(prefs))
    }
}
