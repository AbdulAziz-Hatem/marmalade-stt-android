# Privacy Policy — Marmalade STT

_Last updated: 2026-10-08_

Marmalade STT is a **speech-to-text app that works entirely on your
device**. What you say is turned into text on your phone. Your voice and
your words are never sent anywhere, and there is no analytics, tracking,
or account of any kind.

## Summary

- **Your audio never leaves your phone.** Speech is recognized on the
  device. Audio is processed in memory and is never saved to storage or
  uploaded.
- **No cloud speech service.** Recognition never uses a cloud or online
  speech service, including Google's. There are no API keys to enter and
  no accounts to sign in to.
- **We collect nothing.** No analytics, tracking, telemetry, crash
  reporting, ads, or accounts. There is no Marmalade server, so we could
  not collect your data even if we wanted to.
- **The internet is used only to download speech models.** Nothing about
  you or what you say is sent.

## What stays on your device

- **Your voice.** Audio from the microphone is held in memory only while
  it is being recognized, then discarded. It is never written to storage,
  never sent over the network, and never kept as a recording.
- **The text you dictate.** It goes to the app or keyboard you are
  dictating into, and nowhere else.
- **Your settings.** They are stored in app-private storage on your phone.
- **Your keyboards.** The setup guide looks at which keyboards are installed
  and turned on, to tell you how each one's mic key gets to Marmalade. It
  reads this on the phone, keeps nothing and sends it nowhere.

The app turns Android backup off, so none of this is copied to a cloud
backup or moved to a new phone during device transfer.

## Network activity

When you download a speech model, the app fetches the model files over
HTTPS from fixed, pinned addresses listed in the app. Each file is
checked against a pinned SHA-256 hash before it is used. These are ordinary file downloads: no information
about you or your speech is sent beyond what any HTTPS download needs.
Once a model is installed, recognition works fully offline.

The models are published as release files on GitHub, so GitHub serves
these downloads and handles each request (for example, its server logs)
under its own privacy policy.

Apart from model downloads, the app contacts no server. Marmalade has no
server of its own, and recognition never uses a cloud or online speech
service, Google's included.

Links in the app open in your browser, such as the ones under Settings →
Support (Support development, More Marmalade, Report a bug) and the source
and license links on the open-source licenses screen. Report a bug opens a
new GitHub issue with the app version, the build (F-Droid or Play), the
Android version and the phone model already filled in, so these are part of
the page address your browser loads. You decide whether to submit the issue.

## Permissions and why they are needed

- **`INTERNET`**: only to download speech models, as described above.
- **Microphone (`RECORD_AUDIO`)**: to hear you while you dictate. The
  microphone is used only while dictation is active, and Android asks for
  your permission before the first use.
- **Foreground service (`FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_DATA_SYNC`)**: keeps a model download going when the
  screen turns off or you switch apps. It runs only while a download does.

## Deleting your data

There is nothing on our side to delete, because nothing is sent to us.
Everything the app stores (your settings and downloaded models) lives in
app-private storage on your phone. Uninstalling the app, or clearing its
storage in Android's app settings, removes all of it.

## Children's privacy

Marmalade STT collects no data from anyone, including children, and
contains no ads and no in-app purchases.

## Changes to this policy

If this policy changes, the updated version will be published in the app
repository with a new "Last updated" date.

## Contact

Questions about privacy can be sent to:

> **maxwellw@posteo.com**
