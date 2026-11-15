#!/usr/bin/env python3
"""Generate the release voice pack once. Credentials never enter Android resources."""
import argparse
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request
import urllib.parse
import unicodedata

ROOT = Path(__file__).resolve().parents[1]
AUDIO = ROOT / "app/src/main/assets/game_audio"
NAMES = ROOT / "app/src/main/assets/voice_history"
NAME_SUFFIX = "دورك"
VICTORY_PREFIX = "مبرو"
MAX_NAME_CLIPS = 600


DEFAULT_VOICE_ID = "ECc3xM44reI7J5jdm8jB"
API = "https://api.elevenlabs.io/v1"


def normalise_text(text):
    return " ".join(unicodedata.normalize("NFKC", text or "").split())


def read_audio(request):
    with urllib.request.urlopen(request, timeout=45) as response:
        content = response.read()
        if not content or "audio/" not in response.headers.get("Content-Type", ""):
            raise ValueError("Voice provider returned no audio")
        return content


def find_existing_audio(text, key, voice_id):
    """Read existing account history first. A failed lookup must never trigger paid TTS."""
    cursor = None
    seen = set()
    for _ in range(100):
        query = {"page_size": 100, "voice_id": voice_id, "search": text}
        if cursor:
            query["start_after_history_item_id"] = cursor
        request = urllib.request.Request(API + "/history?" + urllib.parse.urlencode(query),
                                         headers={"xi-api-key": key})
        with urllib.request.urlopen(request, timeout=30) as response:
            page = json.load(response)
        if (not isinstance(page, dict) or not isinstance(page.get("history"), list)
                or not isinstance(page.get("has_more"), bool)):
            raise ValueError("Invalid history response; no new audio was generated")
        for item in page["history"]:
            if (item.get("voice_id") == voice_id
                    and normalise_text(item.get("text")) == normalise_text(text)
                    and item.get("source") in (None, "TTS")):
                item_id = item.get("history_item_id")
                if not item_id:
                    raise ValueError("History item has no audio identifier")
                url = API + "/history/" + urllib.parse.quote(item_id, safe="") + "/audio"
                return read_audio(urllib.request.Request(url, headers={"xi-api-key": key}))
        if not page.get("has_more", False):
            return None
        next_cursor = page.get("last_history_item_id")
        if not next_cursor or next_cursor in seen:
            raise ValueError("Could not finish checking voice history")
        seen.add(next_cursor)
        cursor = next_cursor
    raise ValueError("Voice history search exceeded its page limit; no new audio was generated")


def generate(text, key, voice_id=DEFAULT_VOICE_ID, *, allow_generate=True):
    existing = find_existing_audio(text, key, voice_id)
    if existing is not None:
        return existing
    if not allow_generate:
        raise ValueError("No matching recording in account history")
    body = json.dumps({
        "text": text, "model_id": "eleven_multilingual_v2",
        "voice_settings": {"stability": 0.5, "similarity_boost": 0.75, "style": 0, "speed": 1, "use_speaker_boost": True}
    }).encode()
    request = urllib.request.Request(
        API + "/text-to-speech/" + urllib.parse.quote(voice_id, safe=""), data=body,
        headers={"xi-api-key": key, "Content-Type": "application/json", "Accept": "audio/mpeg"})
    return read_audio(request)


def iter_history(key, voice_id):
    """Every TTS clip of this voice in the account history, newest first."""
    cursor, seen = None, set()
    for _ in range(100):
        query = {"page_size": 100, "voice_id": voice_id}
        if cursor:
            query["start_after_history_item_id"] = cursor
        request = urllib.request.Request(API + "/history?" + urllib.parse.urlencode(query),
                                         headers={"xi-api-key": key})
        with urllib.request.urlopen(request, timeout=30) as response:
            page = json.load(response)
        if not isinstance(page, dict) or not isinstance(page.get("history"), list):
            raise ValueError("Invalid history response")
        for item in page["history"]:
            if item.get("voice_id") == voice_id and item.get("source") in (None, "TTS"):
                yield item
        next_cursor = page.get("last_history_item_id")
        if not page.get("has_more") or not next_cursor or next_cursor in seen:
            return
        seen.add(next_cursor)
        cursor = next_cursor


