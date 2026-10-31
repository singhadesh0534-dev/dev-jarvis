# J.A.R.V.I.S. — Dev Jarvis (Android)

Tony Stark ke JARVIS jaisa personal voice assistant, native Android (Kotlin + Jetpack Compose).
Bilkul free: Gemini ki free API key, open-source libraries, koi paid cheez nahi.

> **Saare 7 phases bane hue hain:** voice loop, "Hello Dev" always-on, phone control, Gemini Live + fallback,
> memory + proactive, eyes & hands, aur polish (floating bubble, boot animation, sounds, battery tuning, crash recovery).
> ⚠️ Har phase GitHub pe compile + unit-tested hai, lekin **real phone pe abhi test nahi hua** — pehle test karke batayein.
> "Hello Dev" bolo → JARVIS Gemini Live ki asli awaaz mein turant jawab deta hai, aap beech mein bol ke tok sakte ho
> → "Mummy ko call karo, phir subah 6 ka alarm" → confirm karke dono kaam. Quota khatam? Chupchaap backup pe.
>
> **Direct APK (pehle ye — normal install hota hai):**
> https://github.com/singhadesh0534-dev/dev-jarvis/releases/download/jarvis-latest/JARVIS-lite.apk
>
> Full edition: https://github.com/singhadesh0534-dev/dev-jarvis/releases/download/jarvis-latest/JARVIS.apk
> (Play Protect browser se install block karta hai — neeche "Lite vs Full" dekho.)

## Lite vs Full (Play Protect)

Google Play Protect ka **fraud protection** (India + kuch aur desh) browser / file manager / WhatsApp se install hone
wale us app ko **block** kar deta hai jo *Accessibility* ya *Notification access* maangta hai — chahe app aapka apna ho.
Isliye do editions hain (same package, same data):

| | **JARVIS Lite** | **JARVIS Full** |
|---|---|---|
| Browser se install | ✅ normal | ❌ Play Protect block |
| Voice, "Hello Dev", Live mode, Groq fallback | ✅ | ✅ |
| Calls, SMS, WhatsApp (pre-fill), apps, alarm, timer, reminders, torch, volume, media, weather | ✅ | ✅ |
| Memory, morning briefing, low battery, reminders bolna | ✅ | ✅ |
| Camera vision ("ye kya hai?") | ✅ | ✅ |
| Screen padhna / screenshot / tap / screen agent / lock screen | — | ✅ |
| Notifications padhna, reply, important-message alerts | — | ✅ |
| Lock screen / doosre app ke upar se apps kholna (bina tap) | notification tap chahiye | ✅ |
| Contacts se naam (Mummy, Bhai), seedha call, seedha SMS | — (dialer / SMS app khulta hai, number bolo) | ✅ |
| Calendar, brightness, floating reactor, boot pe auto-start, exact reminders | — | ✅ |

Lite mein sirf internet, mic (wake word service), camera, notifications aur vibration permissions hain — taaki
normal install ho. Full update (same package) baaki sab wapas laata hai; data bana rehta hai.

**Lite → Full (data same rehta hai):** app → Settings → **Updates** → *Download Full edition*. Us ek install ke liye
Play Protect ko rasta dena hoga — do seedhe tareeke:
1. **Laptop + USB (best):** `adb install -r JARVIS.apk` — ADB se install block nahi hota.
2. **Ya** Play Store → profile photo → **Play Protect** → ⚙️ → *Scan apps with Play Protect* **OFF** → Full install
   → Play Protect wapas **ON**.

## Live update

Settings → **Updates** → *Check for update* → **INSTALL** → Android installer → *Update*. HUD pe bhi banner aata hai
jab naya build ho.

**Ek baar ka setup (zaruri):** Android update tabhi install karta hai jab naya APK **usi signing key** se sign ho.
Wo key private rehni chahiye, isliye repo mein nahi — GitHub secret `JARVIS_KEYSTORE_B64` mein. Steps:
`app/signing/README.md`. Secret ke bina har build alag throwaway key se banta hai: naya install chalega, lekin
purane ke upar update "App not installed" dega.
> ⚠️ Secret lagne se **pehle** ke builds alag key se sign hue the — un par se pehli baar ek uninstall + install
> karna hoga; uske baad live updates seedhe chalenge aur data bana rahega.

