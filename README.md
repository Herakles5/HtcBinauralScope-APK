# ⚡ HTC Binaural Scope & Live AI Audio Translator (Android APK)

An advanced real-time dual-trace oscilloscope and binaural AI speech translator designed for **HTC True Wireless Earphones (HTC NE20)**, any Bluetooth earbuds/headphones, and Android devices.

[![Android](https://img.shields.io/badge/Platform-Android_10+-brightgreen.svg)](https://android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Download-APK_v2.0.0-cyan.svg)](https://github.com/Herakles5/HtcBinauralScope-APK/raw/main/HtcBinauralScope.apk)

---

## 📥 Direct Download

👉 **[Download HtcBinauralScope.apk (v2.0.0 Standalone)](https://github.com/Herakles5/HtcBinauralScope-APK/raw/main/HtcBinauralScope.apk)**

---

## 🌟 Key Features (v2.0.0)

### 🌐 1. 100% Standalone Cloud AI Mode (No PC Needed!)
- Directly interacts with high-performance Groq Cloud AI:
  - **Speech Recognition:** `whisper-large-v3-turbo` (~400ms latency)
  - **Neural Translation:** `qwen/qwen3.8-27b` (~25ms latency)
- **Zero local PC or server requirement**: Works completely standalone anywhere on 4G/5G or Wi-Fi!
- Optional fallback toggle to local PC Bridge for offline LAN environments.

### 🎧 2. Binaural Split Live Translation
- **Left Ear:** Hears original livestream, video, or microphone audio cleanly without interruptions.
- **Right Ear:** Plays real-time AI-synthesized translation via localized TTS.
- Allows listening to foreign livestreams (e.g. Korean YouTube, Twitch, Podcasts) with immediate native translation in the right ear.

### 🌍 3. Global Multi-Language Matrix (28+ Languages & Auto-Detect)
- **Flexible Source & Target Selectors:** Combine any of 28+ supported languages worldwide!
- **🌐 Source (Input Audio / Left Ear):** `Auto Detect` (Automatic Speech Recognition) or direct selection from Korean, Japanese, Chinese, English, German, Spanish, French, Italian, Portuguese, Russian, Turkish, Arabic, Hindi, Vietnamese, Thai, Indonesian, Polish, Ukrainian, Dutch, Swedish, Czech, Greek, Hebrew, Romanian, Hungarian, Danish, Finnish, Norwegian.
- **🔊 Target (Output Audio / Right Ear):** High-speed neural translation played directly in your right earbud using localized native Text-to-Speech voices.
- **⇄ Quick Swap Button:** Easily invert the translation direction with a single tap.

### 🔁 4. Continuous Translation Loop
- One-tap toggle button: **`▶ START CONTINUOUS TRANSLATION`**
- Runs seamlessly in the background with continuous ring buffering.
- Captures audio snapshots, streams to cloud/bridge, and loops continuously until toggled off.
- Built-in duplicate phrase filtering prevents repeated sentences during speech pauses.

### 📻 5. Dual Input Audio Modes
- **📻 Livestream / Video (Internal Audio Capture):** Uses Android `AudioPlaybackCapture` (`MediaProjection`) to intercept system audio directly from YouTube, media players, or livestreams without acoustic microphone noise.
- **🎙️ Earphone Mic:** Uses Bluetooth headset microphone for live face-to-face conversations.

### 📊 6. Real-Time Oscilloscope Screen
- **CH1 (Cyan):** Real-time audio waveform sweep with Peak-to-Peak Voltage (`Vpp`) measurement at 16 kHz sample rate.
- **CH2 (Neon Green):** 2.4 GHz RF Bluetooth Link Quality & RSSI signal trace.

### 🔒 7. Privacy & Safety Compliant
- **No unnecessary telephony permissions** (`CALL_PHONE`, `READ_PHONE_STATE` are completely removed).
- Fully compliant with **Google Play Protect** standards.
- Fully localized in **English** UI.

---

## 📱 Installation

1. Download **`HtcBinauralScope.apk`** directly to your phone:
   - [Direct Download Link](https://github.com/Herakles5/HtcBinauralScope-APK/raw/main/HtcBinauralScope.apk)
2. Open the file and tap **Install** (allow installation from browser if prompted).
3. Open **HTC Binaural Scope**.
4. Grant the **Microphone / Audio** and **Nearby Devices (Bluetooth)** permissions when requested.
5. In **AI Translation Engine**, ensure **`🌐 Standalone Cloud (No PC)`** is selected (Default).
6. Select your desired language direction (e.g., `KR ➔ DE` or `KR ➔ EN`).
7. Tap **`▶ START CONTINUOUS TRANSLATION`** and start listening!

---

## 🏗️ Architecture

```
[ Livestream Audio / Earphone Mic ]
                │
                ▼
    [ BinauralAudioEngine ] ──▶ [ Real-Time Oscilloscope Display ]
                │
     (Continuous Ring Buffer)
                │
        ┌───────┴────────────────────────┐
        ▼ (Default)                      ▼ (Fallback)
[ 🌐 Groq Cloud API ]           [ 🖥️ Local PC Bridge ]
- Whisper Large v3 Turbo        - Whisper.cpp
- Qwen 3.8 27B                  - Groq Gateway
        │                                │
        └───────────────┬────────────────┘
                        ▼ (JSON Translation)
              [ Android Native TTS ]
                        │
                        ▼ (Panned 100% to Right Ear)
              🎧 RIGHT EARPHONE
```

---

## 📄 License
This project is released under the MIT License.
