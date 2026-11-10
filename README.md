# ⚡ HTC Binaural Scope & Live AI Audio Translator (Android APK)

An advanced real-time dual-trace oscilloscope and binaural AI speech translator designed for **HTC True Wireless Earphones (HTC NE20)** and Android devices.

[![Android](https://img.shields.io/badge/Platform-Android_10+-brightgreen.svg)](https://android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Download-APK-cyan.svg)](HtcBinauralScope.apk)

---

## 📥 Direct Download

👉 **[Download HtcBinauralScope.apk (v1.0.0)](./HtcBinauralScope.apk)**

---

## 🌟 Key Features

### 🎧 1. Binaural Split Live Translation
- **Left Ear:** Hears original livestream, video, or microphone audio cleanly without interruptions.
- **Right Ear:** Plays real-time AI-synthesized translation via localized TTS.
- Allows listening to foreign livestreams (e.g. Korean YouTube, Twitch, Podcasts) with immediate native translation in the right ear.

### 🔄 2. Six Direct Language Pairs + Auto-Detect
Optimized for high-speed, zero-hallucination inference with native Whisper language targeting:
- 🇰🇷 **KR ➔ 🇩🇪 DE** *(Korean to German)*
- 🇰🇷 **KR ➔ 🇬🇧 EN** *(Korean to English)*
- 🇬🇧 **EN ➔ 🇩🇪 DE** *(English to German)*
- 🇬🇧 **EN ➔ 🇰🇷 KR** *(English to Korean)*
- 🇩🇪 **DE ➔ 🇰🇷 KR** *(German to Korean)*
- 🇩🇪 **DE ➔ 🇬🇧 EN** *(German to English)*
- ⚡ **AUTO-DETECT** *(Multi-language auto speech recognition)*

### 🔁 3. Continuous Translation Loop
- One-tap toggle button: **`▶ START CONTINUOUS TRANSLATION`**
- Runs seamlessly in the background with continuous ring buffering.
- Captures audio snapshots, streams to the local Whisper/Groq bridge, and loops continuously until toggled off.
- Built-in duplicate phrase filtering prevents repeated sentences during speech pauses.

### 📻 4. Dual Input Audio Modes
- **📻 Livestream / Video (Internal Audio Capture):** Uses Android `AudioPlaybackCapture` (`MediaProjection`) to intercept system audio directly from YouTube, media players, or livestreams without acoustic microphone noise.
- **🎙️ HTC NE20 Mic:** Uses Bluetooth headset microphone for live face-to-face conversations.

### 📊 5. Real-Time Oscilloscope Screen
- **CH1 (Cyan):** Real-time audio waveform sweep with Peak-to-Peak Voltage (`Vpp`) measurement at 16 kHz sample rate.
- **CH2 (Neon Green):** 2.4 GHz RF Bluetooth Link Quality & RSSI signal trace.

### 🔒 6. Privacy & Safety Compliant
- **No unnecessary telephony permissions** (`CALL_PHONE`, `READ_PHONE_STATE` are excluded).
- Fully compliant with **Google Play Protect** standards.

---

## 📱 Installation

1. Download **`HtcBinauralScope.apk`** to your Android smartphone.
2. Open the file and tap **Install** (allow installation from browser if prompted).
3. Open **HTC Binaural Scope**.
4. Grant the **Microphone / Audio** and **Nearby Devices (Bluetooth)** permissions when requested.
5. Select **📻 Livestream / Video** or **🎙️ HTC NE20 Mic**.
6. Select your desired language direction (e.g., `KR ➔ EN` or `KR ➔ DE`).
7. Tap **`▶ START CONTINUOUS TRANSLATION`** and start listening!

---

## 🏗️ Architecture

```
[ Internal Livestream Audio / HTC Mic ]
                 │
                 ▼
     [ BinauralAudioEngine ] ──▶ [ Real-Time Oscilloscope Display ]
                 │
      (Continuous Ring Buffer)
                 │
                 ▼ (HTTP WAV Chunks)
  [ Local PC Bridge / Whisper.cpp + Groq LLM ]
                 │
                 ▼ (JSON Translation)
        [ Android App TTS ] ──▶ Panned to RIGHT Earbud (Binaural Split)
```

---

## 📄 License
This project is released under the MIT License.