## Phase 7 mein naya — polish

- **Floating mini-reactor:** JARVIS sun/bol raha ho aur aap kisi doosre app mein ho → screen ke kinare chhota animated
  reactor (awaaz ke saath pulse, sochte waqt tez ghoomta). **Tap** = JARVIS kholo, **long-press** = rok do,
  **drag** = kahin bhi rakho (jagah yaad rehti hai). "Display over other apps" permission chahiye (Setup).
  Bonus: dikhte overlay se Android 15 pe background se apps kholna aasaan hota hai.
- **Boot animation:** app kholte hi "JARVIS ONLINE" + asli system check (Gemini/Groq key, mic, wake model,
  accessibility, notifications, memory facts, pichhla session clean ya recovered). Tap = skip.
- **Sounds:** wake chime, success blip (tool hua), error tone, follow-up khatam hone ki soft chime. Settings → Extras se OFF.
- **Battery tuning:**
  - Wake engine ab **energy gate** ke saath: chup kamre mein Vosk decoding band (CPU ka sabse bada kharcha), awaaz
    aate hi pichhle 300 ms ke saath turant shuru — "Hello" ka shuruaati hissa nahi katta.
  - 15% se neeche (charging nahi) wake word pause (Settings → Battery saver); tap / home long-press chalte rehte hain.
  - Background refresh sirf jab HUD dikh raha ho.
- **Crash handling:** crash ho to stack trace phone pe save; agli baar boot screen pe "LAST SESSION · RECOVERED",
  Settings → **Diagnostics** mein dekho / share karo / clear. Baat-cheet ke andar koi bug ho to JARVIS crash nahi
  hota — "glitch, maine khud ko reset kar liya" bol ke wake word pe wapas. Service START_STICKY + watchdog se wapas aati hai.
  Log mein API keys ya baat-cheet nahi hoti.

## Phase 6 mein naya — eyes & hands

| Bolo | Tool | Kaise |
|---|---|---|
| "Jarvis, ye kya hai?" / "is dabbe pe kya likha hai?" | `look` | Chhoti camera screen khulti hai (lock screen ke upar bhi), ~1 sec mein photo, Gemini vision jawab deta hai |
| "Screen pe kya hai?" / "ye error kya keh raha hai?" | `look_at_screen` | Accessibility screenshot (Android 11+) → Gemini vision |
| "Screen padh ke sunao" | `read_screen` | Screen ka text seedha (tez, bina image) |
| "Search dabao" / "hello type karo" / "neeche scroll karo" / "back" | `tap`, `type_text`, `scroll`, `go_back`, `go_home` | Ek-ek step |
| "WhatsApp pe Rahul ko 'kal milte hain' bhejo" / "YouTube pe lo-fi music search karo" | `do_task_on_screen` | **Screen agent:** screen padho → LLM agla step chune → tap/type/scroll → repeat (max 15 steps). Har step HUD pe `🖐 3: tap 'Rahul'` |

**Safety (hamesha ON):**
- Password field, OTP, UPI PIN, card, "Pay ₹", bank/wallet app dikhe → agent turant ruk jaata hai ("ye hissa aap khud karein").
- *Send / Post / Share / Reply / Submit / Delete* jaisa koi final button → pehle "Sir, 'Send' dabaun?" (haan/nahi).
- *Pay / Buy / Order / Transfer / Recharge* → kabhi nahi.
- Ek hi kaam 3 baar repeat → ruk jaata hai. Beech mein reactor tap ya "stop" → turant band.
- Screen sirf aapke command pe padhi jaati hai; password fields ka content kabhi nahi padha jaata.

> ⚠️ Social media apps ke screens badalte rehte hain — agent kabhi atak sakta hai. Bahut zyada automation se
> accounts flag ho sakte hain; isliye ye sirf aapke explicit command pe chalta hai. Har step pe ek LLM request lagti hai
> (free quota).

