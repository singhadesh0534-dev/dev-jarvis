# J.A.R.V.I.S. — Dev Jarvis (Android)

Tony Stark ke JARVIS jaisa personal voice assistant, native Android (Kotlin + Jetpack Compose).
Bilkul free: Gemini ki free API key, open-source libraries, koi paid cheez nahi.

> **Abhi kya bana hai: Phase 1 — Core voice loop.**
> Reactor pe tap karo → bolo → Gemini sochta hai → JARVIS streaming mein jawab bolta hai.
> Wake word, phone control, memory waghera agle phases mein aayenge (neeche roadmap dekho).

---

## 1. Phase 1 mein kya hai

| Feature | Status |
|---|---|
| Iron Man HUD (dark navy + cyan), animated arc reactor | ✅ |
| Reactor mic level pe (sunte waqt) aur har bole gaye shabd pe (bolte waqt) react karta hai | ✅ |
| HUD arcs: battery (left), API fuel (right); chips: PWR / NET / MODE / FUEL | ✅ |
| Tap-to-talk (Android speech recognizer, en-IN / hi-IN) | ✅ |
| Gemini streaming — pehla sentence aate hi bolna shuru (poore jawab ka wait nahi) | ✅ |
| JARVIS personality (Settings mein editable) + har turn pe live context (time, battery, network, next alarm) | ✅ |
| Pichhle 20 turns yaad (ek session ke andar) | ✅ |
| Beech mein tap karo → JARVIS chup, aur sunne lagta hai (tap barge-in) | ✅ |
| Type karke bhi baat kar sakte ho | ✅ |
| Deep male UK/India voice auto-pick, pitch/speed sliders, voice picker | ✅ |
| API keys EncryptedSharedPreferences mein (Android Keystore), kabhi log nahi hoti | ✅ |
| Hinglish error lines ("Sir, network gayab hai, abhi offline hoon.") | ✅ |
| Fonts bundled: Orbitron (headings), Rajdhani (body, Hindi bhi support) | ✅ |

Code ka map:

```
app/src/main/java/com/adesh/devjarvis/
├── core/JarvisConfig.kt      ← SAARE model names, endpoints, limits yahan (sirf yahi file badlo)
├── core/DeviceStatus.kt      ← battery, network, next alarm
├── core/UsageTracker.kt      ← "FUEL %" (roz ke requests)
├── settings/SettingsRepository.kt ← encrypted settings (API keys, voice, prompt)
├── brain/SystemPrompt.kt     ← JARVIS ki personality
├── brain/JarvisBrain.kt      ← prompt + context + history jod ke Gemini ko bhejta hai
├── brain/GeminiClient.kt     ← streaming HTTP (SSE) client
├── brain/SentenceChunker.kt  ← streaming text → bolne layak sentences
├── voice/SpeechInput.kt      ← mic → text
├── voice/SpeechOutput.kt     ← TTS interface (pluggable)
├── voice/AndroidTts.kt       ← Android TextToSpeech implementation
└── ui/                       ← HUD, arc reactor, settings (Compose)
```

---

## 2. Laptop setup (ek baar)

### 2.1 Android Studio install
1. https://developer.android.com/studio se **Android Studio** download karke install karo (Windows/Mac/Linux sab chalega).
2. Pehli baar khologe to "Setup Wizard" aayega → **Standard** chuno → Next → Finish. Ye Android SDK khud download kar lega (~3–4 GB, thoda time lagega).
3. Android Studio ke andar JDK already hota hai, alag se Java install karne ki zarurat nahi.

### 2.2 SDK check
Android Studio → **Settings** (Mac pe *Preferences*) → *Languages & Frameworks* → **Android SDK**:
- *SDK Platforms* tab: **Android 16 (API 36)** pe tick hona chahiye.
- *SDK Tools* tab: **Android SDK Build-Tools** aur **Android SDK Platform-Tools** pe tick.
- Apply dabao.

### 2.3 Project kholo
1. Ye repo laptop pe clone/download karo.
2. Android Studio → **Open** → `dev-jarvis` folder chuno.
3. Neeche "Gradle sync" chalega (pehli baar 5–10 min). Khatam hone do.

> VS Code + Claude Code se code edit kar sakte ho, lekin **build/install** ke liye Android Studio (ya terminal mein `./gradlew`) chahiye.

---

## 3. Free API key lena

### Gemini (zaruri)
1. https://aistudio.google.com/apikey kholo, Google account se login.
2. **Create API key** → key copy karo (`AIza...` jaisi dikhegi).
3. Credit card ki zarurat nahi. Free tier ki daily limit hoti hai — AI Studio ke "Rate limits" page pe apne model ki limit dekh lo.
4. ⚠️ Free tier pe Google aapke prompts ko apne models improve karne ke liye use kar sakta hai. Personal/private cheezein soch-samajh ke bolo.

### Groq (optional — Phase 4 ke liye)
https://console.groq.com/keys → login → **Create API Key**. Abhi daal ke rakh sakte ho, Phase 4 mein kaam aayegi.

Keys app ke andar **Settings** mein daalni hain — code mein kabhi mat likhna, GitHub pe kabhi push mat karna.

---

## 4. APK banana aur phone mein daalna

