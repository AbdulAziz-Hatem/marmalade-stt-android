#!/usr/bin/env python3
"""#91 timing pass on the Pixel 6a: silent trials through the clip-fed mic.

Usage: pass.py <out_dir> <engine> <surface> <n> [--clips a,b,c] [--keep] [--cold-process]
surface: keyboard | popup | dictate
Each trial writes <out_dir>/<surface>-<engine>-<tag>-<i>.log (the SttTiming etc. logcat lines).
"""
import argparse, os, subprocess, sys, time, re

SERIAL = os.environ["ANDROID_SERIAL"]
P = os.environ.get("PKG", "app.marmalade.stt.speedaudit")
IME = f"{P}/app.marmalade.stt.ime.VoiceInputService"
FUTO = "org.futo.inputmethod.latin/.LatinIME"
TEST = f"{P}/app.marmalade.stt.debug.SpeechTestActivity"
POPUP = f"{P}/app.marmalade.stt.recognizer.RecognizeSpeechActivity"
MAIN = f"{P}/app.marmalade.stt.MainActivity"
SVC = f"{P}/app.marmalade.stt.debug.SpeedBenchmarkService"
CLIPS = os.environ.get("CLIPS", os.path.expanduser("~/.cache/c91/clips"))  # 16 kHz WAVs trimmed to their speech ± 50 ms, pushed as /data/local/tmp/c91-<name>.wav
TAGS = "SttTiming:V SherpaSttEngine:I SpeechEngineManager:I MicCapture:I StreamingSession:I SmartTurnModel:I NearSilence:D SpeechTest:I AndroidRuntime:E VoiceInputService:V".split()
# Tap positions on the 6a (1080x2400), checked with screenshots before the pass.
POS = {}


def adb(*args, check=True, timeout=60):
    r = subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True, text=True, timeout=timeout, stdin=subprocess.DEVNULL)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {args}: {r.stderr}")
    return r.stdout


def sh(cmd, **kw):
    return adb("shell", cmd, **kw)


def clip_len(name):
    out = subprocess.run(["soxi", "-D", f"{CLIPS}/{name}.wav"], capture_output=True, text=True).stdout
    return float(out)


def arm(name):
    sh(f"run-as {P} sh -c 'mkdir -p files/debug-mic && cp /data/local/tmp/c91-{name}.wav files/debug-mic/clip.wav'")


def disarm():
    sh(f"run-as {P} rm -f files/debug-mic/clip.wav", check=False)