Setup mein naya step: **Camera**. Accessibility ab screen padhne/tap karne ke liye bhi use hoti hai — agar pehle se ON
tha to ek baar OFF→ON kar dena (nayi capabilities ke liye).

## Phase 5 mein naya — memory & proactive JARVIS

**Memory (sab sirf phone pe, Room database):**
- Har baat-cheet ke ~15 sec baad JARVIS LLM se poochhta hai "kya yaad rakhne layak tha?" — log, rishte, pasand,
  routine, projects. OTP / password / PIN / lambe numbers kabhi save nahi hote.
- Har jawab se pehle sabse kaam ki 5 yaadein (keyword + rishton ke aliases + recency) brain ko di jaati hain — wo
  unhe naturally use karta hai, announce nahi karta.
- Bol ke: "yaad rakhna meri wife ka birthday 12 March hai" (`remember`), "Rahul kahan rehta hai?" (`recall`),
  "wo gym wali baat bhool jao" (`forget`, pehle confirm).
- 👤 icon (HUD) / Settings → **Memory**: dekho, edit karo, delete karo, ya sab WIPE. Conversation summaries bhi.

**Proactive:**
- **Morning briefing** (default 7:30): mausam (home city), aaj ke calendar events + reminders, battery, ek witty line.
  Settings → *Play briefing now* se abhi test karo.
- **Low battery:** 20% se neeche (charging nahi) bolta hai, 10% pe dobara.
- **Important notifications:** chune hue apps (default WhatsApp, Messages, Phone) ke direct messages, ya chune hue
  log (jaise "Mummy, Papa") — chhota spoken summary. Max ek alert / 30 sec.
- **Reminders** time pe bolte hain (ab database mein; restart ke baad bhi).
- **Chup kab rehta hai:** raat 11–7 (Settings mein badlo), DND, call, silent mode (headphones lage hon to bolta hai),
  ya jab pehle se baat chal rahi ho — tab sirf notification. Aapke apne reminders raat mein bhi bolte hain.
- Proactive *bolna* always-on service ON hone pe hi hota hai; warna notification.

