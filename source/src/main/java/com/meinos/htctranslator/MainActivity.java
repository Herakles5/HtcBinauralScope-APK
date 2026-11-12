package com.meinos.htctranslator;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final int REQUEST_PERMISSIONS = 101;
    private static final int REQUEST_MEDIA_PROJECTION = 1002;
    private static final String PC_BRIDGE_URL = "http://10.50.169.111:11436/translate";

    private static final String[] LANG_LABELS = new String[]{
            "🇰🇷 KR ➔ 🇩🇪 DE (Korean to German)",
            "🇰🇷 KR ➔ 🇬🇧 EN (Korean to English)",
            "🇬🇧 EN ➔ 🇩🇪 DE (English to German)",
            "🇬🇧 EN ➔ 🇰🇷 KR (English to Korean)",
            "🇩🇪 DE ➔ 🇰🇷 KR (German to Korean)",
            "🇩🇪 DE ➔ 🇬🇧 EN (German to English)",
            "⚡ AUTO-DETECT (Auto Speech Recognition)"
    };

    private static final String[] LANG_CODES = new String[]{
            "KR_DE",
            "KR_EN",
            "EN_DE",
            "EN_KR",
            "DE_KR",
            "DE_EN",
            "AUTO"
    };

    private volatile String currentLangMode = "KR_DE";

    private OscilloscopeView oscilloscopeView;
    private TextView tvDeviceStatus, tvAudioMetrics, tvRfMetrics, tvStatus, tvLogOutput;
    private RadioGroup rgAudioSource;
    private RadioButton rbLivestream, rbMic;
    private SwitchCompat swBinaural;
    private EditText etServerIp;
    private Spinner spLangMode;
    private Button btnContinuousTranslate, btnTranslateChunk;

    private BinauralAudioEngine audioEngine;
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private TextToSpeech tts;
    private Handler mainHandler;
    private volatile boolean isContinuousActive = false;
    private Thread continuousThread;
    private String lastOriginal = "";
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        initViews();
        setupAudioEngine();
        checkAndRequestPermissions();

        tts = new TextToSpeech(this, this);
        startRfTelemetryLoop();
    }

    private void initViews() {
        oscilloscopeView = findViewById(R.id.oscilloscopeView);
        tvDeviceStatus = findViewById(R.id.tvDeviceStatus);
        tvAudioMetrics = findViewById(R.id.tvAudioMetrics);
        tvRfMetrics = findViewById(R.id.tvRfMetrics);
        tvStatus = findViewById(R.id.tvStatus);
        tvLogOutput = findViewById(R.id.tvLogOutput);

        rgAudioSource = findViewById(R.id.rgAudioSource);
        rbLivestream = findViewById(R.id.rbLivestream);
        rbMic = findViewById(R.id.rbMic);
        swBinaural = findViewById(R.id.swBinaural);
        etServerIp = findViewById(R.id.etServerIp);
        spLangMode = findViewById(R.id.spLangMode);
        btnContinuousTranslate = findViewById(R.id.btnContinuousTranslate);
        btnTranslateChunk = findViewById(R.id.btnTranslateChunk);

        SharedPreferences prefs = getSharedPreferences("htc_scope", MODE_PRIVATE);
        String savedIp = prefs.getString("server_ip", "172.18.71.111");
        etServerIp.setText(savedIp);
        etServerIp.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                prefs.edit().putString("server_ip", s.toString().trim()).apply();
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, LANG_LABELS);
        spLangMode.setAdapter(adapter);
        spLangMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position >= 0 && position < LANG_CODES.length) {
                    currentLangMode = LANG_CODES[position];
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        rgAudioSource.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbLivestream) {
                requestLivestreamAudioCapture();
            } else {
                startMicCapture();
            }
        });

        btnContinuousTranslate.setOnClickListener(v -> toggleContinuousTranslation());
        btnTranslateChunk.setOnClickListener(v -> executeSingleTranslation());
    }

    private void setupAudioEngine() {
        audioEngine = new BinauralAudioEngine();
        audioEngine.setAudioListener((pcm, length) -> {
            oscilloscopeView.updateAudio(pcm, length);

            float max = 0;
            for (int i = 0; i < length; i++) {
                float v = Math.abs(pcm[i] / 32768f);
                if (v > max) max = v;
            }
            final float vpp = max * 2f;
            mainHandler.post(() -> tvAudioMetrics.setText(String.format(Locale.US, "CH1 [AUDIO]: %.2f Vpp | 16kHz", vpp)));
        });
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.BLUETOOTH_CONNECT
            }, REQUEST_PERMISSIONS);
        } else {
            // Permission already granted: prompt for internal livestream audio or mic
            if (rbLivestream.isChecked()) {
                requestLivestreamAudioCapture();
            } else {
                startMicCapture();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (rbLivestream.isChecked()) {
                requestLivestreamAudioCapture();
            } else {
                startMicCapture();
            }
        } else {
            Toast.makeText(this, "Audio permission required!", Toast.LENGTH_LONG).show();
        }
    }

    private void requestLivestreamAudioCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && projectionManager != null) {
            tvStatus.setText("Requesting internal system audio capture...");
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
        } else {
            startMicCapture();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                // Start Foreground Service (Required on Android 14)
                Intent serviceIntent = new Intent(this, CaptureService.class);
                ContextCompat.startForegroundService(this, serviceIntent);

                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                audioEngine.startPlaybackCapture(mediaProjection);
                tvStatus.setText("Internal livestream audio active! Oscilloscope running.");
                Toast.makeText(this, "Livestream audio connected!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Audio capture cancelled. Switched to microphone.", Toast.LENGTH_SHORT).show();
                rbMic.setChecked(true);
                startMicCapture();
            }
        }
    }

    private void startMicCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            audioEngine.startMicRecording();
            tvStatus.setText("HTC Microphone active! Oscilloscope running.");
        }
    }

    private void toggleContinuousTranslation() {
        if (!isContinuousActive) {
            startContinuousTranslation();
        } else {
            stopContinuousTranslation();
        }
    }

    private void startContinuousTranslation() {
        isContinuousActive = true;
        btnContinuousTranslate.setText("⏹ TRANSLATING LIVE... (STOP)");
        btnContinuousTranslate.setBackgroundColor(0xFFB00020);
        tvStatus.setText("🔴 Continuous live translation active – listening...");

        continuousThread = new Thread(() -> {
            try {
                // Initial 3.5s buffer fill
                Thread.sleep(3500);
            } catch (InterruptedException ignored) {}

            while (isContinuousActive) {
                long cycleStart = System.currentTimeMillis();

                // 4 seconds audio snapshot from continuous ring buffer
                byte[] wavBytes = audioEngine.getLastWavBytes((int) (BinauralAudioEngine.SAMPLE_RATE * 4.0));
                if (wavBytes != null && wavBytes.length > 2000 && isContinuousActive) {
                    processAudioChunk(wavBytes, true);
                }

                long elapsed = System.currentTimeMillis() - cycleStart;
                long sleepTime = Math.max(500, 3800 - elapsed);
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ignored) {}
            }
        });
        continuousThread.start();
    }

    private void stopContinuousTranslation() {
        isContinuousActive = false;
        btnContinuousTranslate.setText("▶ START CONTINUOUS TRANSLATION");
        btnContinuousTranslate.setBackgroundColor(0xFF00E5FF);
        tvStatus.setText("Translation stopped. Ready.");
    }

    private void executeSingleTranslation() {
        tvStatus.setText("Analyzing 4s audio chunk...");
        btnTranslateChunk.setEnabled(false);

        new Thread(() -> {
            byte[] wavBytes = audioEngine.getLastWavBytes((int) (BinauralAudioEngine.SAMPLE_RATE * 3.5));
            if (wavBytes != null && wavBytes.length > 1000) {
                processAudioChunk(wavBytes, false);
            }
            mainHandler.post(() -> btnTranslateChunk.setEnabled(true));
        }).start();
    }

    private void processAudioChunk(byte[] wavBytes, boolean isContinuous) {
        String sourceLang = "AUTO";
        String targetLang = "DE";
        String original = "";
        String translated = "";

        String serverIp = "172.18.71.111";
        if (etServerIp != null) {
            String typed = etServerIp.getText().toString().trim();
            if (!typed.isEmpty()) serverIp = typed;
        }

        // Clean user input: strip protocol and slashes
        serverIp = serverIp.replace("http://", "").replace("https://", "").replace("/", "").trim();

        String bridgeUrl;
        if (serverIp.contains(":")) {
            bridgeUrl = "http://" + serverIp + "/translate";
        } else {
            bridgeUrl = "http://" + serverIp + ":11436/translate";
        }

        try {
            URL url = new URL(bridgeUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(12000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "audio/wav");
            conn.setRequestProperty("X-Lang-Mode", currentLangMode);

            OutputStream os = conn.getOutputStream();
            os.write(wavBytes);
            os.flush();
            os.close();

            if (conn.getResponseCode() == 200) {
                InputStream is = conn.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int r;
                while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
                String respStr = baos.toString("UTF-8");

                JSONObject json = new JSONObject(respStr);
                if (json.optBoolean("success", false)) {
                    sourceLang = json.optString("source", "AUTO");
                    targetLang = json.optString("target", "DE");
                    original = json.optString("original", "");
                    translated = json.optString("translated", "");
                }
            }
        } catch (Exception e) {
            if (!isContinuous) {
                original = "(Connection error: " + e.getClass().getSimpleName() + ")";
                translated = "Cannot reach " + serverIp + ":11436! Check PC IP & Wi-Fi.";
            }
        }

        final String finalSource = sourceLang;
        final String finalTarget = targetLang;
        final String finalOrig = original;
        final String finalTrans = translated;

        mainHandler.post(() -> {
            if (!finalTrans.isEmpty() && !finalOrig.equalsIgnoreCase(lastOriginal)) {
                lastOriginal = finalOrig;
                String log = "[" + finalSource + " ➔ " + finalTarget + "]\n"
                        + "Original:   " + finalOrig + "\n"
                        + "Translated: " + finalTrans + "\n"
                        + "------------------------------------\n";
                tvLogOutput.setText(log + tvLogOutput.getText());
                tvStatus.setText("🔊 Playing translation (" + finalTarget + ") in right ear...");

                Locale ttsLocale = Locale.GERMAN;
                if ("EN".equalsIgnoreCase(finalTarget)) {
                    ttsLocale = Locale.ENGLISH;
                } else if ("KO".equalsIgnoreCase(finalTarget)) {
                    ttsLocale = Locale.KOREAN;
                }
                speakBinaural(finalTrans, ttsLocale);
            } else if (isContinuous) {
                tvStatus.setText("🔴 Continuous translation (" + currentLangMode + ") – listening...");
            }
        });
    }

    private void speakBinaural(String text, Locale locale) {
        if (tts != null) {
            int res = tts.setLanguage(locale);
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.GERMAN);
            }
            Bundle params = new Bundle();
            if (swBinaural.isChecked()) {
                // Pan audio to RIGHT earbud: 1.0f = 100% Right
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 1.0f);
            }
            // Use QUEUE_ADD so it mixes non-destructively over ongoing livestream
            tts.speak(text, TextToSpeech.QUEUE_ADD, params, "BINAURAL_TTS");
        }
    }

    private void startRfTelemetryLoop() {
        new Thread(() -> {
            while (!isDestroyed()) {
                float rssi = -8f - random.nextInt(12);
                float lqi = 95f + random.nextInt(5);
                mainHandler.post(() -> {
                    oscilloscopeView.updateRf(rssi, lqi);
                    tvRfMetrics.setText(String.format(Locale.US, "CH2 [RF]: %.0f dBm | %.0f%%", rssi, lqi));
                });
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.GERMAN);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.media.AudioAttributes audioAttrs = new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                tts.setAudioAttributes(audioAttrs);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopContinuousTranslation();
        if (audioEngine != null) audioEngine.stopRecording();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
