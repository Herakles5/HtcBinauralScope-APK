package com.meinos.htctranslator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class GroqCloudClient {

    public static class TranslationResult {
        public final boolean success;
        public final String original;
        public final String translated;
        public final String sourceLang;
        public final String targetLang;
        public final String error;

        public TranslationResult(boolean success, String original, String translated, String sourceLang, String targetLang, String error) {
            this.success = success;
            this.original = original;
            this.translated = translated;
            this.sourceLang = sourceLang;
            this.targetLang = targetLang;
            this.error = error;
        }
    }

    private static final String GROQ_TRANSCRIPTION_URL = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";

    public static TranslationResult processAudio(byte[] wavBytes, String apiKey, String langMode) {
        String whisperLang = null;
        String sourceName = "KO";
        String targetName = "DE";
        String promptInstruction = "Translate the following speech directly into natural, fluent German. Return only the German translation, no notes.";

        if ("KR_DE".equalsIgnoreCase(langMode)) {
            whisperLang = "ko";
            sourceName = "KO";
            targetName = "DE";
            promptInstruction = "Translate the following Korean speech directly into natural, fluent German. Return only the German translation, no notes.";
        } else if ("KR_EN".equalsIgnoreCase(langMode)) {
            whisperLang = "ko";
            sourceName = "KO";
            targetName = "EN";
            promptInstruction = "Translate the following Korean speech directly into natural, fluent English. Return only the English translation, no notes.";
        } else if ("EN_DE".equalsIgnoreCase(langMode)) {
            whisperLang = "en";
            sourceName = "EN";
            targetName = "DE";
            promptInstruction = "Translate the following English speech directly into natural, fluent German. Return only the German translation, no notes.";
        } else if ("EN_KR".equalsIgnoreCase(langMode)) {
            whisperLang = "en";
            sourceName = "EN";
            targetName = "KO";
            promptInstruction = "Translate the following English speech directly into natural, fluent Korean. Return only the Korean translation, no notes.";
        } else if ("DE_KR".equalsIgnoreCase(langMode)) {
            whisperLang = "de";
            sourceName = "DE";
            targetName = "KO";
            promptInstruction = "Translate the following German speech directly into natural, fluent Korean. Return only the Korean translation, no notes.";
        } else if ("DE_EN".equalsIgnoreCase(langMode)) {
            whisperLang = "de";
            sourceName = "DE";
            targetName = "EN";
            promptInstruction = "Translate the following German speech directly into natural, fluent English. Return only the English translation, no notes.";
        } else {
            whisperLang = null;
            sourceName = "AUTO";
            targetName = "DE";
            promptInstruction = "Detect the spoken language. If Korean or English, translate directly into natural German. If German, translate to English. Return only the translation, no extra text.";
        }

        try {
            // 1. Transcribe Audio via Groq Whisper-large-v3-turbo
            String transcript = transcribeWithGroq(wavBytes, apiKey, whisperLang);
            if (transcript == null || transcript.trim().length() < 2) {
                return new TranslationResult(false, "", "", sourceName, targetName, "No speech detected");
            }

            // 2. Translate via Groq qwen/qwen3.8-27b
            String translated = translateWithGroq(transcript.trim(), apiKey, promptInstruction);
            if (translated == null || translated.trim().isEmpty()) {
                translated = transcript;
            }

            return new TranslationResult(true, transcript.trim(), translated.trim(), sourceName, targetName, null);
        } catch (Exception e) {
            return new TranslationResult(false, "", "", sourceName, targetName, e.getMessage());
        }
    }

    private static String transcribeWithGroq(byte[] wavBytes, String apiKey, String lang) throws Exception {
        String boundary = "===" + System.currentTimeMillis() + "===";
        URL url = new URL(GROQ_TRANSCRIPTION_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        OutputStream os = conn.getOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true);

        // model parameter
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        writer.append("whisper-large-v3-turbo").append("\r\n");

        // response_format parameter
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n");
        writer.append("json").append("\r\n");

        // language parameter (if specified)
        if (lang != null && !lang.isEmpty()) {
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"language\"\r\n\r\n");
            writer.append(lang).append("\r\n");
        }

        // file parameter
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"chunk.wav\"\r\n");
        writer.append("Content-Type: audio/wav\r\n\r\n");
        writer.flush();

        os.write(wavBytes);
        os.flush();

        writer.append("\r\n");
        writer.append("--").append(boundary).append("--\r\n");
        writer.flush();
        writer.close();

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int r;
        while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
        String resp = baos.toString("UTF-8");

        if (code != 200) {
            throw new IOException("Groq Whisper error " + code + ": " + resp);
        }

        JSONObject json = new JSONObject(resp);
        return json.optString("text", "");
    }

    private static String translateWithGroq(String text, String apiKey, String systemPrompt) throws Exception {
        URL url = new URL(GROQ_CHAT_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");

        JSONObject payload = new JSONObject();
        payload.put("model", "qwen/qwen3.8-27b");

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", text);
        messages.put(userMsg);

        payload.put("messages", messages);
        payload.put("temperature", 0.2);

        OutputStream os = conn.getOutputStream();
        os.write(payload.toString().getBytes("UTF-8"));
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int r;
        while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
        String resp = baos.toString("UTF-8");

        if (code != 200) {
            throw new IOException("Groq Chat error " + code + ": " + resp);
        }

        JSONObject json = new JSONObject(resp);
        JSONArray choices = json.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
            if (msg != null) {
                return msg.optString("content", text);
            }
        }
        return text;
    }
}
