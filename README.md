# Inline Translator (Android)

Same features as the Chrome extension, as an Android app.

## Features

- **Translate, tone, or both**: source/target language pickers (search, favorites, recent, any custom language), a **Tone** picker
  (Improve, Advanced, Formal, Short, Tribal, Corp, Biblical, Viking, Zen, Emojify, or your own text) and **No translation** (tone only).
  Prompts are identical to the extension.
- **Floating button** in any text field: drag it anywhere, it stays exactly where you drop it (no animation, never follows the keyboard).
- **Panel**: drag it by the small bar at its top, it remembers its position. The result is inserted at the cursor (or replaces the selection).
- **Keyboard stays open**: default typing mode types in the panel box while the keyboard stays bound to your message field (typed letters are picked up into the box, the field is put back at once). Alternatives: type in the field and the box mirrors it, or let the panel take the keyboard (Android hides it briefly then).
- **Dropdowns toggle** (tap again to close); **tap outside to close** the panel (setting, keyboard stays).
- **Trigger key**: set any key on your own keyboard (Space, a letter, punctuation, an emoji), 1–5 presses in a row, max. time between presses,
  built-in test field. The typed trigger characters are removed again and the panel opens.
- Select text in any app → ⋮ / More → **Inline Translator**.
- Any OpenAI-compatible provider (base URL + model + key), real "Test connection", light/dark/system theme.

## Build

Push to GitHub: `.github/workflows/build-apk.yml` builds `app-debug.apk`
(Actions → latest run → Artifacts → `InlineTranslator-APK`). No third-party dependencies.

## First run

1. Open the app → AI connection → Base URL, Model, Key → Test connection → Save.
2. "Translate in any text field" → Open Accessibility settings → Inline Translator → On.
   On Android 13+ (sideloaded APK): App info → ⋮ → **Allow restricted settings** first.
3. Optional: Trigger key → tap the key button → press your key on the keyboard → set presses → Save.
