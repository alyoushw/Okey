"""Optional name-only voice service. Run behind an HTTPS reverse proxy.
No API credential is sent to the app. One process owns the shared quota/cache.
"""
from collections import defaultdict, deque
from hashlib import sha256
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import sys
from threading import Lock
import time
import urllib.error

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from generate_game_audio import generate

PREFIX, SUFFIX = "يلا يا ", "، دورك"
LOCK = Lock()
REQUESTS = defaultdict(deque)
DAILY = [0, 0]


def validate_text(value):
    if not isinstance(value, str) or not value.startswith(PREFIX) or not value.endswith(SUFFIX):
        raise ValueError("Only player names are accepted")
    name = value[len(PREFIX):-len(SUFFIX)]
    if not 1 <= len(name) <= 30 or not name.strip() or not all(
        c.isalpha() or c.isdecimal() or c in " '-" or "\u064b" <= c <= "\u065f" for c in name
    ):
        raise ValueError("Invalid name")
    return value


class Handler(BaseHTTPRequestHandler):
    def reply(self, code, data, kind="application/json"):
        self.send_response(code)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        if self.path != "/speech":
            self.reply(404, b'{}')
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 2048:
                raise ValueError("Invalid request size")
            text = validate_text(json.loads(self.rfile.read(size)).get("text"))
        except (ValueError, AttributeError, UnicodeError):
            self.reply(400, b'{"detail":"Invalid player name"}')
            return
        key = os.environ.get("ELEVENLABS_API_KEY", "")
        voice = os.environ.get("ELEVENLABS_VOICE_ID", "ECc3xM44reI7J5jdm8jB")
        if not key:
            self.reply(503, b'{"detail":"Voice service unavailable"}')
            return
        directory = Path(os.environ.get("VOICE_CACHE_DIR", "work/voice-cache"))
        directory.mkdir(parents=True, exist_ok=True)
        cache = directory / (sha256((voice + text).encode()).hexdigest() + ".mp3")
        # Do not trust X-Forwarded-For supplied by clients. The reverse proxy must also rate limit.
        with LOCK:
            now = time.monotonic()
            ip = self.client_address[0]
            recent = REQUESTS[ip]
            while recent and recent[0] <= now - 60:
                recent.popleft()
            if len(recent) >= 20:
                self.reply(429, b'{"detail":"Try again later"}')
                return
            recent.append(now)
            if cache.exists():
                self.reply(200, cache.read_bytes(), "audio/mpeg")
                return
            day = int(time.time() // 86400)
            if DAILY[0] != day:
                DAILY[:] = [day, 0]
                REQUESTS.clear()
            if DAILY[1] >= int(os.environ.get("VOICE_DAILY_LIMIT", "100")):
                self.reply(429, b'{"detail":"Daily voice limit reached"}')
                return
            DAILY[1] += 1
            try:
                content = generate(text, key, voice)
                cache.write_bytes(content)
            except (urllib.error.URLError, ValueError, OSError):
                self.reply(502, b'{"detail":"Could not prepare voice"}')
                return
            self.reply(200, content, "audio/mpeg")

    def log_message(self, *args):
        pass  # Avoid recording player names or request bodies.


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(os.environ.get("PORT", "8080"))), Handler).serve_forever()
