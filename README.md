# SenseKey

An experimental Android keyboard that suggests the next part of your message using conversation context and a local language model.

## Current prototype

- Sense Context captures visible text through an explicitly enabled Android Accessibility Service. Recognized WhatsApp chats can accumulate up to six screens as you scroll.
- Suggestions use the current viewport and bounded captured history. Fragment order and message authors are not always known; this is not a complete chat export.
- A dedicated client sends the draft and context to a llama.cpp server on the same phone at `127.0.0.1:8080`. It has no cloud fallback, API keys, tools, or automatic sending of messages.
- The model predicts a complete message. SenseKey checks that it preserves the exact typed beginning, then offers only the missing suffix. Swipe right to insert it.
- The suggestion panel stays open during typing. Keyboard and panel height can be adjusted in SenseKey settings.
- Empty, malformed, truncated, stale, and copied-history responses are withheld. These checks do not guarantee semantic accuracy.

The inherited manual AI tools have their own settings and optional backends. Their “About me” profile is **not used by SenseKey phrase predictions yet**. Contacts, family relationships, permanent memory, full messenger archives, and call transcripts are future work.

## Install and try

Debug APKs are produced by [Build debug APK](https://github.com/Igor-stake/SenseKey/actions/workflows/build-debug-apk.yml). Open a successful run and download `SenseKey-debug`. Debug builds are labeled **SenseKey DEV**.

Enable SenseKey as your keyboard, then enable Sense Context in Android accessibility settings. Install and run the local model separately in Termux. See [model setup and quality evaluation](tools/quality/README.md). No model weights are included in the APK.

Predictions are experimental. Their relevance and latency must be checked on the actual phone before a product demonstration. A larger model alone does not establish that they are reliable.

## Development

The debug workflow runs focused context/completion tests, an Android 15 ART smoke check, and the APK build. The pull request workflow runs the full JVM test suite. The prototype builds for `arm64-v8a`.

Model evaluation uses only invented examples. [The evaluation tools](tools/quality/) export the production prompt and response parser rather than maintaining a separate imitation. Raw outputs and review criteria are kept alongside the results.

Stored preference keys, notification-channel IDs, and registered widget component names retain some upstream identifiers so installed settings and widgets survive updates. User-facing names are SenseKey.

## License and origins

SenseKey is a fork of [Deskdrop](https://github.com/SvReenen/Deskdrop), itself based on [HeliBoard](https://github.com/Helium314/HeliBoard) and OpenBoard. It is licensed under [GPL-3.0](LICENSE). Upstream copyright notices and third-party licenses are preserved.
