#!/usr/bin/env python3
"""Make the AI robot's movement clips (res/raw/agent_<slot>.mp4) from its picture with a free image-to-video Space.

Runs where the Space host is allowed and Sir's Hugging Face token is injected by the network proxy. The free daily GPU
allowance is small, so each run makes the clips that are still missing until the allowance runs out; run it again
another day for the rest. Not part of the app build.
"""
import io, json, os, subprocess, sys, time, urllib.request, uuid

H = "https://zerogpu-aoti-wan2-2-fp8da-aoti-faster.hf.space"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/res/drawable-nodpi/jarvis_agent.webp")
RAW = os.path.join(ROOT, "app/src/main/res/raw")
NEG = ("色调艳丽, 过曝, 静态, 细节模糊不清, 字幕, 风格, 作品, 画作, 画面, 静止, 整体发灰, 最差质量, 低质量, JPEG压缩残留, 丑陋的, 残缺的, "
       "多余的手指, 画得不好的手部, 画得不好的脸部, 畸形的, 毁容的, 形态畸形的肢体, 手指融合, 静止不动的画面, 杂乱的背景, 三条腿, 背景人很多, 倒着走, "
       "camera movement, zoom, text, extra arms")
STATIC = " The camera does not move, same scene, same robot, smooth natural motion."
# Order = what matters most first. Mode: "pingpong" = forward then backward (starts and ends on the picture's pose);
# "intro" = the full clip (the robot turns to Sir) then its second half backward, so the app can loop from 1 s on
# (AgentClips.Slot.loopFromMs) and the robot keeps facing Sir; "once" = played once (the wave).
CLIPS = [
    ("talk", "intro", "The silver humanoid robot turns its face toward the viewer and talks, its mouth moving as it speaks, explaining with its raised hand."),
    ("wave", "once", "The silver humanoid robot looks at the viewer, raises its hand and waves hello in a friendly way."),
    ("listen", "intro", "The silver humanoid robot turns its head toward the viewer and listens attentively, nodding slightly, its blue eye glowing brighter."),
    ("work", "pingpong", "The silver humanoid robot thinks with its hand at its chin, blinks and slightly tilts its head while the holographic brain on the right glows and its network lights twinkle."),
    ("happy", "pingpong", "The silver humanoid robot smiles happily and nods, its eye glowing warmly."),
    ("sad", "pingpong", "The silver humanoid robot slowly lowers its head sadly and its eye light dims."),
]
SECONDS = float(os.environ.get("CLIP_SECONDS", "2"))
STEPS = int(os.environ.get("CLIP_STEPS", "4"))


def wide_jpeg() -> bytes:
    from PIL import Image
    im = Image.open(SRC).convert("RGB")
    w, _ = im.size
    im = im.crop((0, 0, w, int(w / 1.7333))).resize((832, 480), Image.LANCZOS)
    out = io.BytesIO()
    im.save(out, "JPEG", quality=94)
    return out.getvalue()


def upload(data: bytes) -> str:
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"files\"; filename=\"robot.jpg\"\r\n"
            f"Content-Type: image/jpeg\r\n\r\n").encode() + data + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(f"{H}/gradio_api/upload", data=body, headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    return json.loads(urllib.request.urlopen(req, timeout=60).read())[0]


def generate(path: str, prompt: str) -> str | None:
    info = json.loads(urllib.request.urlopen(f"{H}/config", timeout=30).read())
    fn = [i for i, d in enumerate(info["dependencies"]) if d.get("api_name") == "generate_video"][0]
    sh = uuid.uuid4().hex[:10]
    body = {"data": [{"path": path, "meta": {"_type": "gradio.FileData"}, "orig_name": "robot.jpg"}, prompt + STATIC, STEPS, NEG, SECONDS, 1, 1, 42, False],
            "fn_index": fn, "session_hash": sh, "event_data": None, "trigger_id": None}
    urllib.request.urlopen(urllib.request.Request(f"{H}/gradio_api/queue/join", data=json.dumps(body).encode(),
                                                  headers={"Content-Type": "application/json"}), timeout=60).read()
    with urllib.request.urlopen(f"{H}/gradio_api/queue/data?session_hash={sh}", timeout=900) as r:
        for raw in r:
            line = raw.decode().strip()
            if "process_completed" in line:
                d = json.loads(line[5:])
                return d["output"]["data"][0]["url"] if d.get("success") else None
    return None


def finish(src: str, dst: str, mode: str) -> None:
    """Small H.264 the phone plays everywhere, shaped for seamless looping (see CLIPS)."""
    vf = {
        "pingpong": "[0:v]split[a][b];[b]reverse[r];[a][r]concat=n=2:v=1:a=0,format=yuv420p[v]",
        "intro": "[0:v]split[a][b];[b]trim=start_frame=16,setpts=PTS-STARTPTS,reverse[r];[a][r]concat=n=2:v=1:a=0,format=yuv420p[v]",
        "once": "[0:v]format=yuv420p[v]",
    }[mode]
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, "-filter_complex", vf, "-map", "[v]", "-an",
                    "-c:v", "libx264", "-profile:v", "baseline", "-crf", "24", "-movflags", "+faststart", dst], check=True)


def fetch(url: str, dst: str) -> bool:
    """The Space has several replicas; the file may answer 403 from the wrong one at first."""
    for attempt in range(8):
        try:
            urllib.request.urlretrieve(url, dst)
            if os.path.getsize(dst) > 10_000:
                return True
        except Exception:
            pass
        time.sleep(2 + attempt)
    return False


def main() -> int:
    os.makedirs(RAW, exist_ok=True)
    todo = [c for c in CLIPS if not os.path.exists(os.path.join(RAW, f"agent_{c[0]}.mp4"))]
    if not todo:
        print("all clips present")
        return 0
    path = upload(wide_jpeg())
    made = 0
    for name, mode, prompt in todo:
        url = generate(path, prompt)
        if not url:
            print(f"{name}: no free GPU left now (try again later)")
            break
        tmp = os.path.join(RAW, f".{name}.tmp.mp4")
        if not fetch(url, tmp):
            print(f"{name}: made but could not download, will retry next run")
            continue
        finish(tmp, os.path.join(RAW, f"agent_{name}.mp4"), mode)
        os.remove(tmp)
        made += 1
        print(f"{name}: made")
        time.sleep(2)
    left = [c[0] for c in CLIPS if not os.path.exists(os.path.join(RAW, f"agent_{c[0]}.mp4"))]
    print(f"made {made}; still missing: {', '.join(left) or 'none'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
