"""Builds listening audio (mp3) from shared/content.json with Piper neural voices.
Runs in GitHub Actions. Each speaker gets a different voice; lines are joined with short pauses.
Output: shared/audio/<section id>.mp3 and shared/audio/index.json (with a hash per script, so unchanged sections are skipped)."""
import hashlib, json, os, subprocess, sys, tempfile, wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "shared" / "audio"
VOICES = Path(os.environ.get("VOICES_DIR", "/tmp/voices"))
ORDER = ["en_GB-cori-medium", "en_GB-alan-medium", "en_GB-jenny_dioco-medium", "en_GB-northern_english_male-medium"]

from piper import PiperVoice  # pip install piper-tts

def load_voices():
    vs = []
    for name in ORDER:
        p = VOICES / f"{name}.onnx"
        if p.exists():
            try: vs.append((name, PiperVoice.load(str(p))))
            except Exception as e: print("skip voice", name, e)
    if not vs: sys.exit("no voices")
    return vs

def synth(voice, text):
    """Returns (sample_rate, raw int16 mono frames) for one line, across piper-tts versions."""
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f: tmp = f.name
    with wave.open(tmp, "wb") as wf:
        if hasattr(voice, "synthesize_wav"): voice.synthesize_wav(text, wf)
        else:
            wf.setnchannels(1); wf.setsampwidth(2); wf.setframerate(voice.config.sample_rate)
            voice.synthesize(text, wf)
    with wave.open(tmp, "rb") as rf: sr, data = rf.getframerate(), rf.readframes(rf.getnframes())
    os.unlink(tmp)
    return sr, data

def build(sec, voices, long_gap_after=-1):
    speakers = []
    for sp, _ in sec["script"]:
        if sp not in speakers: speakers.append(sp)
    rate, frames = None, []
    for i, (sp, line) in enumerate(sec["script"]):
        name, v = voices[speakers.index(sp) % len(voices)]
        sr, data = synth(v, line)
        if rate is None: rate = sr
        if sr != rate: continue  # all chosen voices are 22050 Hz; skip any odd one rather than mis-pitch
        frames.append(data)
        gap = 1.5 if i == long_gap_after else 0.45
        frames.append(b"\x00\x00" * int(rate * gap))
    wav = OUT / f"{sec['id']}.wav"
    with wave.open(str(wav), "wb") as wf:
        wf.setnchannels(1); wf.setsampwidth(2); wf.setframerate(rate); wf.writeframes(b"".join(frames))
    mp3 = OUT / f"{sec['id']}.mp3"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-ac", "1", "-b:a", "40k", str(mp3)], check=True)
    wav.unlink()

def main():
    d = json.loads((ROOT / "shared" / "content.json").read_text())
    OUT.mkdir(parents=True, exist_ok=True)
    idx_path = OUT / "index.json"
    idx = json.loads(idx_path.read_text()) if idx_path.exists() else {"files": {}}
    secs = list(d["listening"]) + ([d["placement"]["listening"]] if d.get("placement", {}).get("listening") else [])
    voices = load_voices()
    done = 0
    for s in secs:
        h = hashlib.sha1(json.dumps(s["script"]).encode()).hexdigest()[:12]
        if idx["files"].get(s["id"]) == h and (OUT / f"{s['id']}.mp3").exists(): continue
        print("building", s["id"]); build(s, voices, 8 if s["id"] == "pll" else -1)
        idx["files"][s["id"]] = h; done += 1
    idx["voices"] = [n for n, _ in voices]
    idx_path.write_text(json.dumps(idx, indent=1))
    print("built", done, "sections")

if __name__ == "__main__":
    main()
