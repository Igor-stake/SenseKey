// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import helium314.keyboard.latin.R;
import helium314.keyboard.keyboard.KeyboardSwitcher;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.utils.DeviceProtectedUtils;

/** Pre-alpha setup. Checking the model sends no editor or conversation data. */
public final class SenseCompletionSettingsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final SenseCompletionClient.Cancellation cancellation = new SenseCompletionClient.Cancellation();
    private boolean destroyed;

    @Override public void onCreate(final Bundle state) {
        super.onCreate(state);
        setTitle(R.string.sense_completion_settings);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(this);
        final int padding = (int) (16 * getResources().getDisplayMetrics().density);
        final ScrollView scroll = new ScrollView(this);
        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            final Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        setContentView(scroll);
        ViewCompat.requestApplyInsets(scroll);

        final Button back = new Button(this);
        back.setText(R.string.sense_settings_back);
        back.setOnClickListener(view -> finish());
        content.addView(back);
        final TextView heading = new TextView(this);
        heading.setText(R.string.sense_completion_settings);
        heading.setTextSize(22);
        content.addView(heading);

        final TextView explanation = new TextView(this);
        explanation.setText(R.string.sense_completion_setup_description);
        explanation.setTextSize(16);
        content.addView(explanation);
        final Switch enabled = new Switch(this);
        enabled.setId(R.id.sense_completion_enabled_switch);
        enabled.setText(R.string.sense_completion_enabled);
        enabled.setChecked(prefs.getBoolean(SenseCompletionClient.PREF_ENABLED, false));
        enabled.setPadding(0, padding, 0, padding);
        content.addView(enabled);

        final TextView addressLabel = new TextView(this);
        addressLabel.setText(R.string.sense_completion_address);
        content.addView(addressLabel);
        final EditText address = new EditText(this);
        address.setId(R.id.sense_completion_address_input);
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setText(prefs.getString(SenseCompletionClient.PREF_BASE_URL,
                SenseCompletionClient.DEFAULT_BASE_URL));
        content.addView(address);
        addressLabel.setLabelFor(address.getId());

        final TextView modelLabel = new TextView(this);
        modelLabel.setText(R.string.sense_completion_model);
        content.addView(modelLabel);
        final EditText model = new EditText(this);
        model.setId(R.id.sense_completion_model_input);
        model.setSingleLine(true);
        model.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        model.setHint(R.string.sense_completion_model_auto);
        model.setText(prefs.getString(SenseCompletionClient.PREF_MODEL, ""));
        content.addView(model);
        modelLabel.setLabelFor(model.getId());

        final SeekBar portraitHeight = addHeightControl(content,
                R.id.sense_completion_portrait_height, R.string.sense_completion_keyboard_height,
                30, 150, Math.round(Settings.readHeightScale(prefs, false) * 100), true);
        final SeekBar landscapeHeight = addHeightControl(content,
                R.id.sense_completion_landscape_height, R.string.sense_completion_keyboard_height_landscape,
                30, 150, Math.round(Settings.readHeightScale(prefs, true) * 100), true);
        final SeekBar panelHeight = addHeightControl(content,
                R.id.sense_completion_panel_height, R.string.sense_completion_panel_height,
                SenseCompletionLayout.MIN_PANEL_DP, SenseCompletionLayout.MAX_PANEL_DP,
                SenseCompletionLayout.panelHeightDp(prefs), false);

        final TextView status = new TextView(this);
        status.setPadding(0, padding, 0, padding);
        content.addView(status);
        final Button check = new Button(this);
        check.setText(R.string.sense_completion_check);
        content.addView(check);
        check.setOnClickListener(view -> {
            final String endpoint;
            try { endpoint = SenseCompletionClient.normalizeBaseUrl(address.getText().toString()); }
            catch (IOException e) {
                address.setError(getString(R.string.sense_completion_address_error));
                return;
            }
            check.setEnabled(false);
            status.setText(R.string.sense_completion_checking);
            worker.execute(() -> {
                String discovered = "";
                int error = R.string.sense_completion_not_running;
                try { discovered = new SenseCompletionClient().discoverModel(endpoint, cancellation); }
                catch (java.net.SocketTimeoutException e) { error = R.string.sense_completion_timeout; }
                catch (org.json.JSONException e) { error = R.string.sense_completion_response_error; }
                catch (IOException ignored) { /* Never include server text or private paths in errors. */ }
                final String result = discovered;
                final int errorMessage = error;
                handler.post(() -> {
                    if (destroyed) return;
                    check.setEnabled(true);
                    status.setText(result.isEmpty() ? getString(errorMessage)
                            : getString(R.string.sense_completion_model_found, result));
                });
            });
        });
        final Button save = new Button(this);
        save.setText(R.string.sense_completion_save);
        content.addView(save);
        save.setOnClickListener(view -> {
            final String endpoint;
            try { endpoint = SenseCompletionClient.normalizeBaseUrl(address.getText().toString()); }
            catch (IOException e) {
                address.setError(getString(R.string.sense_completion_address_error));
                return;
            }
            final String chosenModel = model.getText().toString().trim();
            if (chosenModel.length() > 256) {
                model.setError(getString(R.string.sense_completion_model_error));
                return;
            }
            prefs.edit().putBoolean(SenseCompletionClient.PREF_ENABLED, enabled.isChecked())
                    .putString(SenseCompletionClient.PREF_BASE_URL, endpoint)
                    .putString(SenseCompletionClient.PREF_MODEL, chosenModel)
                    .putFloat(SenseCompletionLayout.keyboardHeightKey(false),
                            (portraitHeight.getProgress() + 30) / 100f)
                    .putFloat(SenseCompletionLayout.keyboardHeightKey(true),
                            (landscapeHeight.getProgress() + 30) / 100f)
                    .putInt(SenseCompletionLayout.PREF_PANEL_HEIGHT,
                            panelHeight.getProgress() + SenseCompletionLayout.MIN_PANEL_DP).apply();
            KeyboardSwitcher.getInstance().setThemeNeedsReload();
            Toast.makeText(this, R.string.sense_completion_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private SeekBar addHeightControl(final LinearLayout parent, final int id, final int title,
            final int min, final int max, final int initial, final boolean percent) {
        final TextView label = new TextView(this);
        label.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
        parent.addView(label);
        final SeekBar control = new SeekBar(this);
        control.setId(id);
        control.setMax(max - min);
        control.setProgress(Math.max(0, Math.min(max - min, initial - min)));
        final Runnable updateLabel = () -> label.setText(getString(title) + ": "
                + (control.getProgress() + min) + (percent ? "%" : " dp"));
        control.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(final SeekBar bar, final int progress,
                    final boolean fromUser) { updateLabel.run(); }
            @Override public void onStartTrackingTouch(final SeekBar bar) {}
            @Override public void onStopTrackingTouch(final SeekBar bar) {}
        });
        updateLabel.run();
        parent.addView(control);
        label.setLabelFor(id);
        return control;
    }

    @Override public void onDestroy() {
        destroyed = true;
        cancellation.cancel();
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
