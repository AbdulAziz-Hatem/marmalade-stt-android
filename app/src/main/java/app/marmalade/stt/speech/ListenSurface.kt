package app.marmalade.stt.speech

/**
 * Who is listening (#43). Each surface has its own end of speech
 * (`SpeechPrefs.endFor`), and decides itself whether a Final ends listening:
 *
 * - [DICTATE]: always [EndOfDictation.PAUSE]; only the mic tap stops.
 * - [KEYBOARD]: the voice keyboard's setting, Tap to stop by default. In the
 *   auto modes the first Final acts as Done, unless the user locked
 *   listening (`VoiceIme`).
 * - [APPS]: the speak-now sheet and the RecognitionService, Smart Turn by
 *   default. The request ends at the first Final (`OneShotSession`), unless
 *   the user locked the sheet.
 */
enum class ListenSurface { DICTATE, KEYBOARD, APPS }