def name_file(text):
    from hashlib import sha256
    return sha256(normalise_text(text).encode()).hexdigest()[:32] + ".mp3"


def bundle_player_names(key, voice_id):
    """Copy every earlier player-name announcement from history into the app. Never generates."""
    NAMES.mkdir(parents=True, exist_ok=True)
    index_path = NAMES / "index.json"
    index = json.loads(index_path.read_text()) if index_path.is_file() else {}
    fixed = {normalise_text(text) for text in json.loads((AUDIO / "manifest.json").read_text()).values()}
    added = 0
    for item in iter_history(key, voice_id):
        text = normalise_text(item.get("text"))
        if not (text.endswith(NAME_SUFFIX) or (text.startswith(VICTORY_PREFIX) and "الفوز يا" in text)) or text in fixed or text in index:
            continue
        if len(index) >= MAX_NAME_CLIPS or not item.get("history_item_id"):
            continue
        url = API + "/history/" + urllib.parse.quote(item["history_item_id"], safe="") + "/audio"
        try:
            audio = read_audio(urllib.request.Request(url, headers={"xi-api-key": key}))
        except (urllib.error.URLError, ValueError):
            continue
        filename = name_file(text)
        (NAMES / filename).write_bytes(audio)
        index[text] = filename
        added += 1
    index_path.write_text(json.dumps(index, ensure_ascii=False, indent=1, sort_keys=True))
    print(f"Player names bundled from history: {len(index)} ({added} new)")
    return 0


def describe(error):
    """Provider status and message only; the request (and its key) is never printed."""
    if isinstance(error, urllib.error.HTTPError):
        try:
            detail = error.read().decode("utf-8", "replace")[:300]
        except Exception:
            detail = ""
        return f"HTTP {error.code}: {detail}"
    return f"{type(error).__name__}: {error}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Check the pack without generating or charging credits")
    parser.add_argument("--history-only", action="store_true", help="Download existing audio without generating anything new")
    parser.add_argument("--player-names", action="store_true", help="Bundle earlier player-name clips from account history")
    args = parser.parse_args()
    if args.player_names:
        key = os.environ.get("ELEVENLABS_API_KEY", "")
        if not key:
            print("Set ELEVENLABS_API_KEY to read the voice history.", file=sys.stderr)
            return 1
        try:
            return bundle_player_names(key, os.environ.get("ELEVENLABS_VOICE_ID", DEFAULT_VOICE_ID))
        except (urllib.error.URLError, ValueError) as error:
            print(f"Could not read the voice history ({describe(error)}); continuing without bundled names.", file=sys.stderr)
            return 0
    manifest = json.loads((AUDIO / "manifest.json").read_text())
    missing = [name for name in manifest if not (AUDIO / f"{name}.mp3").is_file() or (AUDIO / f"{name}.mp3").stat().st_size == 0]
    if args.check:
        if missing:
            print("Missing game audio: " + ", ".join(missing), file=sys.stderr)
            return 1
        print("Voice pack complete")
        return 0
    if not missing:
        print("Voice pack already present; no provider requests needed")
        return 0
    key = os.environ.get("ELEVENLABS_API_KEY", "")
    voice = os.environ.get("ELEVENLABS_VOICE_ID", DEFAULT_VOICE_ID)
    if not key:
        print("Set ELEVENLABS_API_KEY in the local environment; do not put it in source control.", file=sys.stderr)
        return 1
    for name in missing:
        try:
            audio = generate(manifest[name], key, voice, allow_generate=not args.history_only)
        except (urllib.error.URLError, ValueError) as error:
            print(f"Could not generate {name} ({describe(error)}); check history permission, voice access and provider credits.", file=sys.stderr)
            return 1
        temporary = AUDIO / f"{name}.tmp"
        temporary.write_bytes(audio)
        temporary.replace(AUDIO / f"{name}.mp3")
        print(f"Saved {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
