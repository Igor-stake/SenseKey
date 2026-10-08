// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import android.content.Context
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import helium314.keyboard.latin.completion.SenseCompletionSettingsActivity
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.SettingsActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.IconOrImage
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.screens.gesturedata.END_DATE_EPOCH_MILLIS
import helium314.keyboard.settings.screens.gesturedata.TWO_WEEKS_IN_MILLIS

@Composable
fun MainSettingsScreen(
    onClickAbout: () -> Unit,
    onClickTextCorrection: () -> Unit,
    onClickPreferences: () -> Unit,
    onClickToolbar: () -> Unit,
    onClickGestureTyping: () -> Unit,
    onClickDataGathering: () -> Unit,
    onClickAdvanced: () -> Unit,
    onClickAI: () -> Unit,
    onClickAppearance: () -> Unit,
    onClickLanguage: () -> Unit,
    onClickLayouts: () -> Unit,
    onClickDictionaries: () -> Unit,
    onClickBack: () -> Unit,
) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.ime_settings),
        settings = emptyList(),
    ) {
        val context = LocalContext.current
        val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) { innerPadding ->
            Column(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SenseKeySetupActions()
                CompactPreference(
                    name = stringResource(R.string.sense_completion_settings),
                    description = stringResource(R.string.sense_completion_home_description),
                    icon = R.drawable.ic_ai_assist,
                    onClick = {
                        context.startActivity(Intent(context, SenseCompletionSettingsActivity::class.java))
                    },
                )
                CompactPreference(
                    name = stringResource(R.string.language_and_layouts_title),
                    description = enabledSubtypes.joinToString(", ") { it.displayName() },
                    icon = R.drawable.ic_settings_languages,
                    onClick = onClickLanguage,
                )
                CompactPreference(
                    name = stringResource(R.string.settings_screen_preferences),
                    icon = R.drawable.ic_settings_preferences,
                    onClick = onClickPreferences,
                )
                CompactPreference(
                    name = stringResource(R.string.settings_screen_appearance),
                    icon = R.drawable.ic_settings_appearance,
                    onClick = onClickAppearance,
                )
                CompactPreference(
                    name = stringResource(R.string.settings_screen_toolbar),
                    icon = R.drawable.ic_settings_toolbar,
                    onClick = onClickToolbar,
                )
                if (JniUtils.sHaveGestureLib) {
                    CompactPreference(
                        name = stringResource(R.string.settings_screen_gesture),
                        icon = R.drawable.ic_settings_gesture,
                        onClick = onClickGestureTyping,
                    )
                }
                // we don't even show the menu if data gathering phase ended more than 2 weeks ago
                if (JniUtils.sHaveGestureLib && System.currentTimeMillis() < END_DATE_EPOCH_MILLIS + TWO_WEEKS_IN_MILLIS) {
                    CompactPreference(
                        name = stringResource(R.string.gesture_data_screen),
                        icon = R.drawable.ic_settings_gesture,
                        onClick = onClickDataGathering,
                    )
                }
                CompactPreference(
                    name = stringResource(R.string.settings_screen_correction),
                    icon = R.drawable.ic_settings_correction,
                    onClick = onClickTextCorrection,
                )
                CompactPreference(
                    name = stringResource(R.string.settings_screen_secondary_layouts),
                    icon = R.drawable.ic_ime_switcher,
                    onClick = onClickLayouts,
                )
                CompactPreference(
                    name = stringResource(R.string.dictionary_settings_category),
                    icon = R.drawable.ic_dictionary,
                    onClick = onClickDictionaries,
                )
                CompactPreference(
                    name = stringResource(R.string.settings_screen_ai),
                    icon = R.drawable.ic_ai_assist,
                    onClick = onClickAI,
                )
                CompactPreference(
                    name = stringResource(R.string.settings_screen_advanced),
                    icon = R.drawable.ic_settings_advanced,
                    onClick = onClickAdvanced,
                )
                CompactPreference(
                    name = stringResource(R.string.settings_screen_about),
                    icon = R.drawable.ic_settings_about,
                    onClick = onClickAbout,
                )

                Spacer(Modifier.height(12.dp))
                SenseKeyBuildsButton()
            }
        }
    }
}

@Composable
private fun CompactPreference(
    name: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconOrImage(icon, name, 24)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!description.isNullOrEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        NextScreenIcon()
    }
}

@Composable
private fun SenseKeySetupActions() {
    val context = LocalContext.current
    // Read the value so returning from Android settings recomposes these actions.
    (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()?.value
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    if (!UncachedInputMethodManagerUtils.isThisImeEnabled(context, imm)) {
        CompactPreference(
            name = stringResource(R.string.sense_setup_enable),
            description = stringResource(R.string.sense_setup_enable_description),
            icon = R.drawable.ic_ime_switcher,
            onClick = { context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS)) },
        )
    } else if (!UncachedInputMethodManagerUtils.isThisImeCurrent(context, imm)) {
        CompactPreference(
            name = stringResource(R.string.sense_setup_select),
            icon = R.drawable.ic_ime_switcher,
            onClick = { imm.showInputMethodPicker() },
        )
    }
}

@Composable
private fun SenseKeyBuildsButton() {
    val context = LocalContext.current
    TextButton(
        onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://github.com/Igor-stake/SenseKey/actions/workflows/build-debug-apk.yml")))
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.sense_builds), color = brandTeal())
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
