# #61 screenshots (Android 15 emulator, 2026-10-03)

- `keyboards-dictation.png`: each keyboard's mic key opened Marmalade and the
  words landed in a Contacts field (Kokoro TTS speech into the emulator mic).
  "ASK → speak-now sheet" is AnySoftKeyboard with our voice keyboard turned
  off: its fallback opened `RecognizeSpeechActivity`; the sheet had already
  closed and returned the text. "Granted" = after the keyboard had typed into
  Marmalade STT (see README "Works with").
- `keyboards-mic-keys.png`: where each keyboard's mic key is. OpenBoard shows
  one only after the grant; Hacker's mic opens only Google voice typing;
  Simple Keyboard has none.
- `onboarding.png`: the setup guide's new steps (Mic key, Speech service,
  Done), English. A throwaway stub voice keyboard was on in the first shot, to
  show the warning.
- `onboarding-de.png`: the same steps in German (per-app locale).

Full method and results: `docs/RESEARCH-keyboards-2026-10.md` §0.
