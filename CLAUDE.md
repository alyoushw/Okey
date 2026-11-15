# Working rules for this project

- The user speaks Arabic (Gulf dialect); reply in Arabic.
- **Every time a work session changes the app, finish by giving the user a direct APK download link.**
  Pushing any branch runs `.github/workflows/android.yml`, which builds the APK and uploads it to the
  `preview-latest` release. Wait for that run to succeed, then give this link:
  https://github.com/alyoushw/Okey/releases/download/preview-latest/Okey.apk
  If the run fails, fix it before reporting; never hand over a link to a stale build without saying so.
- Voices: `ELEVENLABS_API_KEY` is a GitHub Actions secret (must be the real `sk_...` key, not the key ID).
  CI bundles earlier player-name clips from the ElevenLabs history into `app/src/main/assets/voice_history`
  and passes the key to the debug build so new names can be generated on the phone.
