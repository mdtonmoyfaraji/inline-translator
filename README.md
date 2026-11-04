# Inline Translator (Android)

Same features as the Chrome extension, as an Android app:

- Panel with source/target language pickers (search, favorites, recent, any custom language), swap, text box with a round arrow button
- Translate in **any text field**: a small button floats above the keyboard; the translation is inserted at the cursor
- Select text in any app → ⋮ / More → **Inline Translator**
- Any OpenAI-compatible provider (base URL + model + key), real "Test connection"
- Same prompt, URL handling, error messages and colours as the extension (light, dark, system)

## Build

Push to GitHub: the workflow in `.github/workflows/build-apk.yml` builds `app-debug.apk`
(Actions → latest run → Artifacts → `InlineTranslator-APK`). No third-party dependencies.

## First run

1. Open the app → AI connection → fill in Base URL, Model, Key → Test connection → Save.
2. "Translate in any text field" → Open Accessibility settings → Inline Translator → On.
   On Android 13+ (sideloaded APK): App info → ⋮ → **Allow restricted settings** first.