**Notifications:** `read_notifications` ("kiska message aaya?"), `reply_to_notification` ("Mummy ko reply karo
aa raha hoon" → confirm → WhatsApp/Messages ke reply button se bhejta hai). OTP / bank / UPI / payment apps ke
notifications JARVIS ko dikhte hi nahi — na bole jaate, na LLM ko jaate. Context mein top 3 (filtered) bhi jaate hain.

Naye tools: `remember`, `recall`, `forget`, `read_notifications`, `reply_to_notification`, `get_schedule`.
Setup mein naye steps: **Notification access**, **Calendar** (optional). Activity log ab save rehta hai.

## Phase 4 mein naya — Live mode & fallback

| | Kya | Kab |
|---|---|---|
| **LIVE** (default) | Gemini Live API: aapki awaaz seedha model tak (WebSocket, 16 kHz), model ki native awaaz wapas (24 kHz). Beech mein bolo → turant chup (native interruption). Hindi ka asli uchcharan. Tools + confirmation yahan bhi. | Settings → Voice engine: LIVE |
| **CLASSIC** | Sunna (Android ya **Groq Whisper**) → Gemini text streaming → Android TTS | Live fail / quota khatam / Settings mein CLASSIC |
| **FALLBACK** | Gemini text ka 429 (free quota) → chupchaap **Groq** (`openai/gpt-oss-120b`, tool calling ke saath) | Groq key daali ho |

- **Auto-switch:** Live session shuru hi na ho paaye (quota, model, key, network) → usi baat-cheet mein CLASSIC
  pe chala jaata hai — wake word dobara nahi bolna padta. Quota wala Live aaj ke liye band, baaki errors pe 10 min baad dobara try.
- **HUD MODE chip:** `LIVE` / `STREAM` (classic Gemini) / `FALLBACK` (Groq). **FUEL** usi engine ka aaj ka bacha hua
  andaaza (Gemini requests / Live minutes / Groq requests) — 429 aate hi 0%.
- **Echo:** Live mode phone ko "communication mode" mein daalta hai (jaise WhatsApp call) taaki Android ka echo
  canceller JARVIS ki apni awaaz hata sake. Headset/Bluetooth laga ho to wahi use hota hai, warna loudspeaker.
- **Live mein tap = conversation band.** Rokne ke liye bas bol do — Live khud ruk jaata hai.
- **Groq Whisper** (Settings → Classic mode ears): Hinglish Google recognizer se behtar samajhta hai.

---

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
├── reminders/                ← AlarmManager reminders (Room)
├── data/                     ← Room database (facts, conversations, reminders, activity)
├── memory/                   ← ranking, extraction, memory tools
├── notifications/            ← listener, OTP/bank filter, read/reply tools
├── proactive/                ← policy, briefing, alerts
├── screen/                   ← screen model, safety, agent protocol, eyes & hands tools
├── overlay/                  ← floating mini-reactor bubble
├── ui/boot/                  ← "JARVIS ONLINE" boot sequence
├── vision/                   ← camera capture screen, image utils
├── ui/setup/                 ← setup wizard (permissions + Infinix steps)
├── live/                     ← Gemini Live: WebSocket, messages, echo-cancelled audio
├── brain/GroqClient.kt       ← Groq backup brain + Whisper STT
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
./gradlew assembleFullDebug      # Windows pe: gradlew.bat assembleFullDebug
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

### Option C — Bina laptop build ke (GitHub Actions)
Har push pe GitHub khud dono APK banata hai aur release pe daalta hai — upar wale direct links. (Actions →
latest run → Artifacts → `JARVIS-apks` mein bhi hain.)

Laptop se khud build: `./gradlew assembleLiteRelease` (ya `assembleFullRelease`) →
`app/build/outputs/apk/lite/release/app-lite-release.apk`.

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

### 5.2 Phase 7 test (polish)
1. App kholo → boot animation + system checks (koi line amber ho to wo cheez Setup mein theek karo).
2. Setup → Display over other apps ON. YouTube kholo, "Hello Dev, ek joke sunao" → kinare pe mini-reactor; drag karo,
   long-press se roko, tap se JARVIS khulega.
3. Torch on/off bolo → success blip. Settings → Extras → Sound effects OFF karke dobara.
4. Settings → Diagnostics → "Koi crash nahi" (ya log share karo).

### 5.3 Phase 6 test (eyes & hands)
1. Setup → Camera allow. Accessibility ek baar OFF → ON (nayi permissions).
2. Kisi cheez ki taraf phone karke: "Jarvis, ye kya hai?" → camera screen → jawab.
3. Koi article kholo: "Hello Dev, screen pe kya likha hai, short mein batao."
4. "WhatsApp pe Rahul ko 'test message' bhejo" (do_task_on_screen) → HUD pe steps → "Sir, 'Send' dabaun?" → "haan".
5. Kisi shopping app ke checkout pe: "pay kar do" → JARVIS mana karega.

### 5.4 Phase 5 test (memory + proactive)
1. Setup → Notification access ON, Calendar allow. Settings → Home city daalo, Important people mein "Mummy".
2. "Jarvis, yaad rakhna mujhe chai pasand hai, coffee nahi." → 👤 Memory mein dikhna chahiye.
3. Kuch baat karo (jaise "mera bhai Rahul Pune mein rehta hai"), 20 sec ruko → Memory mein naya fact.
4. Naya din / baad mein: "main subah kya peeta hoon?" → JARVIS ko yaad hona chahiye.
5. Settings → **Play briefing now** → briefing bolega (always-on ON ho to) ya notification aayega.
6. Kisi se WhatsApp message bhijwao (Mummy ke naam se) → JARVIS bolega. "Reply karo aa raha hoon" → confirm → reply.
7. Khud ko OTP wala SMS aaye → JARVIS chup rahe, "kiska message aaya" mein bhi na dikhe.

### 5.5 Phase 4 test (Live + fallback)
1. Settings → Voice engine **LIVE** (default), Gemini + Groq dono keys daalo → SAVE. HUD pe MODE: **LIVE**.
2. "Hello Dev" → "Ek lambi kahani sunao" → beech mein bolo "ruko, bas" → turant chup ho jaana chahiye.
3. "Torch on karo" (Live mein bhi tools chalte hain). "Mummy ko call karo" → confirm (haan/nahi) → call.
4. Fallback test: Settings → Live model mein galat naam (jaise `abc`) → SAVE → "Hello Dev" → transcript mein
   "Live mode unavailable … classic mode" aur JARVIS normal (classic) mein jawab de. Test ke baad Reset kar dena
   (default `gemini-3.8-live`).
5. Groq-only test: Gemini key hata ke sirf Groq key → MODE: FALLBACK, sawaal ka jawab Groq se aaye.

### 5.6 Phase 3 test (phone control)
1. "Jarvis, torch on karo" → torch jalegi, HUD pe `✓ toggle_flashlight` line.
2. "Mummy ko call karo" → "Sir, Mummy ko call karun?" → "haan" → call lagegi.
3. Chain: "10 minute ka timer lagao aur volume 50 kar do" → dono ho jayenge.
4. "Papa ko SMS karo ki main 10 minute mein pahunch raha hoon" → message sunayega → "nahi" bolo → nahi bhejega.
5. Screen lock karke: "Hello Dev, YouTube kholo" → (Accessibility ON ho to) YouTube khul jayega, unlock karte hi dikhega.
6. 📋 icon → Activity log mein sab entries.

### 5.7 Phase 1 test (tap-to-talk)
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
| Live mein JARVIS khud ko hi interrupt karta hai (apni awaaz sun ke ruk jaata hai) | Volume thoda kam karo ya headset lagao. Phir bhi ho to Settings → Voice engine CLASSIC. |
| Live mein awaaz earpiece (kaan wale speaker) se aati hai | Kuch phones communication mode mein speaker nahi dete — CLASSIC use karo, ya mujhe batao (device-specific fix). |
| Hamesha "Live mode unavailable" | Live model naam check karo (Settings → Live model, default `gemini-3.8-live`); AI Studio mein Live API free tier available hai ya nahi dekho. |
| MODE: FALLBACK aur jawab Groq se | Gemini ka free quota (429) khatam — normal hai; agle din ya ek minute baad Gemini wapas. |
| Groq error 404 / model not found | Settings → Groq backup model mein current model naam daalo (console.groq.com/docs/models). |
| "ye kya hai" pe camera nahi khulta | Camera permission + Accessibility ON? Screen lock pe pehli baar "tap to continue" notification aa sakta hai. |
| Screen agent galat jagah tap karta hai / atak jaata hai | Goal zyada saaf bolo ("WhatsApp kholo, Rahul ki chat, 'hi' bhejo"). Kuch apps (games, banking) accessibility se dikhte hi nahi. |
| `look_at_screen` sirf text deta hai | Screenshot Android 11+ pe hi; Accessibility OFF→ON karo (canTakeScreenshot). |
| Floating reactor nahi dikhta | Setup → Display over other apps ON; Settings → Extras → Floating mini-reactor ON; always-on service ON hona chahiye. |
| Battery zyada ja rahi hai | Settings → Wake word sensitivity thodi kam karo (kam false wake-ups), Battery saver ON, raat ko notification se "Sleep". |
| JARVIS ne "glitch… reset" bola | Settings → Diagnostics → Share log — mujhe bhej do, fix kar dunga. |
| Briefing nahi bola | Always-on ON hai? Briefing time quiet hours ke andar to nahi? DND / silent mode? (Tab sirf notification aata hai.) |
| Memory mein kuch nahi aata | Baat-cheet ke baad ~15 sec lagte hain, aur internet chahiye. Chhoti baatein ("timer lagao") yaad nahi rakhi jaati — ye design hai. |
| Message alerts nahi bolta | Setup → Notification access ON? Settings → Important apps/people. Group messages tabhi jab sender "important people" mein ho. |
| Wake model download fail | Internet check karo, Setup → Wake word model → dobara Download. |

---

## 7. Roadmap

1. **Phase 1 — Core voice loop** ✅
2. **Phase 2 — Always-on** ✅: foreground service, "Hello Dev"/"Jarvis" wake word (offline), continuous conversation, voice barge-in, setup wizard, Infinix XOS survival
3. **Phase 3 — Phone control** ✅: call, SMS, WhatsApp, apps, alarm, timer, reminder, calendar, flashlight, volume, brightness, settings panels, screenshot/lock, media, device status, weather, calculator — confirmation + activity log
4. **Phase 4 — Live & fallback** ✅: Gemini Live API (native audio, interruption, tools), auto-switch to classic, Groq backup brain + Whisper, per-provider fuel
5. **Phase 5 — Memory & proactive** ✅: Room memory + auto-extraction + retrieval, memory screen, morning briefing, low battery, important notifications, quiet hours/DND/silent rules, notification read/reply
6. **Phase 6 — Eyes & hands** ✅: camera vision (`look`), screenshot vision, screen reading, tap/type/scroll, screen agent with safety stops + final-action confirmation
7. **Phase 7 — Polish** ✅ *(ye version)*: floating mini-reactor, boot animation with real system checks, sound effects, energy-gated wake word + low-battery pause, crash logging + in-session recovery + diagnostics

Aage (ideas): embeddings se memory search, offline TTS upgrade (Piper), widget/quick-settings tile, Wear OS.

## 8. Honest limits

- Brain ke liye internet chahiye. Offline sirf (Phase 2 ka) wake word chalega.
- Free tiers ki daily limits hain; app unke andar rehne ke liye design hai, lekin limit Google/Groq decide karte hain.
- Phase 1 mein sunna Android ke speech recognizer se hota hai (Google app). Ye bhi internet use kar sakta hai.
- **Reboot ke baad ek tap:** Android 15+ boot ke waqt kisi bhi app ko background se mic service start nahi karne deta.
  Isliye restart ke baad "tap karke online kijiye" notification aata hai — ek tap aur JARVIS wapas. (Ye Android ka
  security rule hai, koi app isse bypass nahi kar sakta.)
- **Voice barge-in:** LIVE mode mein kuch bhi bolo, JARVIS ruk jaata hai (native). CLASSIC mode mein sirf
  "Jarvis" / "stop" / "ruko" — kyunki wahan JARVIS ki apni awaaz mic mein aati hai.
- **Live free tier:** Live API ki free limits Google badalta rehta hai; fuel gauge sirf andaaza hai.
- **Wake word accuracy:** Vosk grammar-based spotting hai, Alexa jaisa trained detector nahi. Shor-sharabe mein
  kabhi miss/false alarm hoga — sensitivity slider se tune karo.
- **Battery:** always-on wake word lagataar mic + thoda CPU use karta hai. Andaazan kuch % per din extra; raat ko
  notification ka "Sleep" button dabao.
- **WhatsApp:** `send_whatsapp` message bhar ke kholta hai; poora bhejna chahiye to "WhatsApp pe … bhejo" bolo —
  screen agent bhejega, *Send* se pehle confirm karke.
- **Memory retrieval** keyword + recency based hai (embeddings nahi) — "mummy"/"mom"/"maa" jaise aliases samajhta
  hai, lekin bilkul alag shabdon wala sawaal kabhi miss ho sakta hai.
- **Reply** sirf un apps mein jo notification pe "Reply" button dete hain (WhatsApp, Messages, Telegram…).
- Android normal apps ko Wi-Fi / mobile data / airplane mode seedha toggle nahi karne deta — Phase 3 mein JARVIS sahi settings panel kholega.

## License / credits

Fonts: Orbitron & Rajdhani — SIL Open Font License (`licenses/OFL-Orbitron-Rajdhani.txt`).
Original JARVIS-style lines likhi gayi hain; koi film dialogue copy nahi kiya gaya.
