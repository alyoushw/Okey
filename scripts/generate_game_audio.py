#!/usr/bin/env python3
"""Generate the release voice pack once. Credentials never enter Android resources."""
import argparse
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
AUDIO = ROOT / "app/src/main/assets/game_audio"


def generate(text, key, voice_id):
    body = json.dumps({
        "text": text, "model_id": "eleven_multilingual_v2",
        "voice_settings": {"stability": 0.5, "similarity_boost": 0.75, "style": 0, "speed": 1, "use_speaker_boost": True}
    }).encode()
    request = urllib.request.Request(
        f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}", data=body,
        headers={"xi-api-key": key, "Content-Type": "application/json", "Accept": "audio/mpeg"})
    with urllib.request.urlopen(request, timeout=45) as response:
        content = response.read()
        if not content or "audio/" not in response.headers.get("Content-Type", ""):
            raise ValueError("Voice provider returned no audio")
        return content


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Check the pack without generating or charging credits")
    args = parser.parse_args()
    manifest = json.loads((AUDIO / "manifest.json").read_text())
    missing = [name for name in manifest if not (AUDIO / f"{name}.mp3").is_file() or (AUDIO / f"{name}.mp3").stat().st_size == 0]
    if args.check:
        if missing:
            print("Missing game audio: " + ", ".join(missing), file=sys.stderr)
            return 1
        print("Voice pack complete")
        return 0
    key = os.environ.get("ELEVENLABS_API_KEY", "")
    voice = os.environ.get("ELEVENLABS_VOICE_ID", "ECc3xM44reI7J5jdm8jB")
    if not key:
        print("Set ELEVENLABS_API_KEY in the local environment; do not put it in source control.", file=sys.stderr)
        return 1
    for name in missing:
        try:
            audio = generate(manifest[name], key, voice)
        except (urllib.error.URLError, ValueError):
            print(f"Could not generate {name}; check voice access and provider credits.", file=sys.stderr)
            return 1
        temporary = AUDIO / f"{name}.tmp"
        temporary.write_bytes(audio)
        temporary.replace(AUDIO / f"{name}.mp3")
        print(f"Saved {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
