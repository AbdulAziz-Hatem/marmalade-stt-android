# #89 screenshots (Android 15 emulator, pixel_6, 2026-10-23)

- `bar-light.png`, `bar-dark.png`: the bottom bar with Dictate, then
  Settings, selected. The selected label is bold (the library's
  `MarmaladeNavigationBarLabel`, marmalade-ui 99ca502); the pill, colours
  and sizes are as before.
- `bar-fit-fs200-de-ptBR-sv.png`: German, Brazilian Portuguese and Swedish
  (the longest labels: "Einstellungen", "Configurações", "Inställningar") at
  200 % font. Per locale: the larger display size (density 540) with Dictate
  selected, the default size with Dictate selected, and the default size
  with the long label selected. At 200 % the label is capped at 1.5× (#68),
  so "Einstellungen" and "Configurações" end in "…" in the regular weight
  too; bold loses about one more letter. Swedish fits in both weights.
- `bar-fit-fs130-fs150-de-ptBR.png`: the same labels, selected, at 130 %
  (fit) and 150 % (ellipsized, as the regular weight is at the cap).
- `qwen3-picker-en.png`, `qwen3-picker-de.png` (and `-2`, scrolled): the
  info dialog's per-engine language picker for Qwen3-ASR (Developer engines
  on), Auto-detect first, then by name in the UI language ("Cantonese"
  between "Arabic" and "Chinese"; German "Kantonesisch" under K).
- `qwen3-info-en.png`, `qwen3-info-de.png`: the info dialog with the new
  description ("recommended for Chinese and Korean").
