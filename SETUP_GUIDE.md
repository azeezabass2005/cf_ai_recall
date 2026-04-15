# Sorting the caveats

**1. Android Studio first sync**
- Install Android Studio Koala+ if you don't have it.
- `File → Open` → pick `/home/rustyfola/Desktop/frontend-hassle/recall` (the repo root, not `app/`).
- Let it sync. It'll auto-download the Gradle wrapper, AGP 8.5.2, Kotlin 2.0.10, and the Compose BOM.
- If sync fails, the fix is almost always one of:
  - **AGP/Gradle mismatch** — Studio will offer "Upgrade Gradle wrapper", accept it.
  - **SDK 35 missing** — `Tools → SDK Manager → Android 15 (API 35)` → install.
  - **JDK 17 missing** — `File → Settings → Build → Gradle → Gradle JDK` → pick a 17.

**2. Wrangler version warning**
```sh
cd /home/rustyfola/Desktop/frontend-hassle/recall/worker
npm install --save-dev wrangler@4
```
Then in `wrangler.toml`, the `2026-04-01` compatibility date will be accepted without the fallback warning.

**3. D1 `database_id` placeholder** — only matters when you deploy. When you're ready:
```sh
cd worker
npx wrangler login
npx wrangler d1 create recall-db
```
Copy the `database_id` it prints into `wrangler.toml` (replace `REPLACE_ME_AFTER_wrangler_d1_create`).

**4. npm audit warnings** — transitive, not in our direct deps. Skip for MVP. Revisit when the `agents` SDK ships an update.

---

# Running it on your phone

You have two options. **Option A** is faster to set up but only works while your phone is on the same wifi as your laptop. **Option B** deploys the Worker to Cloudflare so your phone can hit it from anywhere.

## Option A — local Worker, phone on same wifi

**1. Find your laptop's LAN IP**
```sh
ip -4 addr show | grep inet
```
Look for something like `192.168.x.x` on your wifi interface. Call it `<LAN_IP>`.

**2. Point the Android app at it**

Edit `app/build.gradle.kts`, change the `defaultConfig` line:
```kotlin
buildConfigField("String", "RECALL_BASE_URL", "\"http://<LAN_IP>:8789\"")
```

**3. Start the Worker bound to all interfaces**
```sh
cd /home/rustyfola/Desktop/frontend-hassle/recall/worker
npx wrangler dev --port 8789 --ip 0.0.0.0
```
Leave it running. Test from your laptop: `curl http://<LAN_IP>:8789/health` should return JSON.

**4. Enable USB debugging on your phone**
- Settings → About phone → tap "Build number" 7 times.
- Settings → Developer options → enable "USB debugging".
- Plug into laptop. First time, accept the RSA prompt on the phone.

**5. Build and install**
- In Android Studio, your phone shows up in the device dropdown (top toolbar).
- Hit the green ▶ Run button.
- App installs and launches. You should land on the Chat screen.

**6. Test it**
Type "hi" → Send. You should see `(scaffold) you said: "hi"` come back from the Worker. If you see `(error) ...` instead, the phone can't reach the laptop — most likely your wifi network isolates clients (common on public/guest wifi) or your laptop firewall is blocking port 8789. Try `sudo ufw allow 8789/tcp` if you're on Ubuntu.

## Option B — deploy the Worker to Cloudflare

Better for showing it off / not needing the laptop on.

**1. Deploy the Worker**
```sh
cd /home/rustyfola/Desktop/frontend-hassle/recall/worker
npx wrangler login
npx wrangler d1 create recall-db
# paste the database_id into wrangler.toml
npm run db:migrate:remote
npm run deploy
```
Wrangler prints the deployed URL, something like `https://recall-worker.<your-subdomain>.workers.dev`.

**2. Smoke test it**
```sh
curl https://recall-worker.<your-subdomain>.workers.dev/health
```

**3. Point the release build at it**

In `app/build.gradle.kts`, the `release` block already has a `RECALL_BASE_URL` — replace it with your deployed URL:
```kotlin
release {
    isMinifyEnabled = false
    buildConfigField("String", "RECALL_BASE_URL", "\"https://recall-worker.<your-subdomain>.workers.dev\"")
}
```

**4. Build a release APK**

In Android Studio: `Build → Generate Signed App Bundle / APK → APK → release`. First time it'll walk you through creating a keystore — pick any passwords, save the keystore file somewhere safe.

Or from the command line:
```sh
cd /home/rustyfola/Desktop/frontend-hassle/recall
./gradlew assembleRelease
```
The APK lands in `app/build/outputs/apk/release/`.

**5. Install on your phone**
- Plug in the phone, then `adb install app/build/outputs/apk/release/app-release.apk`.
- Or copy the APK to the phone (USB / Drive) and tap it — you'll need to allow "install from unknown sources" for your file manager.

Open Recall. Type a message. The Worker echoes from Cloudflare, no laptop needed.

---

**Heads-up on what you'll see:** the chat is currently an echo (`(scaffold) you said: "..."`). That's expected for the §1–§3 scaffold — the real LLM tool-use loop lands in milestone §5. What you're verifying right now is just the wire: phone → Worker → Durable Object → response.