class Capture:
    def __init__(self, path):
        self.path = path
        self.f = open(path, "w")
        self.p = subprocess.Popen(["adb", "-s", SERIAL, "logcat", "-v", "epoch", "-T", "1", "-s", *TAGS],
                                  stdout=self.f, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        time.sleep(0.4)

    def text(self):
        self.f.flush()
        return open(self.path, errors="replace").read()

    def wait_for(self, pattern, timeout):
        end = time.time() + timeout
        while time.time() < end:
            if re.search(pattern, self.text()):
                return True
            time.sleep(0.2)
        return False

    def stop(self):
        time.sleep(0.5)
        self.p.terminate()
        self.p.wait()
        self.f.close()


def tap(name):
    x, y = POS[name]
    sh(f"input tap {x} {y}")


def screen_on():
    sh("input keyevent KEYCODE_WAKEUP")


def cpuset_snapshot(cap_path):
    """Which cpus the app's threads may use (top-app vs foreground cpuset), appended to the log."""
    pids = sh(f"pidof {P}", check=False).split()
    out = []
    for pid in pids:
        out.append(sh(f"run-as {P} sh -c 'cat /proc/{pid}/cpuset; grep -h Cpus_allowed_list /proc/{pid}/task/*/status | sort | uniq -c'", check=False))
    with open(cap_path + ".cpuset", "w") as f:
        f.write("\n".join(out))


def trial(out, engine, surface, i, clip, tag, keep_screen=True, cpuset=False):
    name = f"{surface}-{engine}-{tag}-{i:02d}"
    path = f"{out}/{name}.log"
    screen_on()
    if clip == "none":  # the real mic, nothing played: only the mic and load stages count
        L = 2.0
    else:
        arm(clip)
        L = clip_len(clip)
    cap = Capture(path)
    t0 = time.time()
    if surface == "keyboard":
        # SpeechTestActivity is in front with FUTO up on its field.
        if IME not in sh("settings get secure enabled_input_methods"):
            print(f"{name}: our IME was disabled; enabling it again", flush=True)
            sh(f"ime enable {IME}")
        sh(f"ime set {IME}")
        if not cap.wait_for(r"clip mic open|Mic effects", 6):
            shot = path + ".nomic.png"
            sh("screencap -p /sdcard/c91.png", check=False); adb("pull", "/sdcard/c91.png", shot, check=False); sh("rm -f /sdcard/c91.png", check=False)
            print(f"{name}: the panel didn't listen; screenshot {shot}; re-preparing", flush=True)
            sh(f"ime set {FUTO}", check=False)
            sh(f"am start -n {TEST}"); time.sleep(2); tap("field"); time.sleep(1.5)
            sh(f"ime set {IME}")
            if not cap.wait_for(r"clip mic open|Mic effects", 6):
                cap.stop(); disarm(); print(f"{name}: still no listen, skipped", flush=True); return path
        time.sleep(0.3 + L + 1.0 - 0.3)  # lead, the clip, then a user's ~1 s before tapping stop
        if cpuset:
            cpuset_snapshot(path)
        tap("kb_mic")
        ok = cap.wait_for(r"keyboard delivered|listen keyboard", 40 + L)
        time.sleep(0.5)
        sh(f"ime set {FUTO}")
        cap.wait_for(r"listen keyboard", 20)
        time.sleep(0.8)
        sh("input keycombination KEYCODE_CTRL_LEFT KEYCODE_A", check=False)
        sh("input keyevent KEYCODE_DEL", check=False)
    elif surface == "popup":
        sh(f"am start -n {POPUP} -a android.speech.action.RECOGNIZE_SPEECH")
        if cpuset:
            time.sleep(0.3 + L - 0.5)
            cpuset_snapshot(path)
        ok = cap.wait_for(r"listen apps", 60 + L)
        time.sleep(1.0)
        sh("input keyevent KEYCODE_BACK", check=False)  # in case the sheet stayed
    elif surface == "dictate":
        tap("dict_mic")
        if cpuset:
            time.sleep(0.3 + L - 0.5)
            cpuset_snapshot(path)
        ok = cap.wait_for(r"dictate delivered", 60 + L)
        time.sleep(1.0)
        tap("dict_mic")  # stop
        cap.wait_for(r"listen dictate", 20)
        time.sleep(0.5)
        if "dict_clear" in POS:
            tap("dict_clear")
    cap.stop()
    disarm()
    m = re.search(r"listen \S+ .*", open(path, errors="replace").read())
    print(f"{name} [{clip}] {time.time() - t0:.1f}s: {m.group(0)[:200] if m else 'NO LISTEN LINE'}", flush=True)
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("engine")
    ap.add_argument("surface")
    ap.add_argument("n", type=int)
    ap.add_argument("--clips", default="ls05-1,ls05-2,ls05-3")
    ap.add_argument("--tag", default="std")
    ap.add_argument("--cold-process", action="store_true", help="force-stop the app before each trial")
    ap.add_argument("--gap", type=float, default=3.0)
    ap.add_argument("--pos", default="")
    ap.add_argument("--ab", default="", help="knob specs A|B, alternated per trial (A first); the tag gets -A/-B")
    ap.add_argument("--release", action="store_true", help="with --ab: release the model each trial (knobs read at load: threads, tuning)")
    a = ap.parse_args()
    for kv in filter(None, a.pos.split(";")):
        k, v = kv.split("=")
        POS[k] = tuple(int(x) for x in v.split(","))
    os.makedirs(a.out, exist_ok=True)
    clips = a.clips.split(",")
    for i in range(a.n):
        if a.cold_process:
            sh(f"am force-stop {P}")
            time.sleep(2)
            if a.surface == "keyboard":
                sh(f"am start -n {TEST}")
                time.sleep(2)
                tap("field")
                time.sleep(1)
        tag = a.tag
        if a.ab:
            specs = a.ab.split("|")
            which = i % len(specs)
            adb("shell", f"am start-foreground-service -n {SVC} --es cmd use --es knobs {specs[which]} --ez release {'true' if a.release else 'false'}")
            time.sleep(1.5)
            tag = f"{a.tag}{'AB'[which]}"
        trial(a.out, a.engine, a.surface, i, clips[(i // (len(a.ab.split('|')) if a.ab else 1)) % len(clips)], tag, cpuset=False)
        time.sleep(a.gap)


if __name__ == "__main__":
    main()
