#!/usr/bin/env python3
import http.server
import socketserver
import json
import os
import subprocess
import re
import uuid

PORT = 11436
WHISPER_BIN = "/root/coding/whisper.cpp/build/bin/whisper-cli"
MODEL_BIN = "/root/coding/AI_Desktop/ggml-base.bin"

# Language pair configurations
LANG_CONFIGS = {
    "KR_DE": {
        "whisper_lang": "ko",
        "source_code": "KO",
        "target_code": "DE",
        "prompt_tmpl": "Translate this Korean speech directly into natural, fluent German. Do not add explanations.\nKorean: \"{clean}\"\nOutput JSON strictly:\n{{\"source\": \"KO\", \"target\": \"DE\", \"original\": \"{clean}\", \"translated\": \"<german translation>\"}}"
    },
    "KR_EN": {
        "whisper_lang": "ko",
        "source_code": "KO",
        "target_code": "EN",
        "prompt_tmpl": "Translate this Korean speech directly into natural, fluent English. Do not add explanations.\nKorean: \"{clean}\"\nOutput JSON strictly:\n{{\"source\": \"KO\", \"target\": \"EN\", \"original\": \"{clean}\", \"translated\": \"<english translation>\"}}"
    },
    "EN_DE": {
        "whisper_lang": "en",
        "source_code": "EN",
        "target_code": "DE",
        "prompt_tmpl": "Translate this English speech directly into natural, fluent German. Do not add explanations.\nEnglish: \"{clean}\"\nOutput JSON strictly:\n{{\"source\": \"EN\", \"target\": \"DE\", \"original\": \"{clean}\", \"translated\": \"<german translation>\"}}"
    },
    "EN_KR": {
        "whisper_lang": "en",
        "source_code": "EN",
        "target_code": "KO",
        "prompt_tmpl": "Translate this English speech directly into natural, fluent Korean. Do not add explanations.\nEnglish: \"{clean}\"\nOutput JSON strictly:\n{{\"source\": \"EN\", \"target\": \"KO\", \"original\": \"{clean}\", \"translated\": \"<korean translation>\"}}"
    },
    "DE_KR": {
        "whisper_lang": "de",
        "source_code": "DE",
        "target_code": "KO",
        "prompt_tmpl": "Translate this German speech directly into natural, fluent Korean. Do not add explanations.\nGerman: \"{clean}\"\nOutput JSON strictly:\n{{\"source\": \"DE\", \"target\": \"KO\", \"original\": \"{clean}\", \"translated\": \"<korean translation>\"}}"
    },
    "DE_EN": {
        "whisper_lang": "de",
        "source_code": "DE",
        "target_code": "EN",
        "prompt_tmpl": "Translate this German speech directly into natural, fluent English. Do not add explanations.\nGerman: \"{clean}\"\nOutput JSON strictly:\n{{\"source\": \"DE\", \"target\": \"EN\", \"original\": \"{clean}\", \"translated\": \"<english translation>\"}}"
    },
    "AUTO": {
        "whisper_lang": "auto",
        "source_code": "AUTO",
        "target_code": "DE",
        "prompt_tmpl": "You are a real-time speech translator for live streams.\nInput speech: \"{clean}\"\n1. Detect the spoken language: Korean (KO), English (EN), or German (DE).\n2. If input is KO or EN -> Translate into natural German (DE).\n   If input is DE -> Translate into natural English (EN).\nOutput JSON strictly:\n{{\"source\": \"KO/EN/DE\", \"target\": \"DE/EN\", \"original\": \"{clean}\", \"translated\": \"<translation>\"}}"
    }
}

class BridgeHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass  # silent

    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(b'{"status": "ready", "engine": "whisper+groq", "pairs": ["KR_DE","KR_EN","EN_DE","EN_KR","DE_KR","DE_EN","AUTO"]}')

    def do_POST(self):
        content_len = int(self.headers.get("Content-Length", 0))
        audio_data = self.rfile.read(content_len)

        # Mode from header (default KR_DE if omitted)
        lang_mode = self.headers.get("X-Lang-Mode", "KR_DE").upper().strip()
        if lang_mode not in LANG_CONFIGS:
            lang_mode = "KR_DE"

        cfg = LANG_CONFIGS[lang_mode]
        whisper_lang = cfg["whisper_lang"]
        source_code = cfg["source_code"]
        target_code = cfg["target_code"]

        wav_path = f"/tmp/phone_{uuid.uuid4().hex[:8]}.wav"
        with open(wav_path, "wb") as f:
            f.write(audio_data)

        # 1. Transcribe with Whisper using explicit or auto language and multi-threading
        transcript = ""
        try:
            cmd = [WHISPER_BIN, "-m", MODEL_BIN, "-f", wav_path, "-nt", "-l", whisper_lang, "-t", "6"]
            res = subprocess.run(cmd, capture_output=True, text=True, timeout=12)
            transcript = res.stdout.strip()
        except Exception as e:
            print(f"[BRIDGE] Whisper error: {e}", flush=True)
            transcript = ""
        finally:
            if os.path.exists(wav_path):
                try:
                    os.remove(wav_path)
                except Exception:
                    pass

        if not transcript or len(transcript) < 2:
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"success": False, "error": "No speech detected"}).encode("utf-8"))
            return

        clean_transcript = transcript.replace('"', '\\"').replace('\n', ' ').strip()
        print(f"[BRIDGE] [{lang_mode}] Transcribed: '{transcript}'", flush=True)

        # 2. Targeted Translation via Local Gateway
        prompt = cfg["prompt_tmpl"].replace("{clean}", clean_transcript)

        detected_src = source_code
        detected_tgt = target_code
        translated = transcript
        try:
            payload = json.dumps({"prompt": prompt})
            res_gw = subprocess.run(
                ["curl", "-s", "http://127.0.0.1:11434/api/generate", "-d", payload],
                capture_output=True, text=True, timeout=15
            )
            raw = json.loads(res_gw.stdout).get("response", "")
            if "{" in raw and "}" in raw:
                raw_json = raw[raw.find("{"):raw.rfind("}")+1]
                t_data = json.loads(raw_json)
                detected_src = t_data.get("source", detected_src)
                detected_tgt = t_data.get("target", detected_tgt)
                translated = t_data.get("translated", transcript)
            else:
                translated = raw.replace("[LANG: DE]", "").replace("[LANG: EN]", "").replace("[LANG: KO]", "").strip()
        except Exception as e:
            print(f"[BRIDGE] Gateway error: {e}", flush=True)
            translated = f"Übersetzung: {transcript}"

        print(f"[BRIDGE] [{detected_src} -> {detected_tgt}]: '{translated}'", flush=True)

        response_payload = {
            "success": True,
            "source": detected_src,
            "target": detected_tgt,
            "original": transcript,
            "translated": translated
        }

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(response_payload).encode("utf-8"))

def run():
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("0.0.0.0", PORT), BridgeHandler) as httpd:
        print(f"Phone Translator Bridge running on port {PORT} with 6-way language pairs...")
        httpd.serve_forever()

if __name__ == "__main__":
    run()
