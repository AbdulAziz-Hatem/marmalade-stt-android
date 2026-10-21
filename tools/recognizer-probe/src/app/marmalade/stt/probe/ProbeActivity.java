package app.marmalade.stt.probe;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.speech.ModelDownloadListener;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.TextView;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A third-party caller of Android's speech APIs (#90). It runs in its own
 * package, so the recognizer serves it the way it serves Duolingo or any
 * other app: from a bound service in another process. Every callback is
 * logged under the tag RecognizerProbe, with milliseconds since the run
 * started.
 *
 * Driven by adb (README.md):
 *   am start -S -n app.marmalade.stt.probe/.ProbeActivity --es scenario listen --es lang es-ES ...
 */
public class ProbeActivity extends Activity {
    private static final String TAG = "RecognizerProbe";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Executor mainExecutor = main::post;
    private final List<SpeechRecognizer> recognizers = new ArrayList<>();
    private TextView view;
    private long t0;
    private Bundle args;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new TextView(this);
        view.setTextIsSelectable(true);
        setContentView(view);
        run(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        run(intent);
    }

    private void run(Intent intent) {
        for (SpeechRecognizer r : recognizers) r.destroy();
        recognizers.clear();
        main.removeCallbacksAndMessages(null);
        args = intent.getExtras() != null ? intent.getExtras() : new Bundle();
        t0 = SystemClock.elapsedRealtime();
        String scenario = args.getString("scenario", "listen");
        log("PROBE scenario=" + scenario + " args=" + describe(args) + " sdk=" + Build.VERSION.SDK_INT);
        switch (scenario) {
            case "listen": {
                SpeechRecognizer r = recognizer("A");
                start(r, "A");
                afterMs("stopAfterMs", () -> { log("A stopListening"); r.stopListening(); });
                afterMs("cancelAfterMs", () -> { log("A cancel"); r.cancel(); });
                break;
            }
            case "restart": {
                // Start, cancel after gapMs, start again at once (a user tapping the mic twice).
                SpeechRecognizer r = recognizer("A");
                start(r, "A");
                main.postDelayed(() -> {
                    log("A cancel");
                    r.cancel();
                    start(r, "A2");
                }, args.getInt("gapMs", 300));
                break;
            }
            case "startTwice": {
                // A second start on the same recognizer with no cancel in between.
                SpeechRecognizer r = recognizer("A");
                start(r, "A");
                main.postDelayed(() -> start(r, "A2"), args.getInt("gapMs", 50));
                break;
            }
            case "stopRestart": {
                // stopListening, then a new start before the result came back.
                SpeechRecognizer r = recognizer("A");
                start(r, "A");
                main.postDelayed(() -> {
                    log("A stopListening");
                    r.stopListening();
                    main.postDelayed(() -> start(r, "A2"), args.getInt("restartMs", 100));
                }, args.getInt("gapMs", 3000));
                break;
            }
            case "recreate": {
                // destroy() the recognizer and start on a fresh one right away (many apps make one per tap).
                SpeechRecognizer r = recognizer("A");
                start(r, "A");
                main.postDelayed(() -> {
                    log("A destroy");
                    r.destroy();
                    recognizers.remove(r);
                    start(recognizer("B"), "B");
                }, args.getInt("gapMs", 300));
                break;
            }
            case "twoClients": {
                SpeechRecognizer a = recognizer("A");
                SpeechRecognizer b = recognizer("B");
                start(a, "A");
                start(b, "B");
                break;
            }
            case "die": {
                // The client process dies mid-session; the next run shows whether the service recovered.
                start(recognizer("A"), "A");
                main.postDelayed(() -> {
                    log("killing the probe process");
                    android.os.Process.killProcess(android.os.Process.myPid());
                }, args.getInt("gapMs", 2000));
                break;
            }
            case "available":
                log("isRecognitionAvailable=" + SpeechRecognizer.isRecognitionAvailable(this));
                if (Build.VERSION.SDK_INT >= 31) {
                    log("isOnDeviceRecognitionAvailable=" + SpeechRecognizer.isOnDeviceRecognitionAvailable(this));
                }
                log("DONE");
                break;
            case "support":
                checkSupport();
                break;
            case "download":
                triggerDownload();
                break;
            case "details":
                languageDetails();
                break;
            case "audioSource":
                audioSource();
                break;
            default:
                log("unknown scenario " + scenario);
        }
    }

