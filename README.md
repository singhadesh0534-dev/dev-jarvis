# J.A.R.V.I.S. — Dev Jarvis (Android)

Tony Stark ke JARVIS jaisa personal voice assistant, native Android (Kotlin + Jetpack Compose).
Bilkul free: Gemini ki free API key, open-source libraries, koi paid cheez nahi.

> **Abhi kya bana hai: Phase 1–3 — voice loop, always-on, phone control.**
> "Hello Dev" bolo → "Mummy ko call karo, phir subah 6 ka alarm laga do" → JARVIS confirm karke dono kaam karta hai.
> Memory, proactive briefing, camera/screen waghera agle phases mein (neeche roadmap).

## Phase 3 mein naya — phone control

Gemini **function calling**: JARVIS khud decide karta hai kaunsa tool chalana hai, ek request mein kai tools
chain kar sakta hai, aur har action HUD transcript + **Activity log** (📋 icon) mein dikhta hai.

| Bolo (example) | Tool | Confirm? |
|---|---|---|
| "Mummy ko call karo" / "Bhai ko phone lagao" | `make_call` (fuzzy contact match: Mummy/Mom/Maa, typos) | ✅ haan/nahi |
| "Papa ko SMS karo ki main late hoon" | `send_sms` (message padh ke sunata hai) | ✅ |
| "Rahul ko WhatsApp karo kal milte hain" | `send_whatsapp` (message bhara hua khulta hai, send aap dabate ho) | — |
| "YouTube kholo" / "insta kholo" | `open_app` | — |
| "Subah 6 baje ka alarm" / "10 minute ka timer" | `set_alarm`, `set_timer` | — |
| "Shaam 7 baje yaad dilana dawai leni hai" | `create_reminder` (time pe bolta + notification) | — |
| "Kal 10 baje meeting calendar mein daalo" | `add_calendar_event` | (calendar mein save aap) |
| "Torch on karo" / "volume 30" / "brightness kam" | `toggle_flashlight`, `set_volume`, `set_brightness` | — |
| "Wi-Fi kholo" / "Bluetooth settings" | `open_settings` (Android direct toggle nahi karne deta) | — |
| "Screenshot lo" / "phone lock karo" | `take_screenshot`, `lock_screen` (Accessibility) | — |
| "Gaana chalao Arijit" / "next song" / "pause" | `play_music`, `media_control` | — |
| "Phone ka status batao" | `device_status` (battery, storage, RAM) | — |
| "Mumbai ka mausam" / "1250 ka 18% kitna" | `weather` (Open-Meteo, free), `calculate` | — |

Confirmation: JARVIS poochhta hai "Sir, Mummy ko call karun?" → boliye **"haan"** / **"nahi"** ya HUD pe
**YES / NO** tap karo. 25 sec mein jawab na mile to *nahi* maana jaata hai. Payments / banking kabhi nahi.

**Accessibility kyun zaruri hai:** Android background se (screen lock / doosre app ke upar) kisi app ko naye screens
kholne nahi deta — sirf tab jab app ka Accessibility service ON ho. Iske bina "call karo" jaise kaam ke liye ek
"tap to continue" notification aata hai. Setup → *Accessibility* step.

---

**Phase 2 (always-on) recap:** "Hello Dev" ya "Jarvis" bolo (screen off / lock mein bhi) → chime → bolo →
JARVIS streaming mein jawab deta hai → ~8 sec follow-up sunta hai.

## Phase 2 mein naya

| Feature | Kaise |
|---|---|
| "Hello Dev" / "Jarvis" wake word, offline | Vosk keyword spotting (small Indian-English model, ~36 MB, ek baar download) |
| Screen off / lock mein bhi sunna | Foreground service (type *microphone*), notification "JARVIS online" |
| Jaagne pe chime + haptic tick | Code se banai gayi 2-note chime, koi audio file nahi |
| Continuous conversation | Jawab ke baad ~8 sec bina wake word ke follow-up. "Bas" / "thank you" / "bye" bolo to turant standby |
| Voice barge-in | JARVIS bol raha ho tab "Jarvis" / "stop" / "ruko" bolo → chup ho ke sunta hai (echo canceller ke saath) |
| Setup wizard | Har permission ki alag screen, "Open setting" button, green tick ✓ |
| Infinix XOS survival | Battery optimization off, Phone Master auto-start deep link, recents lock guide, START_STICKY, boot pe restart, 15-min watchdog |
| Default digital assistant | Home button long-press se JARVIS khule (optional) |
| Music pause | Baat-cheet ke dauraan music ruk jaata hai, baad mein wapas |

