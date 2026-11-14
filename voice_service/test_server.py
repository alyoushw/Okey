import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from http.server import ThreadingHTTPServer
from server import Handler, REQUESTS, DAILY, validate_text


class VoiceServiceTests(unittest.TestCase):
    def test_rejects_arbitrary_prompts_and_unbounded_names(self):
        for text in [None, "Hello world", "يلا يا " + "أ" * 31 + "، دورك", "يلا يا <script>، دورك", "يلا يا    ، دورك"]:
            with self.assertRaises(ValueError):
                validate_text(text)
        self.assertEqual("يلا يا خالد، دورك", validate_text("يلا يا خالد، دورك"))

    def test_reuses_audio_and_enforces_quota_without_exposing_provider_errors(self):
        REQUESTS.clear()
        DAILY[:] = [0, 0]
        with tempfile.TemporaryDirectory() as directory, patch.dict("os.environ", {
            "ELEVENLABS_API_KEY": "test-only", "VOICE_CACHE_DIR": directory, "VOICE_DAILY_LIMIT": "1"
        }), patch("server.generate", return_value=b"audio") as provider:
            server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            def request(name):
                data = json.dumps({"text": f"يلا يا {name}، دورك"}).encode()
                return urlopen(Request(f"http://127.0.0.1:{server.server_port}/speech", data=data), timeout=5)
            try:
                with request("خالد") as response:
                    self.assertEqual(b"audio", response.read())
                with request("خالد") as response:
                    self.assertEqual(b"audio", response.read())
                self.assertEqual(1, provider.call_count)
                with self.assertRaises(HTTPError) as error:
                    request("فهد")
                self.assertEqual(429, error.exception.code)
                error.exception.close()
            finally:
                server.shutdown()
                server.server_close()
                thread.join()


if __name__ == "__main__":
    unittest.main()