    private void afterMs(String key, Runnable action) {
        int ms = args.getInt(key, -1);
        if (ms >= 0) main.postDelayed(action, ms);
    }

    private ComponentName component() {
        String target = args.getString("target", "ours");
        if (target.equals("default")) return null;
        String pkg = args.getString("pkg", "app.marmalade.stt");
        return new ComponentName(pkg, "app.marmalade.stt.recognizer.MarmaladeRecognitionService");
    }

    private SpeechRecognizer recognizer(String name) {
        ComponentName c = component();
        SpeechRecognizer r = c == null
            ? SpeechRecognizer.createSpeechRecognizer(this)
            : SpeechRecognizer.createSpeechRecognizer(this, c);
        r.setRecognitionListener(listener(name));
        recognizers.add(r);
        return r;
    }

    /** The request intent, from the run's extras. */
    private Intent request() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        if (args.containsKey("lang")) i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, args.getString("lang"));
        if (args.getBoolean("partials", true)) i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        if (args.containsKey("maxResults")) i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, args.getInt("maxResults"));
        if (args.containsKey("preferOffline")) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, args.getBoolean("preferOffline"));
        if (args.containsKey("completeSilenceMs")) {
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, (long) args.getInt("completeSilenceMs"));
        }
        if (args.getBoolean("hostileLength", false)) {
            // A class only this app has, where a number belongs: the recognizer must neither crash nor run it.
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, new ProbeOnly());
        }
        if (args.containsKey("minLengthMs")) {
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, (long) args.getInt("minLengthMs"));
        }
        if (args.getBoolean("segmented", false)) i.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE);
        if (args.containsKey("biasing")) {
            i.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS,
                new ArrayList<>(Arrays.asList(args.getString("biasing").split(","))));
        }
        if (args.containsKey("formatting")) i.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, args.getString("formatting"));
        if (args.getBoolean("hideTrailingPunct", false)) i.putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true);
        if (args.getBoolean("mask", false)) i.putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, true);
        if (args.getBoolean("detect", false)) {
            i.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true);
            if (args.containsKey("detectLangs")) {
                i.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    new ArrayList<>(Arrays.asList(args.getString("detectLangs").split(","))));
            }
        }
        return i;
    }

    private void start(SpeechRecognizer r, String name) {
        log(name + " startListening");
        r.startListening(request());
    }

    private RecognitionListener listener(String name) {
        return new RecognitionListener() {
            private int rms;

            @Override public void onReadyForSpeech(Bundle params) { log(name + " onReadyForSpeech " + describe(params)); }
            @Override public void onBeginningOfSpeech() { log(name + " onBeginningOfSpeech"); }
            @Override public void onRmsChanged(float rmsdB) {
                // Ten a second: every tenth.
                if (rms++ % 10 == 0) log(name + " onRmsChanged " + rmsdB);
            }
            @Override public void onBufferReceived(byte[] buffer) { log(name + " onBufferReceived " + buffer.length); }
            @Override public void onEndOfSpeech() { log(name + " onEndOfSpeech"); }
            @Override public void onError(int error) { log(name + " onError " + error + " " + errorName(error)); }
            @Override public void onResults(Bundle results) { log(name + " onResults " + describe(results)); }
            @Override public void onPartialResults(Bundle partial) { log(name + " onPartialResults " + describe(partial)); }
            @Override public void onEvent(int eventType, Bundle params) { log(name + " onEvent " + eventType + " " + describe(params)); }
            @Override public void onSegmentResults(Bundle segment) { log(name + " onSegmentResults " + describe(segment)); }
            @Override public void onEndOfSegmentedSession() { log(name + " onEndOfSegmentedSession"); }
            @Override public void onLanguageDetection(Bundle results) { log(name + " onLanguageDetection " + describe(results)); }
        };
    }

    private void checkSupport() {
        if (Build.VERSION.SDK_INT < 33) {
            log("checkRecognitionSupport needs API 33");
            return;
        }
        SpeechRecognizer r = recognizer("S");
        r.checkRecognitionSupport(request(), mainExecutor, new RecognitionSupportCallback() {
            @Override public void onSupportResult(RecognitionSupport s) {
                log("S onSupportResult installed=" + s.getInstalledOnDeviceLanguages()
                    + " pending=" + s.getPendingOnDeviceLanguages()
                    + " supported=" + s.getSupportedOnDeviceLanguages()
                    + " online=" + s.getOnlineLanguages());
                log("DONE");
            }
            @Override public void onError(int error) { log("S support onError " + error + " " + errorName(error)); log("DONE"); }
        });
    }

    private void triggerDownload() {
        if (Build.VERSION.SDK_INT < 34) {
            log("triggerModelDownload with a listener needs API 34");
            return;
        }
        SpeechRecognizer r = recognizer("D");
        r.triggerModelDownload(request(), mainExecutor, new ModelDownloadListener() {
            @Override public void onProgress(int completedPercent) { log("D onProgress " + completedPercent); }
            @Override public void onSuccess() { log("D onSuccess"); log("DONE"); }
            @Override public void onScheduled() { log("D onScheduled"); log("DONE"); }
            @Override public void onError(int error) { log("D onError " + error + " " + errorName(error)); log("DONE"); }
        });
    }

    private void languageDetails() {
        Intent details = RecognizerIntent.getVoiceDetailsIntent(this);
        log("getVoiceDetailsIntent=" + details + " " + (details == null ? "" : describe(details.getExtras())));
        if (details == null) {
            details = new Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS).setPackage(args.getString("pkg", "app.marmalade.stt"));
            log("sending to the package directly: " + details);
        }
        sendOrderedBroadcast(details, null, new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                log("details resultCode=" + getResultCode() + " extras=" + describe(getResultExtras(true)));
                log("DONE");
            }
        }, null, Activity.RESULT_OK, null, null);
    }

    /**
     * Streams a 16-bit PCM WAV through EXTRA_AUDIO_SOURCE (API 33+). The WAV is
     * read from this app's files dir (args "wav", default probe.wav), in real
     * time unless "fast" is set. "stallAfterMs": the writer stops writing then
     * and keeps the pipe open for a minute (a stalled caller), or with
     * "trickleMs" writes one byte that often for a minute (a caller keeping
     * the pipe busy with next to no audio; a failed write is logged with its
     * time: the reader closed); "stopAfterMs" / "cancelAfterMs":
     * stopListening / cancel after that long.
     */
    private void audioSource() {
        if (Build.VERSION.SDK_INT < 33) {
            log("EXTRA_AUDIO_SOURCE needs API 33");
            return;
        }
        final ParcelFileDescriptor[] pipe;
        final byte[] wav;
        try {
            pipe = ParcelFileDescriptor.createPipe();
            try (InputStream in = new FileInputStream(getFilesDir() + "/" + args.getString("wav", "probe.wav"))) {
                wav = in.readAllBytes();
            }
        } catch (IOException e) {
            log("audioSource setup failed: " + e);
            return;
        }
        Wav w = Wav.parse(wav);
        log("wav rate=" + w.rate + " channels=" + w.channels + " bits=" + w.bits + " dataBytes=" + w.dataLength);
        SpeechRecognizer r = recognizer("A");
        Intent i = request()
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, w.channels)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                w.bits == 8 ? android.media.AudioFormat.ENCODING_PCM_8BIT : android.media.AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, w.rate);
        log("A startListening (audio source)");
        r.startListening(i);
        afterMs("stopAfterMs", () -> { log("A stopListening"); r.stopListening(); });
        afterMs("cancelAfterMs", () -> { log("A cancel"); r.cancel(); });
        boolean fast = args.getBoolean("fast", false);
        int stallAfterMs = args.getInt("stallAfterMs", -1);
        int trickleMs = args.getInt("trickleMs", 0);
        new Thread(() -> {
            // Our read end: the recognizer has its own once the request has gone out. Closed so
            // that a write after the recognizer closes its end fails.
            SystemClock.sleep(500);
            try {
                pipe[0].close();
            } catch (IOException ignored) {
            }
            int bytesPerSecond = w.rate * w.channels * (w.bits / 8);
            int chunk = bytesPerSecond / 10;
            long started = SystemClock.elapsedRealtime();
            try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                for (int off = w.dataOffset; off < w.dataOffset + w.dataLength; off += chunk) {
                    if (stallAfterMs >= 0 && SystemClock.elapsedRealtime() - started >= stallAfterMs) {
                        log("A audio source: stalling at byte " + (off - w.dataOffset) + ", the pipe stays open"
                            + (trickleMs > 0 ? ", a byte every " + trickleMs + " ms" : ""));
                        long until = SystemClock.elapsedRealtime() + 60_000;
                        while (SystemClock.elapsedRealtime() < until) {
                            SystemClock.sleep(trickleMs > 0 ? trickleMs : 1_000);
                            if (trickleMs > 0) out.write(0);
                        }
                        break;
                    }
                    out.write(wav, off, Math.min(chunk, w.dataOffset + w.dataLength - off));
                    if (!fast) SystemClock.sleep(100);
                }
                log("A audio source: all " + w.dataLength + " bytes written, closing the pipe");
            } catch (IOException e) {
                log("A audio source write failed: " + e);
            }
        }).start();
    }

    /** A Serializable that exists only in the probe (see "hostileLength"). */
    static final class ProbeOnly implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
    }

    /** The parts of a RIFF WAV the probe needs. */
    private static final class Wav {
        int rate, channels, bits, dataOffset, dataLength;

        static Wav parse(byte[] b) {
            Wav w = new Wav();
            int p = 12;
            while (p + 8 <= b.length) {
                String id = new String(b, p, 4);
                int size = le32(b, p + 4);
                if (id.equals("fmt ")) {
                    w.channels = le16(b, p + 10);
                    w.rate = le32(b, p + 12);
                    w.bits = le16(b, p + 22);
                } else if (id.equals("data")) {
                    w.dataOffset = p + 8;
                    w.dataLength = Math.min(size, b.length - w.dataOffset);
                    break;
                }
                p += 8 + size + (size & 1);
            }
            return w;
        }

        static int le16(byte[] b, int i) { return (b[i] & 0xff) | (b[i + 1] & 0xff) << 8; }
        static int le32(byte[] b, int i) { return le16(b, i) | le16(b, i + 2) << 16; }
    }

    private static String errorName(int e) {
        switch (e) {
            case 1: return "NETWORK_TIMEOUT";
            case 2: return "NETWORK";
            case 3: return "AUDIO";
            case 4: return "SERVER";
            case 5: return "CLIENT";
            case 6: return "SPEECH_TIMEOUT";
            case 7: return "NO_MATCH";
            case 8: return "RECOGNIZER_BUSY";
            case 9: return "INSUFFICIENT_PERMISSIONS";
            case 10: return "TOO_MANY_REQUESTS";
            case 11: return "SERVER_DISCONNECTED";
            case 12: return "LANGUAGE_NOT_SUPPORTED";
            case 13: return "LANGUAGE_UNAVAILABLE";
            case 14: return "CANNOT_CHECK_SUPPORT";
            case 15: return "CANNOT_LISTEN_TO_DOWNLOAD_EVENTS";
            default: return "?";
        }
    }

    private static String describe(Bundle b) {
        if (b == null) return "null";
        StringBuilder s = new StringBuilder("{");
        for (String k : b.keySet()) {
            Object v = b.get(k);
            if (v instanceof float[]) v = Arrays.toString((float[]) v);
            s.append(k.replace("android.speech.extra.", "")).append('=').append(v).append(", ");
        }
        return s.append('}').toString();
    }

    private void log(String line) {
        String text = (SystemClock.elapsedRealtime() - t0) + "ms " + line;
        Log.i(TAG, text);
        main.post(() -> view.append(text + "\n"));
    }

    @Override
    protected void onDestroy() {
        for (SpeechRecognizer r : recognizers) r.destroy();
        super.onDestroy();
    }
}
