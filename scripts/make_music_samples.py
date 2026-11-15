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
NAMES = ["حسين", "مسلم", "عماد"]

# Variations on the two approved ideas: Gulf zaffa drums, and stadium drums.
SOUND_EFFECTS = {
    "beat_zaffa_fast": "Fast Gulf Arabic zaffa wedding procession, very heavy tabl drums and tambourines, darbuka rolls, "
                       "rhythmic group handclaps, loud joyful ululation, festive and energetic, no vocals",
    "beat_zaffa_big": "Big Khaleeji celebration drum ensemble, deep tabl hits, mirwas drums, fast clapping, "
                      "crowd cheering and whistling, triumphant, no vocals",
    "beat_stadium_war": "Huge stadium war drums, thunderous taiko hits, stomp stomp clap, crowd roaring, "
                        "air horn, champion celebration, very energetic",
    "beat_stadium_brass": "Stadium victory anthem, marching drumline, brass fanfare stabs, crowd chanting hey hey, "
                          "confetti moment, epic and hype",
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
    for index, player in enumerate(NAMES, 1):
        jobs.append((f"voice_announcer_{index}", f"/text-to-speech/{DEFAULT_VOICE_ID}", {
            "text": f"مبرووووك الفوز يا {player}!", "model_id": "eleven_multilingual_v2",
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
