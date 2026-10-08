// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Export the real production prompt and run the real response parser/copy filter. */
public final class QualityBridge {
    public static void main(String[] args) throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = input.readLine()) != null) {
            JSONObject data = new JSONObject(line);
            SenseCompletionRequest request = new SenseCompletionRequest(data.getString("draft"),
                    data.getString("context"), data.getString("visible"), data.getString("language"));
            if (!data.has("raw")) {
                System.out.println(SenseCompletionClient.createPayload("sensekey", request));
                continue;
            }
            String suffix = "";
            String error = "";
            try { suffix = SenseCompletionClient.parseCompletion(data.getString("raw"), request.draft); }
            catch (org.json.JSONException e) { error = "invalid structured response"; }
            boolean echo = !suffix.isEmpty()
                    && SenseCompletionQuality.copiesHistory(suffix, request.payloadContext);
            boolean unsupported = !suffix.isEmpty() && SenseCompletionQuality.addsUnsupportedSpecifics(
                    suffix, request.payloadContext, request.draft);
            System.out.println(new JSONObject().put("suffix", echo || unsupported ? "" : suffix)
                    .put("unsupported_specifics", unsupported)
                    .put("echo", echo).put("parse_error", error));
        }
    }
}
