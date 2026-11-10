#!/usr/bin/env python3
"""
HTC NE20 - Bluetooth & Audio Oscilloscope + AI Signal Processor
CosmOS / MeinOS Integration
Features:
- Dual Oscilloscope: 2.4 GHz RF Telemetry (RSSI/LQI) + Real-time Audio Waveform & FFT
- AI Livestream & Mic Translation: Auto language detection (DE / EN / KO)
- Binaural Split Mode: Left ear = Original Audio (Livestream or Mic), Right ear = Live Translation
"""

import sys
import os
import time
import math
import subprocess
import threading
import json
import numpy as np
import tkinter as tk
from tkinter import ttk, messagebox

DEVICE_MAC = "41:42:D4:14:B4:9D"
DEVICE_CARD = "bluez_card.41_42_D4_14_B4_9D"
DEVICE_NAME = "HTC NE20"

class OscilloscopeApp:
    def __init__(self, root):
        self.root = root
        self.root.title(f"OSCILLOSCOPE // {DEVICE_NAME} [{DEVICE_MAC}]")
        self.root.geometry("1060x750")
        self.root.configure(bg="#080c10")

        # Oscilloscope State
        self.running = True
        self.paused = False
        self.current_mode = "DUAL"  # DUAL, AUDIO, FFT, RF
        self.timebase = 1.0         # Time scale factor
        self.gain = 2.0             # Amplitude gain factor
        self.trigger_level = 0.0

        # RF Telemetry History (Circular buffers for rolling scope)
        self.history_len = 160
        self.rssi_history = [-30.0] * self.history_len
        self.lq_history = [200.0] * self.history_len
        self.tx_history = [4.0] * self.history_len
        self.current_rssi = -30
        self.current_lq = 200
        self.current_tx = 4

        # Audio Stream Data Buffer
        self.audio_len = 512
        self.audio_buffer = np.zeros(self.audio_len, dtype=np.float32)
        self.fft_buffer = np.zeros(self.audio_len // 2, dtype=np.float32)
        self.vpp = 0.0
        self.vrms = 0.0
        self.freq_peak = 0.0

        # Continuous Auto-Translate State
        self.auto_loop_running = False

        # Process management for audio capture
        self.audio_proc = None
        self.audio_thread = None
        self.rf_thread = None

        self.setup_ui()
        self.start_telemetry_thread()
        self.start_audio_capture()

        # Canvas refresh timer
        self.root.after(33, self.update_display)

    def setup_ui(self):
        # Top Header Bar
        hdr = tk.Frame(self.root, bg="#0d151c", height=50)
        hdr.pack(fill=tk.X, side=tk.TOP, padx=6, pady=(6, 2))

        lbl_title = tk.Label(
            hdr, 
            text="⚡ MEIN-OS // BLUETOOTH & AUDIO OSCILLOSCOPE", 
            font=("Consolas", 12, "bold"), 
            fg="#00e5ff", 
            bg="#0d151c"
        )
        lbl_title.pack(side=tk.LEFT, padx=12, pady=8)

        self.lbl_dev_status = tk.Label(
            hdr, 
            text=f"TARGET: {DEVICE_NAME} ({DEVICE_MAC}) ● ONLINE", 
            font=("Consolas", 10, "bold"), 
            fg="#00ff88", 
            bg="#0d151c"
        )
        self.lbl_dev_status.pack(side=tk.LEFT, padx=8)

        # Profile Switch Buttons
        btn_a2dp = tk.Button(
            hdr, 
            text="🎧 HiFi Mode (A2DP)", 
            font=("Consolas", 9, "bold"), 
            bg="#172331", 
            fg="#68d5ff", 
            activebackground="#20344b",
            activeforeground="#ffffff",
            relief=tk.FLAT, 
            padx=8,
            command=self.set_a2dp
        )
        btn_a2dp.pack(side=tk.RIGHT, padx=4, pady=6)

        btn_route = tk.Button(
            hdr, 
            text="🎵 Route Sound to Earbuds", 
            font=("Consolas", 9, "bold"), 
            bg="#1a382c", 
            fg="#50fa7b", 
            activebackground="#255341",
            activeforeground="#ffffff",
            relief=tk.FLAT, 
            padx=8,
            command=self.route_all_audio
        )
        btn_route.pack(side=tk.RIGHT, padx=4, pady=6)

        btn_hfp = tk.Button(
            hdr, 
            text="🎙️ Mic Mode (mSBC)", 
            font=("Consolas", 9, "bold"), 
            bg="#172331", 
            fg="#ffb86c", 
            activebackground="#20344b",
            activeforeground="#ffffff",
            relief=tk.FLAT, 
            padx=8,
            command=self.set_hfp
        )
        btn_hfp.pack(side=tk.RIGHT, padx=4, pady=6)

        # Main Layout: Left Canvas (Scope Screen), Right Controls Panel
        main_pane = tk.Frame(self.root, bg="#080c10")
        main_pane.pack(fill=tk.BOTH, expand=True, padx=6, pady=4)

        # Oscilloscope Screen Frame
        screen_frame = tk.Frame(main_pane, bg="#05080c", bd=2, relief=tk.SOLID)
        screen_frame.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(0, 6))

        self.canvas = tk.Canvas(
            screen_frame, 
            bg="#040608", 
            highlightthickness=0, 
            cursor="crosshair"
        )
        self.canvas.pack(fill=tk.BOTH, expand=True)

        # Controls & AI Sidebar
        ctrl_frame = tk.Frame(main_pane, bg="#0d151c", width=310)
        ctrl_frame.pack(side=tk.RIGHT, fill=tk.Y)
        ctrl_frame.pack_propagate(False)

        # Section 1: Scope Mode Selector
        sec_modes = tk.LabelFrame(ctrl_frame, text=" DISPLAY CHANNEL ", font=("Consolas", 9, "bold"), fg="#00e5ff", bg="#0d151c")
        sec_modes.pack(fill=tk.X, padx=8, pady=4)

        self.mode_var = tk.StringVar(value="DUAL")
        for mode, name in [("DUAL", "Dual Scope (RF + Audio)"), ("RF", "RF Telemetry Scope"), ("AUDIO", "Audio Waveform"), ("FFT", "FFT Spectrum Analyzer")]:
            rb = tk.Radiobutton(
                sec_modes, 
                text=name, 
                variable=self.mode_var, 
                value=mode, 
                command=self.on_mode_change,
                font=("Consolas", 8), 
                fg="#cfd8dc", 
                bg="#0d151c", 
                selectcolor="#102030", 
                activebackground="#0d151c",
                activeforeground="#00e5ff"
            )
            rb.pack(anchor=tk.W, padx=6, pady=1)

        # Section 2: Scale & Trigger
        sec_scope = tk.LabelFrame(ctrl_frame, text=" TIMEBASE & GAIN ", font=("Consolas", 9, "bold"), fg="#00e5ff", bg="#0d151c")
        sec_scope.pack(fill=tk.X, padx=8, pady=4)

        f_sl = tk.Frame(sec_scope, bg="#0d151c")
        f_sl.pack(fill=tk.X, padx=6, pady=2)
        tk.Label(f_sl, text="Gain:", font=("Consolas", 8), fg="#90a4ae", bg="#0d151c").pack(side=tk.LEFT)
        self.slider_gain = tk.Scale(
            f_sl, from_=0.5, to=8.0, resolution=0.5, orient=tk.HORIZONTAL,
            bg="#0d151c", fg="#00ff88", highlightthickness=0, troughcolor="#172331",
            command=lambda v: setattr(self, 'gain', float(v))
        )
        self.slider_gain.set(2.0)
        self.slider_gain.pack(side=tk.RIGHT, fill=tk.X, expand=True)

        f_tl = tk.Frame(sec_scope, bg="#0d151c")
        f_tl.pack(fill=tk.X, padx=6, pady=2)
        tk.Label(f_tl, text="Sweep:", font=("Consolas", 8), fg="#90a4ae", bg="#0d151c").pack(side=tk.LEFT)
        self.slider_time = tk.Scale(
            f_tl, from_=0.5, to=3.0, resolution=0.2, orient=tk.HORIZONTAL,
            bg="#0d151c", fg="#00ff88", highlightthickness=0, troughcolor="#172331",
            command=lambda v: setattr(self, 'timebase', float(v))
        )
        self.slider_time.set(1.0)
        self.slider_time.pack(side=tk.RIGHT, fill=tk.X, expand=True)

        self.btn_run = tk.Button(
            sec_scope, 
            text="⏸ FREEZE TRACE", 
            font=("Consolas", 8, "bold"), 
            bg="#223344", 
            fg="#ffffff", 
            relief=tk.FLAT,
            command=self.toggle_freeze
        )
        self.btn_run.pack(fill=tk.X, padx=6, pady=4)

        # Section 3: AI Translation & Binaural Processing Module
        sec_ai = tk.LabelFrame(ctrl_frame, text=" AI TRANSLATOR & BINAURAL ", font=("Consolas", 9, "bold"), fg="#ffb86c", bg="#0d151c")
        sec_ai.pack(fill=tk.BOTH, expand=True, padx=8, pady=4)

        # Audio Source Radio
        tk.Label(sec_ai, text="Audioquelle / Input:", font=("Consolas", 8, "bold"), fg="#8be9fd", bg="#0d151c").pack(anchor=tk.W, padx=6, pady=(2, 0))
        self.src_var = tk.StringVar(value="LIVESTREAM")
        rb_live = tk.Radiobutton(
            sec_ai, text="📻 Livestream / Desktop (No Mic)", variable=self.src_var, value="LIVESTREAM",
            font=("Consolas", 8), fg="#50fa7b", bg="#0d151c", selectcolor="#102030", activebackground="#0d151c"
        )
        rb_live.pack(anchor=tk.W, padx=6)
        rb_mic = tk.Radiobutton(
            sec_ai, text="🎙️ HTC NE20 Mic (Eigene Stimme)", variable=self.src_var, value="MIC",
            font=("Consolas", 8), fg="#ffb86c", bg="#0d151c", selectcolor="#102030", activebackground="#0d151c"
        )
        rb_mic.pack(anchor=tk.W, padx=6)

        # Binaural Split Toggle Checkbox
        self.binaural_var = tk.BooleanVar(value=True)
        cb_bin = tk.Checkbutton(
            sec_ai, 
            text="🎧 Binaural Split: L:Original | R:Übersetzung", 
            variable=self.binaural_var,
            font=("Consolas", 8, "bold"), 
            fg="#ff79c6", 
            bg="#0d151c", 
            selectcolor="#102030",
            activebackground="#0d151c",
            activeforeground="#ff79c6"
        )
        cb_bin.pack(anchor=tk.W, padx=6, pady=4)

        # Target Language Dropdown
        tk.Label(sec_ai, text="Sprachmodus:", font=("Consolas", 8), fg="#90a4ae", bg="#0d151c").pack(anchor=tk.W, padx=6)
        self.combo_lang = ttk.Combobox(
            sec_ai, 
            values=[
                "🇰🇷 KR ➔ 🇩🇪 DE (Koreanisch zu Deutsch)",
                "🇰🇷 KR ➔ 🇬🇧 EN (Koreanisch zu Englisch)",
                "🇬🇧 EN ➔ 🇩🇪 DE (Englisch zu Deutsch)",
                "🇬🇧 EN ➔ 🇰🇷 KR (Englisch zu Koreanisch)",
                "🇩🇪 DE ➔ 🇰🇷 KR (Deutsch zu Koreanisch)",
                "🇩🇪 DE ➔ 🇬🇧 EN (Deutsch zu Englisch)",
                "⚡ Auto-Detect (KR/EN -> DE, DE -> EN)"
            ], 
            state="readonly", 
            font=("Consolas", 8)
        )
        self.combo_lang.current(0)
        self.combo_lang.pack(fill=tk.X, padx=6, pady=(0, 4))

        # Action Buttons
        self.btn_ai_rec = tk.Button(
            sec_ai, 
            text="⚡ 4s CHUNK ÜBERSETZEN", 
            font=("Consolas", 9, "bold"), 
            bg="#992233", 
            fg="#ffffff", 
            relief=tk.FLAT,
            command=self.trigger_ai_translation
        )
        self.btn_ai_rec.pack(fill=tk.X, padx=6, pady=3)

        self.btn_auto_loop = tk.Button(
            sec_ai, 
            text="🔁 AUTO-STREAM TRANSLATE (Start)", 
            font=("Consolas", 8, "bold"), 
            bg="#204030", 
            fg="#50fa7b", 
            relief=tk.FLAT,
            command=self.toggle_auto_loop
        )
        self.btn_auto_loop.pack(fill=tk.X, padx=6, pady=2)

        self.lbl_ai_status = tk.Label(
            sec_ai, 
            text="Bereit: Digital Loopback aktiv", 
            font=("Consolas", 8), 
            fg="#50fa7b", 
            bg="#0d151c",
            wraplength=290
        )
        self.lbl_ai_status.pack(anchor=tk.W, padx=6, pady=2)

        self.txt_ai_output = tk.Text(
            sec_ai, 
            height=6, 
            font=("Consolas", 8), 
            bg="#06090d", 
            fg="#f8f8f2", 
            relief=tk.FLAT,
            wrap=tk.WORD
        )
        self.txt_ai_output.pack(fill=tk.BOTH, expand=True, padx=6, pady=(2, 6))
        self.txt_ai_output.insert(tk.END, "Livestream & AI Output...\n")

        # Bottom HUD Status Line
        self.hud = tk.Frame(self.root, bg="#05080c", height=28)
        self.hud.pack(fill=tk.X, side=tk.BOTTOM, padx=6, pady=(0, 4))

        self.lbl_hud_left = tk.Label(
            self.hud, 
            text="CH1 [AUDIO]: Vpp: 0.00V | Vrms: 0.00V | Freq: --- Hz", 
            font=("Consolas", 9), 
            fg="#00e5ff", 
            bg="#05080c"
        )
        self.lbl_hud_left.pack(side=tk.LEFT, padx=10)

        self.lbl_hud_right = tk.Label(
            self.hud, 
            text="CH2 [RF 2.4GHz]: RSSI: -30 dBm | LQI: 200/255 | TX: 4 dBm", 
            font=("Consolas", 9), 
            fg="#50fa7b", 
            bg="#05080c"
        )
        self.lbl_hud_right.pack(side=tk.RIGHT, padx=10)

    def on_mode_change(self):
        self.current_mode = self.mode_var.get()

    def toggle_freeze(self):
        self.paused = not self.paused
        if self.paused:
            self.btn_run.config(text="▶ RESUME TRACE", bg="#2e7d32")
        else:
            self.btn_run.config(text="⏸ FREEZE TRACE", bg="#223344")

    def set_a2dp(self):
        def task():
            os.system(f"pactl set-card-profile {DEVICE_CARD} a2dp-sink")
            time.sleep(0.5)
            self.route_all_audio()
            self.root.after(0, lambda: self.lbl_dev_status.config(text=f"TARGET: {DEVICE_NAME} ● HiFi A2DP Active"))
        threading.Thread(target=task, daemon=True).start()

    def set_hfp(self):
        threading.Thread(target=self._run_cmd, args=(f"pactl set-card-profile {DEVICE_CARD} headset-head-unit-msbc", "Switched to Headset (mSBC)")).start()

    def route_all_audio(self):
        def task():
            res = subprocess.run(["pactl", "list", "sinks", "short"], capture_output=True, text=True)
            sink_name = "bluez_output.41_42_D4_14_B4_9D.1"
            for line in res.stdout.splitlines():
                if "41_42_D4_14_B4_9D" in line:
                    parts = line.split()
                    if len(parts) >= 2:
                        sink_name = parts[1]
                        break
            os.system(f"pactl set-default-sink {sink_name}")
            os.system(f"for id in $(pactl list sink-inputs short | awk '{{print $1}}'); do pactl move-sink-input \"$id\" {sink_name} 2>/dev/null; done")
            self.root.after(0, lambda: self.lbl_dev_status.config(text=f"TARGET: {DEVICE_NAME} ● Audio Routed to Earbuds"))
        threading.Thread(target=task, daemon=True).start()

    def _run_cmd(self, cmd, msg):
        os.system(cmd)
        time.sleep(0.5)
        self.root.after(0, lambda: self.lbl_dev_status.config(text=f"TARGET: {DEVICE_NAME} ● {msg}"))

    # RF Telemetry Thread (hcitool rssi, lq, tpl)
    def start_telemetry_thread(self):
        def loop():
            while self.running:
                if not self.paused:
                    try:
                        res = subprocess.run(["hcitool", "rssi", DEVICE_MAC], capture_output=True, text=True, timeout=1.0)
                        if "RSSI return value:" in res.stdout:
                            val = int(res.stdout.split(":")[-1].strip())
                            self.current_rssi = val
                            self.rssi_history.append(float(val))
                            self.rssi_history.pop(0)

                        res_lq = subprocess.run(["hcitool", "lq", DEVICE_MAC], capture_output=True, text=True, timeout=1.0)
                        if "Link quality:" in res_lq.stdout:
                            lq_val = int(res_lq.stdout.split(":")[-1].strip())
                            self.current_lq = lq_val
                            self.lq_history.append(float(lq_val))
                            self.lq_history.pop(0)

                        res_tx = subprocess.run(["hcitool", "tpl", DEVICE_MAC], capture_output=True, text=True, timeout=1.0)
                        if "power level:" in res_tx.stdout:
                            tx_val = int(res_tx.stdout.split(":")[-1].strip())
                            self.current_tx = tx_val
                            self.tx_history.append(float(tx_val))
                            self.tx_history.pop(0)
                    except Exception:
                        pass
                time.sleep(0.1)

        self.rf_thread = threading.Thread(target=loop, daemon=True)
        self.rf_thread.start()

    # Audio Capture Stream for Scope (reads live audio from loopback monitor or mic)
    def start_audio_capture(self):
        def loop():
            while self.running:
                try:
                    # Find loopback monitor or default
                    res = subprocess.run(["pactl", "list", "sources", "short"], capture_output=True, text=True)
                    target_src = None
                    for line in res.stdout.splitlines():
                        if "41_42_D4_14_B4_9D" in line and "monitor" in line:
                            target_src = line.split()[1]
                            break
                    if not target_src:
                        for line in res.stdout.splitlines():
                            if "monitor" in line:
                                target_src = line.split()[1]
                                break

                    cmd = ["parec", "--format=s16le", "--rate=16000", "--channels=1"]
                    if target_src:
                        cmd.extend(["-d", target_src])

                    self.audio_proc = subprocess.Popen(
                        cmd,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.DEVNULL
                    )
                    while self.running and self.audio_proc.poll() is None:
                        raw = self.audio_proc.stdout.read(1024)
                        if not raw or len(raw) < 1024:
                            break
                        if self.paused:
                            time.sleep(0.05)
                            continue

                        samples = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
                        scaled_samples = samples * self.gain
                        
                        n = len(scaled_samples)
                        if n >= self.audio_len:
                            self.audio_buffer = scaled_samples[:self.audio_len]
                        else:
                            self.audio_buffer = np.roll(self.audio_buffer, -n)
                            self.audio_buffer[-n:] = scaled_samples

                        self.vpp = float(np.ptp(self.audio_buffer))
                        self.vrms = float(np.sqrt(np.mean(self.audio_buffer**2)))

                        windowed = self.audio_buffer * np.hanning(self.audio_len)
                        fft_vals = np.abs(np.fft.rfft(windowed))
                        self.fft_buffer = fft_vals[:self.audio_len // 2]
                        peak_idx = np.argmax(self.fft_buffer[2:]) + 2
                        self.freq_peak = (peak_idx * 16000) / self.audio_len
                except Exception:
                    time.sleep(1.0)
                finally:
                    if self.audio_proc:
                        try:
                            self.audio_proc.kill()
                        except Exception:
                            pass

        self.audio_thread = threading.Thread(target=loop, daemon=True)
        self.audio_thread.start()

    # Canvas Drawing: Oscilloscope Display
    def update_display(self):
        if not self.running:
            return

        w = self.canvas.winfo_width()
        h = self.canvas.winfo_height()

        if w > 20 and h > 20:
            self.canvas.delete("all")
            self.draw_graticule(w, h)

            if self.current_mode == "DUAL":
                h_half = h / 2.0
                self.draw_rf_scope(0, 0, w, h_half, title="CH2: 2.4GHz RF TELEMETRY [RSSI / LINK QUALITY / TX]")
                self.draw_audio_waveform(0, h_half, w, h_half, title="CH1: AUDIO WAVEFORM [LIVESTREAM / MIC]")
            elif self.current_mode == "RF":
                self.draw_rf_scope(0, 0, w, h, title="CH2: 2.4GHz RF TELEMETRY [RSSI & LINK QUALITY HISTORICAL SWEEP]")
            elif self.current_mode == "AUDIO":
                self.draw_audio_waveform(0, 0, w, h, title="CH1: AUDIO WAVEFORM [TIME DOMAIN]")
            elif self.current_mode == "FFT":
                self.draw_fft_spectrum(0, 0, w, h, title="CH1: FFT SPECTRUM ANALYZER [0 Hz - 8000 Hz]")

            self.lbl_hud_left.config(
                text=f"CH1 [AUDIO]: Vpp: {self.vpp:.2f}V | Vrms: {self.vrms:.2f}V | Peak Freq: {self.freq_peak:.0f} Hz"
            )
            self.lbl_hud_right.config(
                text=f"CH2 [RF 2.4GHz]: RSSI: {self.current_rssi} dBm | LQI: {self.current_lq}/255 ({int(self.current_lq/2.55)}%) | TX: {self.current_tx} dBm"
            )

        self.root.after(33, self.update_display)

    def draw_graticule(self, w, h):
        cols = 10
        rows = 8
        for i in range(1, cols):
            x = i * (w / cols)
            self.canvas.create_line(x, 0, x, h, fill="#0d1b1a", dash=(2, 4))
        for j in range(1, rows):
            y = j * (h / rows)
            self.canvas.create_line(0, y, w, y, fill="#0d1b1a", dash=(2, 4))

        cx = w / 2
        cy = h / 2
        self.canvas.create_line(cx, 0, cx, h, fill="#122c2a")
        self.canvas.create_line(0, cy, w, cy, fill="#122c2a")

    def draw_audio_waveform(self, ox, oy, ow, oh, title):
        self.canvas.create_text(ox + 14, oy + 16, text=title, anchor=tk.W, fill="#00e5ff", font=("Consolas", 9, "bold"))
        self.canvas.create_text(ox + ow - 14, oy + 16, text=f"GAIN: x{self.gain} | TIME: x{self.timebase}", anchor=tk.E, fill="#50fa7b", font=("Consolas", 8))

        cy = oy + oh / 2.0
        self.canvas.create_line(ox, cy, ox + ow, cy, fill="#003b38", dash=(4, 4))

        pts = []
        n_samples = len(self.audio_buffer)
        step = max(1, int(n_samples / ow))
        
        for idx, i in enumerate(range(0, n_samples, step)):
            x = ox + (idx / (n_samples / step)) * ow
            val = max(-1.0, min(1.0, self.audio_buffer[i]))
            y = cy - val * (oh * 0.42)
            pts.extend([x, y])

        if len(pts) >= 4:
            self.canvas.create_line(pts, fill="#004433", width=3, smooth=True)
            self.canvas.create_line(pts, fill="#00f0ff", width=1.5, smooth=True)

    def draw_rf_scope(self, ox, oy, ow, oh, title):
        self.canvas.create_text(ox + 14, oy + 16, text=title, anchor=tk.W, fill="#50fa7b", font=("Consolas", 9, "bold"))
        self.canvas.create_text(ox + ow - 14, oy + 16, text=f"RSSI: {self.current_rssi} dBm (LQI: {self.current_lq})", anchor=tk.E, fill="#ffb86c", font=("Consolas", 8, "bold"))

        for dbm, label in [(-10, "-10 dBm (STARK)"), (-30, "-30 dBm (GUT)"), (-60, "-60 dBm (MITTEL)"), (-85, "-85 dBm (SCHWACH)")]:
            norm_y = 1.0 - (dbm - (-90.0)) / 90.0
            y = oy + 26 + norm_y * (oh - 44)
            self.canvas.create_line(ox, y, ox + ow, y, fill="#12251d", dash=(3, 3))
            self.canvas.create_text(ox + ow - 60, y - 6, text=label, fill="#2b5e4a", font=("Consolas", 7))

        pts_lq = []
        n_pts = len(self.lq_history)
        for i, val in enumerate(self.lq_history):
            x = ox + (i / (n_pts - 1)) * ow
            norm_val = val / 255.0
            y = oy + oh - 16 - norm_val * (oh - 44)
            pts_lq.extend([x, y])

        if len(pts_lq) >= 4:
            self.canvas.create_line(pts_lq, fill="#0088aa", width=1.2, dash=(4, 2), smooth=True)

        pts_rssi = []
        for i, val in enumerate(self.rssi_history):
            x = ox + (i / (n_pts - 1)) * ow
            c_val = max(-90.0, min(0.0, val))
            norm_y = 1.0 - (c_val - (-90.0)) / 90.0
            y = oy + 26 + norm_y * (oh - 44)
            pts_rssi.extend([x, y])

        if len(pts_rssi) >= 4:
            self.canvas.create_line(pts_rssi, fill="#005522", width=4, smooth=True)
            self.canvas.create_line(pts_rssi, fill="#00ff66", width=2, smooth=True)
            lx, ly = pts_rssi[-2], pts_rssi[-1]
            self.canvas.create_oval(lx - 4, ly - 4, lx + 4, ly + 4, fill="#ffffff", outline="#00ff66")

    def draw_fft_spectrum(self, ox, oy, ow, oh, title):
        self.canvas.create_text(ox + 14, oy + 16, text=title, anchor=tk.W, fill="#ff79c6", font=("Consolas", 9, "bold"))
        self.canvas.create_text(ox + ow - 14, oy + 16, text="SPEECH HARMONICS (300Hz - 3400Hz)", anchor=tk.E, fill="#8be9fd", font=("Consolas", 8))

        n_bins = len(self.fft_buffer)
        if n_bins == 0:
            return

        bar_w = ow / n_bins
        max_val = max(1.0, float(np.max(self.fft_buffer)))

        for i in range(n_bins):
            x1 = ox + i * bar_w
            x2 = x1 + max(1, bar_w - 1)
            norm_h = min(1.0, (self.fft_buffer[i] / max_val) * (self.gain / 2.0))
            y1 = oy + oh - 20 - norm_h * (oh - 50)
            y2 = oy + oh - 20
            color = "#00ff88" if norm_h < 0.5 else "#ffb86c" if norm_h < 0.8 else "#ff5555"
            self.canvas.create_rectangle(x1, y1, x2, y2, fill=color, outline="")

        self.canvas.create_line(ox, oy + oh - 20, ox + ow, oy + oh - 20, fill="#283344")

    # Continuous Auto-Loop Toggle
    def toggle_auto_loop(self):
        self.auto_loop_running = not self.auto_loop_running
        if self.auto_loop_running:
            self.btn_auto_loop.config(text="⏹ STOP AUTO-STREAM", bg="#882222", fg="#ffffff")
            self.lbl_ai_status.config(text="Auto-Stream Translation Active (Looping)...", fg="#50fa7b")
            threading.Thread(target=self._auto_loop_worker, daemon=True).start()
        else:
            self.btn_auto_loop.config(text="🔁 AUTO-STREAM TRANSLATE (Start)", bg="#204030", fg="#50fa7b")
            self.lbl_ai_status.config(text="Auto-Stream stopped.", fg="#90a4ae")

    def _auto_loop_worker(self):
        while self.auto_loop_running and self.running:
            self._execute_pipeline(is_auto=True)
            time.sleep(1.0)

    # Trigger Single Translation
    def trigger_ai_translation(self):
        threading.Thread(target=self._execute_pipeline, kwargs={"is_auto": False}, daemon=True).start()

    # Core Translation & Binaural Processing Pipeline
    def _execute_pipeline(self, is_auto=False):
        source = self.src_var.get()
        mode_lang = self.combo_lang.get()
        use_binaural = self.binaural_var.get()

        if not is_auto:
            self.btn_ai_rec.config(state=tk.DISABLED, text="🔴 CAPTURING 3.5s...", bg="#ff5555")
        self.lbl_ai_status.config(text=f"Listening ({'Livestream Loopback' if source == 'LIVESTREAM' else 'HTC Mic'})...", fg="#ff5555")

        wav_path = "/tmp/htc_rec.wav"

        # 1. Capture Audio
        if source == "MIC":
            os.system(f"pactl set-card-profile {DEVICE_CARD} headset-head-unit-msbc")
            time.sleep(0.4)
            cmd_rec = (
                f"ffmpeg -y -f pulse -i bluez_input.41_42_D4_14_B4_9D.0 -t 3.5 "
                f"-ar 16000 -ac 1 -c:a pcm_s16le {wav_path} >/dev/null 2>&1"
            )
            os.system(cmd_rec)
        else:
            # Livestream: Capture directly from active sink monitor without mic and without profile switch!
            # Find loopback monitor
            res = subprocess.run(["pactl", "list", "sources", "short"], capture_output=True, text=True)
            loop_src = "bluez_output.41_42_D4_14_B4_9D.1.monitor"
            for line in res.stdout.splitlines():
                if "41_42_D4_14_B4_9D" in line and "monitor" in line:
                    loop_src = line.split()[1]
                    break
            cmd_rec = (
                f"ffmpeg -y -f pulse -i {loop_src} -t 3.5 "
                f"-ar 16000 -ac 1 -c:a pcm_s16le {wav_path} >/dev/null 2>&1"
            )
            os.system(cmd_rec)

        # Determine language configuration
        whisper_lang = "auto"
        source_code = "AUTO"
        target_lang_code = "de"
        prompt_instruction = ""

        if "KR ➔ 🇩🇪 DE" in mode_lang:
            whisper_lang = "ko"
            source_code = "KO"
            target_lang_code = "de"
            prompt_instruction = "Translate this Korean speech directly into natural, fluent German. Do not add explanations."
        elif "KR ➔ 🇬🇧 EN" in mode_lang:
            whisper_lang = "ko"
            source_code = "KO"
            target_lang_code = "en"
            prompt_instruction = "Translate this Korean speech directly into natural, fluent English. Do not add explanations."
        elif "EN ➔ 🇩🇪 DE" in mode_lang:
            whisper_lang = "en"
            source_code = "EN"
            target_lang_code = "de"
            prompt_instruction = "Translate this English speech directly into natural, fluent German. Do not add explanations."
        elif "EN ➔ 🇰🇷 KR" in mode_lang:
            whisper_lang = "en"
            source_code = "EN"
            target_lang_code = "ko"
            prompt_instruction = "Translate this English speech directly into natural, fluent Korean. Do not add explanations."
        elif "DE ➔ 🇰🇷 KR" in mode_lang:
            whisper_lang = "de"
            source_code = "DE"
            target_lang_code = "ko"
            prompt_instruction = "Translate this German speech directly into natural, fluent Korean. Do not add explanations."
        elif "DE ➔ 🇬🇧 EN" in mode_lang:
            whisper_lang = "de"
            source_code = "DE"
            target_lang_code = "en"
            prompt_instruction = "Translate this German speech directly into natural, fluent English. Do not add explanations."
        else:
            whisper_lang = "auto"
            prompt_instruction = "Detect whether input is Korean (KO), English (EN) or German (DE). If KO or EN -> translate to German (DE). If DE -> translate to English (EN)."

        self.root.after(0, lambda: self.lbl_ai_status.config(text=f"Transcribing ({whisper_lang.upper()}) with Whisper...", fg="#ffb86c"))

        # 2. Whisper Speech-to-Text
        transcript = ""
        try:
            whisper_bin = "/root/coding/whisper.cpp/build/bin/whisper-cli"
            model_bin = "/root/coding/AI_Desktop/ggml-base.bin"
            if os.path.exists(whisper_bin) and os.path.exists(model_bin):
                res_w = subprocess.run(
                    [whisper_bin, "-m", model_bin, "-f", wav_path, "-nt", "-l", whisper_lang],
                    capture_output=True, text=True, timeout=12
                )
                transcript = res_w.stdout.strip()
        except Exception:
            pass

        if not transcript or len(transcript) < 2:
            self.root.after(0, lambda: self.lbl_ai_status.config(text="Kein klares Sprachsignal erkannt.", fg="#ffb86c"))
            if not is_auto:
                self.root.after(0, lambda: self.btn_ai_rec.config(state=tk.NORMAL, text="⚡ 4s CHUNK ÜBERSETZEN", bg="#992233"))
            if source == "MIC":
                self.set_a2dp()
            return

        clean_transcript = transcript.replace('"', '\\"').replace('\n', ' ').strip()
        self.root.after(0, lambda: self.lbl_ai_status.config(text=f"Translating: '{clean_transcript[:30]}'...", fg="#50fa7b"))

        # 3. Targeted Translation via Groq Gateway
        prompt = (
            f"{prompt_instruction}\n"
            f"Input speech: \"{clean_transcript}\"\n"
            f"Output strictly in JSON format without markdown:\n"
            f'{{"source": "{source_code}", "target": "{target_lang_code.upper()}", "original": "{clean_transcript}", "translated": "<translation>"}}'
        )

        detected_code = source_code
        translated_text = transcript
        try:
            payload = json.dumps({"prompt": prompt})
            res = subprocess.run(
                ["curl", "-s", "http://localhost:11434/api/generate", "-d", payload],
                capture_output=True, text=True, timeout=15
            )
            raw_res = json.loads(res.stdout).get("response", "")
            # Clean JSON
            json_str = raw_res
            if "{" in raw_res and "}" in raw_res:
                json_str = raw_res[raw_res.find("{"):raw_res.rfind("}")+1]
            t_data = json.loads(json_str)
            target_lang_code = t_data.get("target", target_lang_code).lower()
            translated_text = t_data.get("translated", transcript)
            detected_code = t_data.get("source", detected_code)
        except Exception:
            translated_text = f"Übersetzung: {transcript}"

        # 4. Display in UI
        def update_ui():
            self.txt_ai_output.insert(
                tk.END, 
                f"[{time.strftime('%H:%M:%S')}] [{detected_code} -> {target_lang_code.upper()}]\n"
                f"Original:   {transcript}\n"
                f"Übersetzt:  {translated_text}\n"
                f"{'-'*32}\n"
            )
            self.txt_ai_output.see(tk.END)
            self.lbl_ai_status.config(
                text=f"Audio fertig: L: Original | R: {target_lang_code.upper()}", 
                fg="#50fa7b"
            )
            if not is_auto:
                self.btn_ai_rec.config(state=tk.NORMAL, text="⚡ 4s CHUNK ÜBERSETZEN", bg="#992233")

        self.root.after(0, update_ui)

        # 5. Synthesize TTS with Piper
        piper_bin = "/root/coding/AI_Desktop/piper/piper"
        tts_wav = "/tmp/htc_tts.wav"
        
        # Pick model
        if target_lang_code == "en":
            piper_model = "/root/coding/AI_Desktop/voice.onnx"
        elif target_lang_code == "ko":
            piper_model = "/root/coding/AI_Desktop/ko_KR-kss-medium.onnx"
        else:
            piper_model = "/root/coding/AI_Desktop/de_DE-ramona-low.onnx"

        with open("/tmp/htc_tts.txt", "w", encoding="utf-8") as f:
            f.write(translated_text)

        cmd_tts = f"nice -n 19 {piper_bin} --model {piper_model} --output_file {tts_wav} < /tmp/htc_tts.txt >/dev/null 2>&1"
        os.system(cmd_tts)

        # 6. Playback: Binaural Split (L: Original | R: Translation) or Centered
        sink_target = subprocess.run(["pactl", "list", "sinks", "short"], capture_output=True, text=True).stdout
        target_sink = "bluez_output.41_42_D4_14_B4_9D.1"
        for line in sink_target.splitlines():
            if "41_42_D4_14_B4_9D" in line:
                target_sink = line.split()[1]
                break

        if use_binaural and os.path.exists(wav_path) and os.path.exists(tts_wav):
            binaural_wav = "/tmp/htc_binaural.wav"
            # Join: Left Channel = Original (0:a), Right Channel = TTS (1:a)
            cmd_binaural = (
                f"ffmpeg -y -i {wav_path} -i {tts_wav} -filter_complex "
                f"\"[0:a]aformat=sample_rates=48000:channel_layouts=mono[l];"
                f"[1:a]aformat=sample_rates=48000:channel_layouts=mono[r];"
                f"[l][r]join=inputs=2:channel_layout=stereo[out]\" -map \"[out]\" {binaural_wav} >/dev/null 2>&1"
            )
            os.system(cmd_binaural)
            os.system(f"pw-play --target {target_sink} {binaural_wav}")
        else:
            os.system(f"pw-play --target {target_sink} {tts_wav}")

        # 7. Restore A2DP & Spotify routing if MIC mode was used
        if source == "MIC":
            time.sleep(0.5)
            self.set_a2dp()

    def on_closing(self):
        self.running = False
        self.auto_loop_running = False
        if self.audio_proc:
            try:
                self.audio_proc.kill()
            except Exception:
                pass
        # Restore audio routing to default PC speakers
        try:
            os.system("pactl set-default-sink alsa_output.pci-0000_00_1f.3.analog-stereo >/dev/null 2>&1")
            os.system("for id in $(pactl list sink-inputs short | awk '{print $1}'); do pactl move-sink-input $id alsa_output.pci-0000_00_1f.3.analog-stereo 2>/dev/null; done")
        except Exception:
            pass
        self.root.destroy()

if __name__ == "__main__":
    if "DISPLAY" not in os.environ:
        os.environ["DISPLAY"] = ":0"

    root = tk.Tk()
    app = OscilloscopeApp(root)
    root.protocol("WM_DELETE_WINDOW", app.on_closing)
    root.mainloop()
