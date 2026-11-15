# Working rules for this project

- The user speaks Arabic (Gulf dialect); reply in Arabic.
- **Every time a work session changes the app, finish by giving the user a direct APK download link.**
  Start from `main`. Pushing `main` or a `ccr-*` / `claude/*` branch runs `.github/workflows/android.yml`,
  which builds the APK and uploads it to the `preview-latest` release (other branches build but never publish).
  When the work is done, open a pull request to `main` so the next session starts from it.
  **The owner has given standing permission to merge that pull request yourself** once its checks pass;
  the owner does not want to merge by hand. Merge it (normal merge, never force-push `main`) and say so. Wait for that run to succeed, then give this link:
  https://github.com/alyoushw/Okey/releases/download/preview-latest/Okey.apk
  If the run fails, fix it before reporting; never hand over a link to a stale build without saying so.
- Voices: `ELEVENLABS_API_KEY` is a GitHub Actions secret (must be the real `sk_...` key, not the key ID).
  CI bundles earlier player-name clips from the ElevenLabs history into `app/src/main/assets/voice_history`
  and passes the key to the debug build so new names can be generated on the phone.
- In-app updates: the app reads `version.json` from the `preview-latest` release and offers the new APK.
  Add a `Notes-ar:` trailer to the commit message (one Arabic line) to show what changed in the update dialog.
  Preview builds are signed with `signing/okey-preview.keystore` so updates install over the old app.
- **Never ship a new sound without the owner's approval.** Generate candidates with
  `.github/workflows/music-samples.yml` (they go to the `music-samples` release, not the app), send them to the
  owner to listen to, and only add the approved ones to the app.
- App builds and updates run only when app files change (see the `paths` filter in `android.yml`), so samples,
  posters and notes never send users an update.