**Wake word engine kyun Vosk?** openWakeWord (ONNX) zyada accurate hai, lekin har naye phrase ("Hello Dev") ke liye
alag model *train* karna padta hai. Vosk ke grammar mode mein koi bhi phrase bas config mein likh do, poora offline,
aur ek core ka chhota hissa leta hai. Hindi model ki zarurat nahi — "hello dev" / "jarvis" English words hain.
Phrases `core/JarvisConfig.kt` mein `WAKE_PHRASES` mein hain.

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
├── core/JarvisSession.kt     ← sunna/sochna/bolna + mic ka hisaab (HUD aur service dono isi ko use karte hain)
├── wakeword/                 ← Vosk wake word engine + model download
├── service/                  ← always-on service, boot receiver, watchdog
├── tools/                    ← JarvisTool interface, ToolRegistry, contact/app fuzzy match, arg parsing
├── tools/impl/               ← Communication / Phone / Info tools
├── accessibility/            ← global actions (lock, screenshot) + background launch
├── reminders/                ← AlarmManager reminders
├── ui/setup/                 ← setup wizard (permissions + Infinix steps)
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

## 5. Pehli baar chalana

### 5.0 Setup wizard (pehli baar khud khulta hai)
Har step pe **button** dabao → system setting khulegi → allow karo → wapas aao → green **✓ DONE** dikhega.
1. Microphone → Allow
2. Notifications → Allow
3. Wake word model → Download (~36 MB, Wi-Fi behtar)
4. Battery optimization → "Allow" / "Don't optimize"
5. **Infinix: Auto-start** → Phone Master mein JARVIS ka Auto-start ON; App info → Battery → "No restrictions" → wapas aake **HO GAYA**
6. **Infinix: Recents lock** → recent apps kholo → JARVIS card neeche kheencho → 🔒 → **HO GAYA**
7. Phone calls, Contacts, Send SMS → Allow (teeno alag screens)
8. **Accessibility** → Installed apps → JARVIS → ON. "Restricted setting" aaye to: Settings > Apps > JARVIS > ⋮ > *Allow restricted settings*
9. Modify system settings (optional, brightness ke liye)
10. Display over other apps (optional)
11. Default digital assistant (optional) → JARVIS chuno

Settings mein **Home city** bhi daal do (weather ke liye).

Baad mein bhi: Settings → *Setup & permissions checklist*.

### 5.1 Phase 2 test (always-on)
1. HUD pe **"HELLO DEV" ALWAYS-ON** switch ON karo. Notification bar mein "JARVIS online" aayega.
2. Screen lock kar do. Bolo **"Hello Dev"** → chime + vibration → bolo "Aaj ka din kaisa hai?"
3. Jawab ke baad bina wake word ke bolo "Aur kal?" (follow-up, ~8 sec).
4. Lambe jawab ke beech mein bolo **"Jarvis"** ya **"stop"** → wo rukega aur sunega.
5. Bolo "bas, thank you" → wo jawab dega aur standby (soft chime).
6. HUD pe switch ke neeche `heard: ...` dikhata hai ki wake engine ne kya suna — sensitivity tune karne mein kaam aata hai
   (Settings → Wake word → Sensitivity).

### 5.2 Phase 3 test (phone control)
1. "Jarvis, torch on karo" → torch jalegi, HUD pe `✓ toggle_flashlight` line.
2. "Mummy ko call karo" → "Sir, Mummy ko call karun?" → "haan" → call lagegi.
3. Chain: "10 minute ka timer lagao aur volume 50 kar do" → dono ho jayenge.
4. "Papa ko SMS karo ki main 10 minute mein pahunch raha hoon" → message sunayega → "nahi" bolo → nahi bhejega.
5. Screen lock karke: "Hello Dev, YouTube kholo" → (Accessibility ON ho to) YouTube khul jayega, unlock karte hi dikhega.
6. 📋 icon → Activity log mein sab entries.

