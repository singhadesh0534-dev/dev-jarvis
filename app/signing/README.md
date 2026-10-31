# Signing key (one-time setup for live updates)

Android installs an update only if it is signed with the **same key** as the installed app. So every JARVIS build
must use one stable key. That key must stay private (never commit it — the repo is public), so it lives in a
GitHub secret:

1. Get the key file (`jarvis-personal.jks`) — Claude generated one for you, or make your own:
   `keytool -genkeypair -keystore jarvis-personal.jks -storepass jarvis-personal -keypass jarvis-personal -alias jarvis -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=JARVIS"`
2. Base64 it: `base64 -w0 jarvis-personal.jks` (Mac: `base64 -i jarvis-personal.jks`).
3. GitHub → repo → **Settings → Secrets and variables → Actions → New repository secret**
   - Name: `JARVIS_KEYSTORE_B64`, Secret: the base64 text.
   - (Only if you chose your own passwords/alias: `JARVIS_KEYSTORE_PASSWORD`, `JARVIS_KEY_ALIAS`, `JARVIS_KEY_PASSWORD`.)
4. Keep the `.jks` safe somewhere (Google Drive / laptop). Lose it = one uninstall + reinstall later.

Laptop builds: `export JARVIS_KEYSTORE_PATH=/path/to/jarvis-personal.jks` before `./gradlew`.

Without the secret, CI still builds, but with a throwaway key per run — fresh installs work, updates over an older
build fail with "App not installed".
