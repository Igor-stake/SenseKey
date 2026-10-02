// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import helium314.keyboard.latin.R;
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
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(this);
        final int padding = (int) (16 * getResources().getDisplayMetrics().density);
        final ScrollView scroll = new ScrollView(this);
        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        setContentView(scroll);

        final TextView explanation = new TextView(this);
        explanation.setText(R.string.sense_completion_setup_description);
        explanation.setTextSize(16);
        content.addView(explanation);
        final Switch enabled = new Switch(this);
        enabled.setText(R.string.sense_completion_enabled);
        enabled.setChecked(prefs.getBoolean(SenseCompletionClient.PREF_ENABLED, false));
        enabled.setPadding(0, padding, 0, padding);
        content.addView(enabled);

        final TextView addressLabel = new TextView(this);
        addressLabel.setText(R.string.sense_completion_address);
        content.addView(addressLabel);
        final EditText address = new EditText(this);
        address.setId(android.view.View.generateViewId());
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
        model.setId(android.view.View.generateViewId());
        model.setSingleLine(true);
        model.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        model.setHint(R.string.sense_completion_model_auto);
        model.setText(prefs.getString(SenseCompletionClient.PREF_MODEL, ""));
        content.addView(model);
        modelLabel.setLabelFor(model.getId());

        final TextView status = new TextView(this);
        status.setPadding(0, padding, 0, padding);
        content.addView(status);
        final Button check = new Button(this);
        check.setText(R.string.sense_completion_check);
        content.addView(check);
        check.setOnClickListener(view -> {
            final String endpoint = address.getText().toString();
            check.setEnabled(false);
            status.setText(R.string.sense_completion_checking);
            worker.execute(() -> {
                String discovered = "";
                try { discovered = new SenseCompletionClient().discoverModel(endpoint, cancellation); }
                catch (Exception ignored) { /* Never include server text or private paths in errors. */ }
                final String result = discovered;
                handler.post(() -> {
                    if (destroyed) return;
                    check.setEnabled(true);
                    status.setText(result.isEmpty() ? getString(R.string.sense_completion_not_running)
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
                    .putString(SenseCompletionClient.PREF_MODEL, chosenModel).apply();
            Toast.makeText(this, R.string.sense_completion_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override public void onDestroy() {
        destroyed = true;
        cancellation.cancel();
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