### 5.3 Phase 1 test (tap-to-talk)
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
| "Hello Dev" pe nahi jaagta | HUD pe `heard:` dekho. Kuch nahi aata → mic/notification permission + always-on switch check karo. Galat shabd aata hai → Sensitivity badhao (0.7–0.9), ya "Jarvis" bolo (ye sabse reliable hai). |
| Bina bole khud jaag jaata hai | Sensitivity kam karo (0.3–0.5). TV/music ke paas false alarms zyada hote hain. |
| "JARVIS standby mein hai — tap karke online kijiye" notification | Android ne background mein mic rok diya (reboot ke baad ya XOS ne app maara). Notification pe ek tap → wapas online. Bar-bar aaye to Setup ke Infinix steps dobara karo. |
| Thodi der baad JARVIS online notification gayab | XOS ne maar diya: Auto-start ON, Battery "No restrictions", Recents lock — teeno zaruri. |
| "Call karo" pe "tap to continue" notification aata hai | Accessibility OFF hai. Setup → Accessibility step karo. |
| Contact nahi milta / galat contact | Contacts permission check karo. Do log same naam ke hon to JARVIS poochhega kaunsa. Contact ka naam jaisa save hai waisa bolo, ya number bolo. |
| Alarm/timer nahi lagta | Phone mein default Clock app hona chahiye (Infinix Clock theek hai). |
| Brightness nahi badalti | Setup → Modify system settings ON karo. |
| Gemini error "function call ... thought signature" / 400 | Model naam check karo (Settings → Gemini model). Purane models pe Settings mein `gemini-3.8-flash` daalo. |
| Wake model download fail | Internet check karo, Setup → Wake word model → dobara Download. |

---

## 7. Roadmap

1. **Phase 1 — Core voice loop** ✅
2. **Phase 2 — Always-on** ✅: foreground service, "Hello Dev"/"Jarvis" wake word (offline), continuous conversation, voice barge-in, setup wizard, Infinix XOS survival
3. **Phase 3 — Phone control** ✅ *(ye version)*: call, SMS, WhatsApp, apps, alarm, timer, reminder, calendar, flashlight, volume, brightness, settings panels, screenshot/lock, media, device status, weather, calculator — confirmation + activity log
4. Phase 4 — Gemini Live API (native audio) + Groq fallback + real fuel tracking
5. Phase 5 — Memory DB + morning briefing + proactive alerts
6. Phase 6 — Camera vision, screen reading, accessibility screen agent
7. Phase 7 — Floating bubble, boot animation, sounds, battery tuning

## 8. Honest limits

- Brain ke liye internet chahiye. Offline sirf (Phase 2 ka) wake word chalega.
- Free tiers ki daily limits hain; app unke andar rehne ke liye design hai, lekin limit Google/Groq decide karte hain.
- Phase 1 mein sunna Android ke speech recognizer se hota hai (Google app). Ye bhi internet use kar sakta hai.
- **Reboot ke baad ek tap:** Android 15+ boot ke waqt kisi bhi app ko background se mic service start nahi karne deta.
  Isliye restart ke baad "tap karke online kijiye" notification aata hai — ek tap aur JARVIS wapas. (Ye Android ka
  security rule hai, koi app isse bypass nahi kar sakta.)
- **Voice barge-in sirf keywords pe:** loudspeaker pe JARVIS ki apni awaaz mic mein aati hai, isliye "koi bhi awaaz pe
  ruk jao" reliable nahi hota. Isliye "Jarvis" / "stop" / "ruko" — ya reactor pe tap. Phase 4 (Gemini Live) mein
  native interruption aayega.
- **Wake word accuracy:** Vosk grammar-based spotting hai, Alexa jaisa trained detector nahi. Shor-sharabe mein
  kabhi miss/false alarm hoga — sensitivity slider se tune karo.
- **Battery:** always-on wake word lagataar mic + thoda CPU use karta hai. Andaazan kuch % per din extra; raat ko
  notification ka "Sleep" button dabao.
- **WhatsApp auto-send nahi:** message bhar ke khulta hai, send aap dabate ho. Auto-send (Accessibility se) Phase 6 mein,
  sirf aapke explicit command pe.
- **Reminders** abhi simple local storage mein hain (Phase 5 mein DB). Notification-reading / reply Phase 5 mein.
- **Activity log** abhi app band hone pe saaf ho jaata hai (Phase 5 mein save hoga).
- Android normal apps ko Wi-Fi / mobile data / airplane mode seedha toggle nahi karne deta — Phase 3 mein JARVIS sahi settings panel kholega.

## License / credits

Fonts: Orbitron & Rajdhani — SIL Open Font License (`licenses/OFL-Orbitron-Rajdhani.txt`).
Original JARVIS-style lines likhi gayi hain; koi film dialogue copy nahi kiya gaya.
