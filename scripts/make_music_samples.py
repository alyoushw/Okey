#!/usr/bin/env python3
"""Generate candidate victory sounds for the owner to listen to. Nothing here goes into the app."""
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_game_audio import describe, DEFAULT_VOICE_ID  # noqa: E402

OUT = Path("samples")
API = "https://api.elevenlabs.io/v1"
NAME = "خالد"

SOUND_EFFECTS = {
    "beat_zaffa": "Gulf Arabic zaffa victory celebration, heavy tabl drums and darbuka, fast rhythmic handclaps, "
                  "joyful ululation and crowd cheering, festive and loud, no vocals",
    "beat_stadium": "Stadium victory hype, massive war drums, stomp stomp clap rhythm, crowd chanting hey, "
                    "brass stabs, energetic champion moment",
}
SONGS = {
    "song_khaleeji": f"Energetic Khaleeji celebration song, heavy tabl and darbuka drums, handclaps, a joyful male "
                     f"singer sings in Gulf Arabic: 'مبروك يا {NAME}، يا بطل الأوكي'. Festive wedding zaffa energy.",
    "song_stadium": f"Upbeat stadium victory anthem, big drums and crowd, a male singer and crowd chant in Arabic: "
                    f"'{NAME} بطل! {NAME} بطل!', triumphant and hype.",
}


def post(path, payload, key, timeout=180):
    request = urllib.request.Request(API + path, data=json.dumps(payload).encode(), headers={
        "xi-api-key": key, "Content-Type": "application/json", "Accept": "audio/mpeg"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        content = response.read()
        if not content:
            raise ValueError("No audio returned")
        return content


def main():
    key = os.environ.get("ELEVENLABS_API_KEY", "")
    if not key:
        print("ELEVENLABS_API_KEY is missing", file=sys.stderr)
        return 1
    OUT.mkdir(exist_ok=True)
    jobs = []
    for name, prompt in SOUND_EFFECTS.items():
        jobs.append((name, "/sound-generation", {"text": prompt, "duration_seconds": 15, "prompt_influence": 0.5}))
    for name, prompt in SONGS.items():
        jobs.append((name, "/music", {"prompt": prompt, "music_length_ms": 20000, "model_id": "music_v1"}))
    jobs.append(("voice_announcer", f"/text-to-speech/{DEFAULT_VOICE_ID}", {
        "text": f"مبرووووك الفوز يا {NAME}!", "model_id": "eleven_multilingual_v2",
        "voice_settings": {"stability": 0.25, "similarity_boost": 0.8, "style": 0.7, "use_speaker_boost": True}}))
    for name, path, payload in jobs:
        try:
            (OUT / f"{name}.mp3").write_bytes(post(path, payload, key))
            print(f"Made {name}")
        except (urllib.error.URLError, ValueError) as error:
            print(f"Could not make {name}: {describe(error)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
