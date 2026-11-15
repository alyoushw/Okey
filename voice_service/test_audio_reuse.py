import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import generate_game_audio as audio


class Response(io.BytesIO):
    def __init__(self, content, kind="application/json"):
        super().__init__(content)
        self.headers = {"Content-Type": kind}


def page(items, **extra):
    return Response(json.dumps({"history": items, "has_more": False, **extra}).encode())


class AudioReuseTests(unittest.TestCase):
    def test_retrieves_matching_voice_and_text_across_history_pages_without_generating(self):
        first = page([{"voice_id": "different", "text": "خالد"}], has_more=True, last_history_item_id="page-1")
        second = page([{"voice_id": audio.DEFAULT_VOICE_ID, "text": "  خالد  ", "history_item_id": "saved-id", "source": "TTS"}])
        with patch.object(audio.urllib.request, "urlopen", side_effect=[first, second, Response(b"saved-audio", "audio/mpeg")]) as request:
            self.assertEqual(b"saved-audio", audio.generate("خالد", "test-only"))
            self.assertEqual(3, request.call_count)
            self.assertTrue(all(call.args[0].get_method() == "GET" for call in request.call_args_list))
            self.assertIn("start_after_history_item_id=page-1", request.call_args_list[1].args[0].full_url)
            self.assertTrue(request.call_args_list[2].args[0].full_url.endswith("/history/saved-id/audio"))

    def test_generates_only_after_a_complete_history_search_finds_no_matching_clip(self):
        with patch.object(audio.urllib.request, "urlopen", side_effect=[page([]), Response(b"new-audio", "audio/mpeg")]) as request:
            self.assertEqual(b"new-audio", audio.generate("فهد", "test-only"))
            self.assertEqual(["GET", "POST"], [call.args[0].get_method() for call in request.call_args_list])

    def test_history_permission_failure_never_causes_paid_regeneration(self):
        with patch.object(audio.urllib.request, "urlopen", side_effect=HTTPError("https://example.invalid", 403, "Forbidden", {}, None)) as request:
            with self.assertRaises(HTTPError):
                audio.generate("خالد", "test-only")
            self.assertEqual(1, request.call_count)

    def test_malformed_history_response_does_not_trigger_generation(self):
        with patch.object(audio.urllib.request, "urlopen", return_value=Response(b"{}")) as request:
            with self.assertRaises(ValueError):
                audio.generate("خالد", "test-only")
            self.assertEqual(1, request.call_count)

    def test_history_only_never_generates_missing_audio(self):
        with patch.object(audio.urllib.request, "urlopen", return_value=page([])) as request:
            with self.assertRaises(ValueError):
                audio.generate("جديد", "test-only", allow_generate=False)
            self.assertEqual(1, request.call_count)

    def test_broken_pagination_does_not_silently_regenerate(self):
        with patch.object(audio.urllib.request, "urlopen", return_value=page([], has_more=True)) as request:
            with self.assertRaises(ValueError):
                audio.generate("خالد", "test-only")
            self.assertEqual(1, request.call_count)

    def test_invalid_existing_audio_is_not_replaced_with_paid_generation(self):
        history = page([{"voice_id": audio.DEFAULT_VOICE_ID, "text": "خالد", "history_item_id": "old"}])
        with patch.object(audio.urllib.request, "urlopen", side_effect=[history, Response(b"", "audio/mpeg")]) as request:
            with self.assertRaises(ValueError):
                audio.generate("خالد", "test-only")
            self.assertEqual(2, request.call_count)

    def test_complete_local_pack_needs_no_credentials_or_provider_calls(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            (directory / "manifest.json").write_text(json.dumps({"one": "واحد"}))
            (directory / "one.mp3").write_bytes(b"saved")
            with patch.object(audio, "AUDIO", directory), patch.dict("os.environ", {"ELEVENLABS_API_KEY": ""}), patch.object(sys, "argv", ["generate"]), patch.object(audio, "generate") as generate:
                self.assertEqual(0, audio.main())
                generate.assert_not_called()

    def test_partial_pack_only_fetches_missing_files(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            (directory / "manifest.json").write_text(json.dumps({"one": "واحد", "two": "اثنين"}))
            (directory / "one.mp3").write_bytes(b"saved")
            with patch.object(audio, "AUDIO", directory), patch.dict("os.environ", {"ELEVENLABS_API_KEY": "test-only"}), patch.object(sys, "argv", ["generate", "--history-only"]), patch.object(audio, "generate", return_value=b"existing") as generate:
                self.assertEqual(0, audio.main())
                self.assertEqual(1, generate.call_count)
                self.assertEqual("اثنين", generate.call_args.args[0])
                self.assertFalse(generate.call_args.kwargs["allow_generate"])
                self.assertEqual(b"saved", (directory / "one.mp3").read_bytes())
                self.assertEqual(b"existing", (directory / "two.mp3").read_bytes())


if __name__ == "__main__":
    unittest.main()
