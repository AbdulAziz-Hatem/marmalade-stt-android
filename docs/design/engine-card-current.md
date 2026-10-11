# Engine card: current layout (#27, before the design lab)

**Superseded (2026-09-28):** Max picked A1 cards, A3 rows under "Other
engines", the B1 dialog and C1 onboarding from `engine-cards-lab.html`. This
file records the card as it was before, for the lab's A0.

What the Engines tab card looks like as built on `child/23-24-speed`, so the
design lab can reproduce it. Code: `app/src/main/java/app/marmalade/stt/ui/screen/`
`EnginesScreen.kt` (`EngineCard`), `EngineSpecColumn.kt`, `EngineBadges.kt`,
`EngineParts.kt` (`EngineActionRow`). Screenshots (emulator, 1080×2400,
420 dpi), kept outside the repo in `shots27/` (`engines-light.png`,
`engines-dark.png`, `engines-japanese-light.png`, `engines-light-empty.png`).

## List

- `LazyColumn`, padding 16 dp sides, 8 dp top, 16 dp bottom, 12 dp between cards.
- Order: the engine recommended for this phone first, then the other user
  engines, then (Developer engines on) a "Developer engines" header
  (`titleSmall`, 12 dp above) and the developer engines. Catalog order within
  each group.
- User engines: Parakeet TDT 0.6B v3, Nemotron 3.5 Streaming (560 ms),
  Distil-Whisper Small (English), Whisper Tiny (English). Engines over the
  phone's speed budget are hidden unless in use or recommended.

## Card

`OutlinedCard`, `shapes.medium` = 16 dp corners, full width. Inner padding
16 dp sides, 4 dp top (the 48 dp info button sets the header height), 16 dp
bottom.

Unselected: page fill (`surface`: #FFF7ED light, #1C1917 dark), 1 dp default
outline (`outlineVariant`: #D6D3D1 light, #3D3835 dark).
Selected (in use): fill `surfaceContainer` (#FFEDD5 light, #292524 dark),
content colour `onSurface` (it was grey `onSurfaceVariant` in light mode until
2026-09-27, the "disabled-looking" text the lab showed), 2 dp outline in `onPrimaryContainer` (#4A1808 rich dark brown light, #FED7AA
toast dark). The whole card is the radio button (`selectable`, role
RadioButton) when installed; tapping it selects the engine.

Top to bottom:

1. **Header row**: engine name (`titleMedium`, SemiBold, Manrope; fills the
   row) + an ⓘ `IconButton` (outlined Info icon, `onSurfaceVariant`, shifted
   12 dp right to align with the padding). ⓘ opens the engine details.
2. **Body row** (top-aligned): a left column (weight 1) and, 12 dp to its
   right, the spec column (fixed 120 dp).
   - Left column:
     - description (`bodySmall`, `onSurfaceVariant`, max 3 lines, ellipsis)
     - 8 dp, then **badges** (only those that apply) as a `FlowRow` of chips,
       6 dp apart. Chip: `shapes.small` (12 dp corners), fill
       `surfaceVariant` (#FFEDD5 / #3D3835), or `surface` on the selected
       card, text `labelMedium` `onSurfaceVariant`, padding 8×4 dp. Labels:
       "Recommended for this phone", "Developer", "Streaming".
     - 8 dp, then the **language note**, when the engine doesn't transcribe
       the user's language (`labelMedium` SemiBold, `onSurface`): "Doesn't
       transcribe Japanese", and on the card in use "Doesn't transcribe
       Japanese. Nemotron 3.5 Streaming (560 ms) does."
     - 8 dp, then the **size line** (`labelMedium`, `onSurfaceVariant`): "104 MB
       on your phone" when installed, "682 MB download" while downloading or
       paused; left out when the card shows the Download button (which
       carries the size).
   - Spec column: three rows, each an overline label (`labelSmall`,
     uppercase, `onSurfaceVariant`), 2 dp, then the value; 4 dp vertical
     padding per row.
     - **SPEED**: a four-segment meter, each segment 6×12 dp, 2 dp corners,
       3 dp apart. Lit segments = tier (4 fastest, 3 fast, 2 moderate, 1
       slow), in a traffic light: 4 green #16A34A / #4ADE80, 3 lime
       #65A30D / #A3E635, 2 amber #D97706 / #FBBF24, 1 red #DC2626 /
       #EF4444 (light / dark); unlit `outlineVariant`. Slow adds "May be slow"
       under it (`labelMedium`, `onSurfaceVariant`). Unmeasured engine: "Not
       measured".
     - **QUALITY**: one word (`titleSmall` Bold, `onSurface`): Best / Great /
       Good / Fair / Basic, plus " (English)" when it was measured in another
       language than the user's.
     - **LANGUAGES**: one language's name, or "25 languages ›" (a chevron,
       16 dp) that opens a dialog listing them.
3. **Action area**, 12 dp below:
   - Not installed / downloading / paused / failed / damaged: an install
     status line (`EngineStatus`: the download progress, or what went wrong)
     when not "not installed", then a right-aligned `FlowRow` of buttons.
     Download is the filled accent-soft button "Download · 682 MB" (fill
     `primaryContainer`: toast #FED7AA light, rich brown #422006 dark; text
     `onPrimaryContainer`). Others (Cancel, Resume, Retry, Remove download,
     Download again) are `OutlinedButton`s.
   - Installed (8 dp below the body): a row with a `RadioButton` (selected
     colour `onPrimaryContainer`), 8 dp, "In use" or "Use this engine"
     (`bodyMedium`, `onSurface`), then a right-aligned `OutlinedButton`
     "Remove". Outlined buttons (`MarmaladeOutlinedButton`, app-wide) have
     `onPrimaryContainer` text: rich dark brown #4A1808 in light, toast
     #FED7AA in dark (they were Material's orange `primary` in light mode
     until 2026-09-27).

## Engine details

Opened by ⓘ; was a bottom sheet, now a centered dialog (Max, 2026-09-27).
Content: name (`titleLarge`), badges, full description, install status and
actions (Check files, Remove when installed), then rows: Language for this
engine (developer override, Developer engines on only), Speed (tier phrase,
"N× the decoding time of Distil-Whisper Small on a Pixel 8a (estimated)",
"This phone: N× a Pixel 8a"), Quality (word + where measured), Languages (all
names), Download size / size on phone, License (opens the text), Source.

## What Max said

"Look off" (2026-09-27). The lab picks the variant; the data behind each
field (speed tier, quality stars, languages, recommendation, language note)
stays as it is.
