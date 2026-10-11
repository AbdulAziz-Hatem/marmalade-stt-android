# Accessibility pass, October 2026 (#68)

Before 1.0.0, every screen was audited for TalkBack, 200 % text with the
largest display size, contrast (WCAG 2.2 AA: 4.5:1 text, 3:1 large text,
icons and UI parts), 48 dp touch targets, hardware keyboard and Switch
Access, and "Remove animations". The audit read every UI file (four
parallel code audits), then checked the result on an API 35 emulator with
the Play image: TalkBack 15 with its speech log, a hardware keyboard (Tab,
Enter), font scale 2.0 with density 540 (Pixel 6's largest display size),
and light and dark mode. Contrast numbers are computed from the colour
tokens in `ui/theme/Color.kt`.

Status: **Fixed** (in #68), **OK** (checked, fine), **Known** (left as
is, with the reason). P1–P6 were design proposals; Max decided them on
2026-10-06 (`docs/design/accessibility-lab.html` shows the result).

## Conformance claim

Claimable: **"Designed to meet WCAG 2.1 Level AA (per WCAG2ICT). Full
TalkBack support. An AA-contrast High contrast theme (light and dark) and
the dark theme are available in Settings → Appearance (dark follows the
system by default), and the setup guide offers High contrast on its first
screen."**

Not claimable: "WCAG 2.1 AA by default in light mode". Max's rulings
(2026-10-06, as in marmalade-tts-android's 2026-08 audit): the default
Marmalade theme's light mode keeps the brand's white on orange 500 for the
accent button and the mic (2.8:1) and orange 500 for small accent text
(section headers, text buttons, links, the slider's value: 2.6:1), except
Copy and Clear on Dictate, which keep the ink. Contrast conformance comes
from the High contrast preset and the dark theme as conforming alternate
versions (WCAG Conformance Requirement 1), one tap away on the guide's
first screen. Say so; don't hide it. `ThemePresetTest` keeps High contrast
at AA in both modes (every text role on every surface it is drawn on, every
filled role under its label, fills and edges 3:1) and dark mode at AA in
every preset. Upgrade path to a flat "conforms": a TalkBack pass on a real
phone.

Before/after screenshots of the fixes (Arabic included) were kept outside
the repo, not committed.

## Cross-cutting

| Check | Finding | Status |
|---|---|---|
| Contrast | Text drawn in `primary` (orange 500 in Marmalade light, green 600 in Forest light): 2.6:1 / 3.1:1 on cream. Material's TextButton does this by default: Skip setup, Back, every dialog's Save/Cancel/Close, the sheet's Cancel, Copy/Clear. Also section headers, the slider value, "View full license", privacy headings and links. | Known in Marmalade light (Max, 2026-10-06: "I like the orange"), except Copy/Clear (ink). Fixed everywhere else: accent text goes through `MaterialTheme.accent.text` (orange 500 only in Marmalade light; ink in High contrast, the other presets and dark mode, 6.2–12.9:1). `ButtonContrastSourceTest` fails on a bare `TextButton(` or text in `colorScheme.primary`. |
| Contrast | Error red: light 600 was 4.2:1 on dialogs, dark 500 3.5:1 ("Download failed", Delete). | Fixed: red 700 light, red 400 dark (≥ 4.8:1). `ThemePresetTest` checks every text role on every surface, in every preset. |
| Contrast | White on orange 500 (the accent button: Get started, Allow, Download in the setup guide; the light mic) is 2.8:1. Other presets pass. | Known: the brand (Max, P1). The High contrast preset uses orange 700 (5.2:1); it and dark mode are the AA versions (see the claim). |
| Contrast | No one-tap way to an AA theme. | Fixed: High contrast preset (Settings → Appearance) and the "Accessibility mode" switch on the setup guide's first screen (a card with the accessibility icon), reversible (off restores the earlier preset, kept on disk). TalkBack says the switch's own On/Off; the extra announcement was dropped as a double read. |
| Contrast | Dark-mode mic disc (rich brown on stone) is 1.2:1 against the page: only the glyph shows. | Fixed (P2-B): toast disc with toast ink in dark mode, all three mics (`micColors`). |
| Contrast | Selected tab in light mode: the pill is 1.08:1 against the bar. | Known (Max, P3): the icon and label change, TalkBack says "selected", and the tab animates. |
| Contrast | Meter segments: unlit 1.3–1.5:1 against the card; light lime/amber lit 2.7–3.0:1. | Fixed (P4): light lit segments in the 700 shades, unlit segments drawn as outlines in `outline`. `MeterContrastTest`. |
| Contrast | Switch unchecked border and outlined parts (`outline`, 2.4:1 light; dark 2.8:1 on dialogs); slider inactive track 1.4:1; checked switch and slider in orange 500 (2.6:1). | Fixed (P5): `outline` stone 500 light, stone 400 dark (≥ 3:1 on every surface). The slider's thumb and active track carry its value; the orange parts are the brand (P1). |
| Contrast | Toast button fill against the page (1.2–1.3:1). The label carries the button (WCAG 1.4.11 passes). | Known (Max, P6: leave). |
| Contrast | Disabled controls (38 % alpha). | OK (WCAG exempts them); a disabled switch row now keeps its *reason* line at full contrast (Fixed). |
| Keyboard | Focus indication was Material's 10 % overlay, barely visible on the mic and the cream page. | Fixed: the theme raises the focus layer to 32 % (`LocalRippleConfiguration`); touch never shows it. |
| Keyboard | Everything reachable with Tab/arrows/Enter; no gesture-only action. Hold-to-lock has a long-click label. | OK (emulator, hardware keyboard). |
| Motion | JarMascot honours Remove animations (#43); screen and step transitions are finite tweens Compose scales to 0. | OK |
| Motion | The mic level ring pulsed ten times a second with Remove animations on. | Fixed (`rememberMicMeter`). |
| Text scaling | Font sizes are all `sp` from the typography; screens scroll. | OK, except the items below. |
| Text scaling | In Arabic at 200 % the tab screens' titles lost their last letter ("الإعدادا…"): Compose measured the one-line title a little narrower than the Arabic run needs. English was fine. | Fixed: `TopBarTitle` fills the bar's title slot and centres its text; `DetailTopBar` gets an empty 48 dp actions slot so the title stays centred on screen (checked: glyph centre 541 of 1080 px). |
| TalkBack | Click labels ("Open in browser", "Edit") are spoken inside TalkBack's own hint ("Double-tap to …"); Polish needs an infinitive ("aby edytuj" was ungrammatical), German a noun, Korean a 하다 noun. | Fixed (pl, de, ko); rule in `docs/i18n/TRANSLATING.md`. |
| RTL | Arabic on the emulator (#69's 12 locales merged): pane title "الخطوة ٢ من ٧: السماح باستخدام الميكروفون" (TalkBack log); Show more's 48 dp target sits on the text's right edge; the engine card's spec column and the details dialog's tiles stack and mirror at 200 %; download label "تنزيل Nemotron 3.5 بحجم ٦٨٢ MB". | OK (verified) |
| TalkBack | Async changes whose text appears in a new node are missed by live regions. | Fixed with `rememberAnnouncer` (download started/ready/paused/failed, microphone result, recommendation, sheet problem, IME blocker, rule/snippet deleted, end words reverted, benchmark finished). |

## Per screen

| Screen | Check | Finding | Status |
|---|---|---|---|
| Tabs | Text | "Settings"/"Engines" broke mid-word at 200 % + largest display. | Fixed: one line, grows up to 1.5×, ellipsis past that. |
| Tabs | TalkBack | "Dictate, Tab, 1 of 3, selected". | OK (verified) |
| Dictate | TalkBack | No heading on the screen (the wordmark was hidden). | Fixed: the wordmark reads "Dictate, heading". |
| Dictate | TalkBack | Mic: "Not listening, Start dictation, Button"; status is a polite live region. | OK (verified) |
| Dictate | TalkBack | Card titles (no model, microphone) weren't headings. | Fixed |
| Dictate | TalkBack | New Finals aren't spoken (TalkBack users swipe to the transcript). | Known (Max, Q1: not in 1.0; it would talk over the user). |
| Dictate | Text | At 200 % or in landscape the mic or a card's button went off the bottom. | Fixed: the controls scroll; the transcript keeps 96 dp. 100 % portrait is pixel-identical. |
| Voice keyboard | Text | Status line cut at 2 lines with no ellipsis (errors at ≥ 140 %). | Fixed: wraps. |
| Voice keyboard | Text | Partial box a fixed 88 dp (1.5 lines at 200 %). | Fixed: three lines at any scale, 88 dp at 100 %. |
| Voice keyboard | TalkBack | Mic said only "Pause listening", no phase. | Fixed: phase state as on Dictate. |
| Voice keyboard | TalkBack | "Voice typing needs the microphone" appears unannounced. | Fixed (announced) |
| Voice keyboard | Targets | ✓ / ✕ are 48 dp IconButtons; mic 72 dp. | OK |
| Speech sheet | Text | No scroll: Done/Cancel off-screen at 200 % or in landscape. | Fixed |
| Speech sheet | Text | Partial ellipsized at the end, hiding the newest words. | Fixed: scrolls, newest visible. |
| Speech sheet | TalkBack | No pane title or heading; mic said "Stop listening" in every phase. | Fixed |
| Speech sheet | Keyboard | The scrim took keyboard focus invisibly; Enter cancelled. | Fixed |
| Speech sheet | Contrast | The mic kept its accent colours while disabled (finishing). | Fixed |
| Setup guide | TalkBack | Step position never announced. | Fixed: pane title "Step 2 of 6: <title>" per page. |
| Setup guide | TalkBack | The advance button was two nodes (filled vs text), so focus fell off when its look changed; Back came and went. | Fixed: one node each. |
| Setup guide | TalkBack | Microphone result, the recommendation and the download start/end were silent. | Fixed (announced) |
| Setup guide | TalkBack | Card titles and the recommended engine weren't headings; FUTO's "Asking…" row was two stops. | Fixed |
| Setup guide | Keyboard | "Try it here" could sit under the keyboard (edge-to-edge, no IME inset). | Fixed: `imePadding`. Not verified with a soft keyboard on the emulator. |
| Setup guide | Text | Top bar title could wrap and clip at 200 %; opened from Settings (with Close) it shrank to "Step …". | Fixed: at font scale ≥ 1.5 the step count moves above the progress bar. |
| Engines | TalkBack | A finished, paused or failed download was never announced. | Fixed (`downloadOutcomes`, unit-tested). Damaged files: Check files' snackbar. |
| Engines | Keyboard | Cancel disappears when the download pauses, so focus fell off (in the details dialog it landed on "Remove download"). | Fixed: focus moves to Resume (keyboard, Switch Access; `EngineA11yTest`). TalkBack in touch mode keeps no focus there (Compose 1.7 clickables can't take focus in touch mode) but hears "download paused". |
| Engines | TalkBack | Card buttons didn't say whose engine ("Cancel" with two downloads); once named, they read twice ("Remove Parakeet 110M. Remove"). | Fixed: "Remove, Parakeet 110M. Button" (verified). |
| Engines | TalkBack | Row progress ring said only "45 percent"; the slow meter said "May be slow on this phone" twice. | Fixed |
| Engines | TalkBack | A row with a language note hid its download status. | Fixed |
| Engines | TalkBack | Engine names, dialog titles and the tab title weren't headings. | Fixed |
| Engines | Targets | "Show more" was ~24 dp tall; "See all 25" 44 dp. | Fixed (48 dp, no visual change) |
| Engines | TalkBack | "Show more" was named "About <engine>": Voice Access users say the visible words (WCAG 2.5.3). | Fixed: "Show more, <engine>". |
| Engines | Keyboard | "See all 25" is focusable but silent for TalkBack (its click is deliberately cleared; the full list is in Show more). | Known |
| Engines | Text | Spec column (118 dp) and the dialog's three tiles broke words at 200 %. | Fixed: stacked at font scale ≥ 1.5. |
| Engines | Contrast | Language dialog's selected radio in orange 500 (2.5:1). | Known in Marmalade light (the orange accent text, Max); ink in High contrast and dark mode. |
| Engines | TalkBack | Language sheet rows: radio role, selected state, one stop each. | OK |
| Settings | TalkBack | Colour/mode chips read "checkbox"; no group; selection by colour alone (1.2:1). | Fixed: "Selected, Marmalade, Radio button, 2 of 5" (verified), check mark. |
| Settings | TalkBack | Switch rows: "On, Remove filler words, …, Switch", one stop. | OK (verified) |
| Settings | TalkBack | Slider label and value read twice. | Fixed (header hidden; the slider carries both) |
| Settings | TalkBack | Navigation rows had no role; link rows didn't say they open a browser; 🍊 read as "tangerine". | Fixed |
| Settings | TalkBack | Screen and dialog titles weren't headings. | Fixed |
| Dictionary, snippets | TalkBack | Delete was silent; field errors said only "Invalid input"; import counts silent. | Fixed |
| Dictionary | Text | Import / Copy all and Delete / Cancel couldn't wrap. | Fixed |
| End words | TalkBack | An empty edit silently reverted. | Fixed (announced) |
| Licences | TalkBack | A licence body was one utterance of several KB. | Fixed: one stop per paragraph. |
| Licences | TalkBack | Component name not a heading. | Fixed |
| Privacy | TalkBack | Each bullet was two stops ("bullet", then the text). | Fixed |
| Benchmark | TalkBack | Run progress and the end of a run were silent. | Fixed |
| Benchmark | Text | Record / Play buttons couldn't wrap. | Fixed |

## Automated checks

- `ButtonContrastSourceTest` (JVM): no bare `TextButton`, no text in `colorScheme.primary`.
- `ThemePresetTest`: text roles legible on every surface in every preset (Marmalade light's orange
  `accent.text` excepted); error red included; dark mode's filled controls (`primary`) 3:1 and their
  labels 4.5:1, control edges (`outline`) 3:1 in both modes; High contrast AA in both modes, every
  role.
- `MeterContrastTest`: every meter colour and the unlit outline 3:1 on every card.
- `DownloadOutcomesTest`: which install-state changes are announced.
- Compose semantics under Robolectric (`app/src/testDebug/.../ui/a11y/`): labels, roles,
  states, headings, 48 dp touch targets and no double-read labels for the settings rows,
  chips, engine card, row and action bar.

## Not verified

- A real phone with TalkBack (the emulator's TalkBack 15 was used; Max's Pixel is off-limits
  to child sessions). Worth a pass on the Pixel 8a before 1.0.0.
- Switch Access was not run (scanning follows the same accessibility actions TalkBack and the
  keyboard used; every control is a standard clickable/toggleable/selectable).
