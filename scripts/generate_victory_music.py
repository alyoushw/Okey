#!/usr/bin/env python3
"""Prepare the victory music tracks once with ElevenLabs sound generation.

Existing files are never regenerated (CI restores them from the music-pack release first),
so credits are spent only for a track that is missing.
"""
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_game_audio import describe  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
MUSIC = ROOT / "app/src/main/assets/victory_music"
DURATION = 12

# id -> prompt. Keep ids stable: the app stores the chosen id in its settings.
TRACKS = {
    "drums_heroes": "Epic stadium victory anthem, powerful taiko and marching drums, bold brass fanfare, "
                    "crowd cheering, triumphant, high energy, no vocals",
    "gulf_zaffa": "Energetic Gulf Arabic celebration music, fast tabl and darbuka drums, rhythmic handclaps, "
                  "festive zaffa wedding rhythm, joyful and loud, no vocals",
    "arena_hype": "Sports arena hype celebration, stomp stomp clap drum beat, heavy electric guitar riff, "
                  "crowd cheering, winning moment, energetic, no vocals",
    "epic_triumph": "Cinematic orchestral triumph, huge drum hits and timpani rolls, heroic brass and strings, "
                    "champion victory moment, powerful, no vocals",
}


def generate(prompt, key):
    body = json.dumps({"text": prompt, "duration_seconds": DURATION, "prompt_influence": 0.5}).encode()
    request = urllib.request.Request(
        "https://api.elevenlabs.io/v1/sound-generation", data=body,
        headers={"xi-api-key": key, "Content-Type": "application/json", "Accept": "audio/mpeg"})
    with urllib.request.urlopen(request, timeout=120) as response:
        content = response.read()
        if not content:
            raise ValueError("No audio returned")
        return content


def main():
    MUSIC.mkdir(parents=True, exist_ok=True)
    missing = [i for i in TRACKS if not (MUSIC / f"{i}.mp3").is_file() or (MUSIC / f"{i}.mp3").stat().st_size == 0]
    if not missing:
        print("Victory music already present")
        return 0
    key = os.environ.get("ELEVENLABS_API_KEY", "")
    if not key:
        print("Set ELEVENLABS_API_KEY to prepare victory music.", file=sys.stderr)
        return 1
    failed = False
    for track in missing:
        try:
            audio = generate(TRACKS[track], key)
        except (urllib.error.URLError, ValueError) as error:
            print(f"Could not prepare {track} ({describe(error)})", file=sys.stderr)
            failed = True
            continue
        (MUSIC / f"{track}.mp3").write_bytes(audio)
        print(f"Prepared {track}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