### Option A — Android Studio + USB (recommended)
1. Phone pe **Developer options** on karo: *Settings → About phone → Build number* pe 7 baar tap. (Infinix pe: *Settings → My phone → All specs → Build number*.)
2. *Settings → System → Developer options* → **USB debugging** ON.
3. USB cable se phone laptop se jodo, phone pe "Allow USB debugging?" → **Allow**.
4. Android Studio ke upar device dropdown mein apna phone dikhega → hara ▶ **Run** button dabao. App phone pe install ho ke khul jayega.

### Option B — Terminal + ADB
```bash
./gradlew assembleDebug          # Windows pe: gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Option C — Bina laptop build ke (GitHub Actions)
Har push pe GitHub khud APK banata hai:
1. GitHub pe repo → **Actions** tab → "Build JARVIS APK" ka latest run (hara ✓).
2. Neeche **Artifacts** → `JARVIS-debug-apk` download → unzip → `app-debug.apk`.
3. APK phone pe bhejo (USB / Drive / Telegram) aur tap karke install karo. "Install unknown apps" allow karna padega.

---

## 5. Pehli baar chalana (Phase 1 test)

1. JARVIS app kholo → upar right ⚙️ **Settings**.
2. **Gemini API key** paste karo → neeche **SAVE**.
3. *VOICE* section mein **Test voice** dabao. Awaaz pasand nahi aayi to **Choose voice** se koi aur chuno (`en-gb-x-rjs`, `en-gb-x-gbd`, `en-in-x-ene` deep male voices hain). *Offline* wali voice tez hoti hai.
4. Back aao, beech ke **arc reactor pe tap** karo. Pehli baar mic permission maangega → Allow.
5. Bolo, jaise:
   - "Hello Jarvis, kaise ho?"
   - "Battery kitni hai?" (context se jawab dega)
   - "Mujhe ek joke sunao"
   - "Explain black holes in two lines"
6. JARVIS bol raha ho tab reactor pe tap karo → wo turant chup ho ke sunne lagega.
7. Type karke bhi poochh sakte ho (neeche text box).

Reactor ke states: **STANDBY** (dheere pulse) → **LISTENING** (mic ke saath phoolta hai) → **PROCESSING** (tez ghoomta hai) → **SPEAKING** (har shabd pe pulse).

---

## 6. Troubleshooting

| Problem | Solution |
|---|---|
| "Speech recognition service nahi mili" | Play Store se **Google** app aur **Speech Services by Google** install/update karo. |
| Awaaz robotic / Hindi galat bolta hai | Settings → Text-to-speech → engine **Speech Services by Google** chuno → English (India) / English (UK) voice data download karo. Phir app mein Choose voice. |
| "API key Google ko pasand nahi aayi" | Key dobara copy karo (aage-peeche space nahi). AI Studio mein key active hai check karo. |
| "Aaj ka free fuel khatam" (HTTP 429) | Free limit hit. Thodi der/kal try karo, ya Settings mein chhota model (jaise `gemini-3.5-flash-lite`) daalo. Phase 4 mein Groq auto-fallback aayega. |
| "Google ke servers nakhre kar rahe hain" + HTTP 404 | Model naam galat/purana. `core/JarvisConfig.kt` ya Settings → Gemini model mein current naam daalo (https://ai.google.dev/gemini-api/docs/models). |
| Gradle sync fail | Internet check karo; *File → Invalidate Caches → Restart*; SDK mein API 36 installed hai check karo. |
| Phone Android Studio mein nahi dikhta | Dusri USB cable (data wali) try karo; phone pe USB mode "File transfer" karo; USB debugging dobara on/off. |
| Hinglish galat samajhta hai | Settings → Speech language `en-IN` ↔ `hi-IN` badal ke dekho. |

---

## 7. Roadmap

1. **Phase 1 — Core voice loop** ✅ *(ye version)*
2. Phase 2 — Always-on: foreground service, "Hello Dev"/"Jarvis" wake word (offline), continuous conversation, voice barge-in, setup wizard, Infinix XOS survival
3. Phase 3 — Phone control: call, SMS, WhatsApp, apps, alarm, timer, reminder, flashlight, volume, media — confirmation ke saath
4. Phase 4 — Gemini Live API (native audio) + Groq fallback + real fuel tracking
5. Phase 5 — Memory DB + morning briefing + proactive alerts
6. Phase 6 — Camera vision, screen reading, accessibility screen agent
7. Phase 7 — Floating bubble, boot animation, sounds, battery tuning

## 8. Honest limits

- Brain ke liye internet chahiye. Offline sirf (Phase 2 ka) wake word chalega.
- Free tiers ki daily limits hain; app unke andar rehne ke liye design hai, lekin limit Google/Groq decide karte hain.
- Phase 1 mein sunna Android ke speech recognizer se hota hai (Google app). Ye bhi internet use kar sakta hai.
- Android normal apps ko Wi-Fi / mobile data / airplane mode seedha toggle nahi karne deta — Phase 3 mein JARVIS sahi settings panel kholega.

## License / credits

Fonts: Orbitron & Rajdhani — SIL Open Font License (`licenses/OFL-Orbitron-Rajdhani.txt`).
Original JARVIS-style lines likhi gayi hain; koi film dialogue copy nahi kiya gaya.
