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
import android.widget.LinearLayout;
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

    public static class LanguageItem {
        public final String code;
        public final String name;
        public final String display;

        public LanguageItem(String code, String name, String display) {
            this.code = code;
            this.name = name;
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    private static final LanguageItem[] SOURCE_LANGUAGES = new LanguageItem[]{
            new LanguageItem("auto", "Auto Detect", "🌐 Auto Detect"),
            new LanguageItem("ko", "Korean", "🇰🇷 Korean (한국어)"),
            new LanguageItem("en", "English", "🇬🇧 English"),
            new LanguageItem("de", "German", "🇩🇪 German (Deutsch)"),
            new LanguageItem("ja", "Japanese", "🇯🇵 Japanese (日本語)"),
            new LanguageItem("zh", "Chinese", "🇨🇳 Chinese (中文)"),
            new LanguageItem("es", "Spanish", "🇪🇸 Spanish (Español)"),
            new LanguageItem("fr", "French", "🇫🇷 French (Français)"),
            new LanguageItem("it", "Italian", "🇮🇹 Italian (Italiano)"),
            new LanguageItem("pt", "Portuguese", "🇵🇹 Portuguese (Português)"),
            new LanguageItem("ru", "Russian", "🇷🇺 Russian (Русский)"),
            new LanguageItem("tr", "Turkish", "🇹🇷 Turkish (Türkçe)"),
            new LanguageItem("ar", "Arabic", "🇦🇪 Arabic (العربية)"),
            new LanguageItem("hi", "Hindi", "🇮🇳 Hindi (हिन्दी)"),
            new LanguageItem("vi", "Vietnamese", "🇻🇳 Vietnamese (Tiếng Việt)"),
            new LanguageItem("th", "Thai", "🇹🇭 Thai (ไทย)"),
            new LanguageItem("id", "Indonesian", "🇮🇩 Indonesian (Bahasa)"),
            new LanguageItem("pl", "Polish", "🇵🇱 Polish (Polski)"),
            new LanguageItem("uk", "Ukrainian", "🇺🇦 Ukrainian (Українська)"),
            new LanguageItem("nl", "Dutch", "🇳🇱 Dutch (Nederlands)"),
            new LanguageItem("sv", "Swedish", "🇸🇪 Swedish (Svenska)"),
            new LanguageItem("cs", "Czech", "🇨🇿 Czech (Čeština)"),
            new LanguageItem("el", "Greek", "🇬🇷 Greek (Ελληνικά)"),
            new LanguageItem("he", "Hebrew", "🇮🇱 Hebrew (עברית)"),
            new LanguageItem("ro", "Romanian", "🇷🇴 Romanian (Română)"),
            new LanguageItem("hu", "Hungarian", "🇭🇺 Hungarian (Magyar)"),
            new LanguageItem("da", "Danish", "🇩🇰 Danish (Dansk)"),
            new LanguageItem("fi", "Finnish", "🇫🇮 Finnish (Suomi)"),
            new LanguageItem("no", "Norwegian", "🇳🇴 Norwegian (Norsk)")
    };

    private static final LanguageItem[] TARGET_LANGUAGES = new LanguageItem[]{
            new LanguageItem("de", "German", "🇩🇪 German (Deutsch)"),
            new LanguageItem("en", "English", "🇬🇧 English"),
            new LanguageItem("ko", "Korean", "🇰🇷 Korean (한국어)"),
            new LanguageItem("ja", "Japanese", "🇯🇵 Japanese (日本語)"),
            new LanguageItem("zh", "Chinese", "🇨🇳 Chinese (中文)"),
            new LanguageItem("es", "Spanish", "🇪🇸 Spanish (Español)"),
            new LanguageItem("fr", "French", "🇫🇷 French (Français)"),
            new LanguageItem("it", "Italian", "🇮🇹 Italian (Italiano)"),
            new LanguageItem("pt", "Portuguese", "🇵🇹 Portuguese (Português)"),
            new LanguageItem("ru", "Russian", "🇷🇺 Russian (Русский)"),
            new LanguageItem("tr", "Turkish", "🇹🇷 Turkish (Türkçe)"),
            new LanguageItem("ar", "Arabic", "🇦🇪 Arabic (العربية)"),
            new LanguageItem("hi", "Hindi", "🇮🇳 Hindi (हिन्दी)"),
            new LanguageItem("vi", "Vietnamese", "🇻🇳 Vietnamese (Tiếng Việt)"),
            new LanguageItem("th", "Thai", "🇹🇭 Thai (ไทย)"),
            new LanguageItem("id", "Indonesian", "🇮🇩 Indonesian (Bahasa)"),
            new LanguageItem("pl", "Polish", "🇵🇱 Polish (Polski)"),
            new LanguageItem("uk", "Ukrainian", "🇺🇦 Ukrainian (Українська)"),
            new LanguageItem("nl", "Dutch", "🇳🇱 Dutch (Nederlands)"),
            new LanguageItem("sv", "Swedish", "🇸🇪 Swedish (Svenska)"),
            new LanguageItem("cs", "Czech", "🇨🇿 Czech (Čeština)"),
            new LanguageItem("el", "Greek", "🇬🇷 Greek (Ελληνικά)"),
            new LanguageItem("he", "Hebrew", "🇮🇱 Hebrew (עברית)"),
            new LanguageItem("ro", "Romanian", "🇷🇴 Romanian (Română)"),
            new LanguageItem("hu", "Hungarian", "🇭🇺 Hungarian (Magyar)"),
            new LanguageItem("da", "Danish", "🇩🇰 Danish (Dansk)"),
            new LanguageItem("fi", "Finnish", "🇫🇮 Finnish (Suomi)"),
            new LanguageItem("no", "Norwegian", "🇳🇴 Norwegian (Norsk)")
    };

    private volatile LanguageItem currentSource = SOURCE_LANGUAGES[0];
    private volatile LanguageItem currentTarget = TARGET_LANGUAGES[0];

    private OscilloscopeView oscilloscopeView;
    private TextView tvDeviceStatus, tvAudioMetrics, tvRfMetrics, tvStatus, tvLogOutput;
    private static String getEmbeddedKey() {
        byte[] enc = new byte[]{61, 41, 49, 5, 10, 17, 3, 25, 16, 11, 16, 31, 3, 28, 12, 14, 54, 9, 27, 41, 62, 45, 110, 59, 13, 29, 62, 35, 56, 105, 28, 3, 30, 61, 57, 32, 108, 44, 55, 59, 63, 105, 106, 29, 50, 59, 109, 0, 111, 24, 109, 107, 0, 12, 46, 12};
        byte[] dec = new byte[enc.length];
        for (int i = 0; i < enc.length; i++) {
            dec[i] = (byte) (enc[i] ^ 0x5A);
        }
        return new String(dec, java.nio.charset.StandardCharsets.UTF_8);
    }

    private RadioGroup rgAudioSource;
    private RadioButton rbLivestream, rbMic;
    private RadioGroup rgBackendEngine;
    private RadioButton rbCloudEngine, rbLocalEngine;
    private LinearLayout layoutCloudConfig, layoutLocalConfig;
    private EditText etApiKey;
    private SwitchCompat swBinaural;
    private EditText etServerIp;
    private Spinner spSourceLang, spTargetLang;
    private Button btnSwapLang;
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

        rgBackendEngine = findViewById(R.id.rgBackendEngine);
        rbCloudEngine = findViewById(R.id.rbCloudEngine);
        rbLocalEngine = findViewById(R.id.rbLocalEngine);
        layoutCloudConfig = findViewById(R.id.layoutCloudConfig);
        layoutLocalConfig = findViewById(R.id.layoutLocalConfig);
        etApiKey = findViewById(R.id.etApiKey);

        swBinaural = findViewById(R.id.swBinaural);
        etServerIp = findViewById(R.id.etServerIp);
        spSourceLang = findViewById(R.id.spSourceLang);
        spTargetLang = findViewById(R.id.spTargetLang);
        btnSwapLang = findViewById(R.id.btnSwapLang);
        btnContinuousTranslate = findViewById(R.id.btnContinuousTranslate);
        btnTranslateChunk = findViewById(R.id.btnTranslateChunk);

        SharedPreferences prefs = getSharedPreferences("htc_scope", MODE_PRIVATE);

        String savedKey = prefs.getString("groq_api_key", getEmbeddedKey());
        etApiKey.setText(savedKey);
        etApiKey.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                prefs.edit().putString("groq_api_key", s.toString().trim()).apply();
            }
        });

        boolean isCloud = prefs.getBoolean("use_cloud_backend", true);
        if (isCloud) {
            rbCloudEngine.setChecked(true);
            layoutCloudConfig.setVisibility(android.view.View.VISIBLE);
            layoutLocalConfig.setVisibility(android.view.View.GONE);
        } else {
            rbLocalEngine.setChecked(true);
            layoutCloudConfig.setVisibility(android.view.View.GONE);
            layoutLocalConfig.setVisibility(android.view.View.VISIBLE);
        }

        rgBackendEngine.setOnCheckedChangeListener((group, checkedId) -> {
            boolean cloud = (checkedId == R.id.rbCloudEngine);
            prefs.edit().putBoolean("use_cloud_backend", cloud).apply();
            layoutCloudConfig.setVisibility(cloud ? android.view.View.VISIBLE : android.view.View.GONE);
            layoutLocalConfig.setVisibility(cloud ? android.view.View.GONE : android.view.View.VISIBLE);
            if (cloud) {
                tvStatus.setText("Cloud Mode active: Direct Groq AI (No PC required)");
            } else {
                tvStatus.setText("PC Bridge Mode active: Connecting to local PC");
            }
        });

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

        ArrayAdapter<LanguageItem> sourceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SOURCE_LANGUAGES);
        spSourceLang.setAdapter(sourceAdapter);

        ArrayAdapter<LanguageItem> targetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, TARGET_LANGUAGES);
        spTargetLang.setAdapter(targetAdapter);

        String savedSource = prefs.getString("source_lang_code", "auto");
        for (int i = 0; i < SOURCE_LANGUAGES.length; i++) {
            if (SOURCE_LANGUAGES[i].code.equalsIgnoreCase(savedSource)) {
                spSourceLang.setSelection(i);
                currentSource = SOURCE_LANGUAGES[i];
                break;
            }
        }

        String savedTarget = prefs.getString("target_lang_code", "de");
        for (int i = 0; i < TARGET_LANGUAGES.length; i++) {
            if (TARGET_LANGUAGES[i].code.equalsIgnoreCase(savedTarget)) {
                spTargetLang.setSelection(i);
                currentTarget = TARGET_LANGUAGES[i];
                break;
            }
        }

        spSourceLang.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position >= 0 && position < SOURCE_LANGUAGES.length) {
                    currentSource = SOURCE_LANGUAGES[position];
                    prefs.edit().putString("source_lang_code", currentSource.code).apply();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        spTargetLang.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position >= 0 && position < TARGET_LANGUAGES.length) {
                    currentTarget = TARGET_LANGUAGES[position];
                    prefs.edit().putString("target_lang_code", currentTarget.code).apply();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnSwapLang.setOnClickListener(v -> {
            if ("auto".equalsIgnoreCase(currentSource.code)) {
                Toast.makeText(this, "Cannot swap when Source is Auto Detect", Toast.LENGTH_SHORT).show();
                return;
            }
            String oldSourceCode = currentSource.code;
            String oldTargetCode = currentTarget.code;

            for (int i = 0; i < SOURCE_LANGUAGES.length; i++) {
                if (SOURCE_LANGUAGES[i].code.equalsIgnoreCase(oldTargetCode)) {
                    spSourceLang.setSelection(i);
                    break;
                }
            }
            for (int j = 0; j < TARGET_LANGUAGES.length; j++) {
                if (TARGET_LANGUAGES[j].code.equalsIgnoreCase(oldSourceCode)) {
                    spTargetLang.setSelection(j);
                    break;
                }
            }
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
        String sourceLang = currentSource.code.toUpperCase();
        String targetLang = currentTarget.code.toUpperCase();
        String original = "";
        String translated = "";

        boolean useCloud = (rbCloudEngine != null && rbCloudEngine.isChecked());

        if (useCloud) {
            String apiKey = (etApiKey != null) ? etApiKey.getText().toString().trim() : getEmbeddedKey();
            if (apiKey.isEmpty()) apiKey = getEmbeddedKey();

            GroqCloudClient.TranslationResult res = GroqCloudClient.processAudio(
                    wavBytes, apiKey,
                    currentSource.code, currentTarget.code,
                    currentSource.name, currentTarget.name
            );
            if (res.success) {
                sourceLang = res.sourceLang;
                targetLang = res.targetLang;
                original = res.original;
                translated = res.translated;
            } else {
                if (!isContinuous && res.error != null) {
                    original = "(Cloud error)";
                    translated = res.error;
                }
            }
        } else {
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
                conn.setRequestProperty("X-Source-Lang", currentSource.code);
                conn.setRequestProperty("X-Target-Lang", currentTarget.code);
                conn.setRequestProperty("X-Lang-Mode", currentSource.code.toUpperCase() + "_" + currentTarget.code.toUpperCase());

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
                        sourceLang = json.optString("source", currentSource.code.toUpperCase());
                        targetLang = json.optString("target", currentTarget.code.toUpperCase());
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

                if (!finalOrig.startsWith("(")) {
                    speakBinaural(finalTrans, finalTarget);
                } else {
                    tvStatus.setText("⚠️ " + finalOrig + ": " + finalTrans);
                }
            } else if (isContinuous) {
                tvStatus.setText("🔴 Continuous translation (" + currentSource.code.toUpperCase() + " ➔ " + currentTarget.code.toUpperCase() + ") – listening...");
            }
        });
    }

    private Locale getTtsLocale(String code) {
        switch (code.toLowerCase()) {
            case "de": return Locale.GERMAN;
            case "en": return Locale.ENGLISH;
            case "ko": return Locale.KOREAN;
            case "ja": return Locale.JAPANESE;
            case "zh": return Locale.CHINESE;
            case "fr": return Locale.FRENCH;
            case "it": return Locale.ITALIAN;
            case "es": return new Locale("es");
            case "pt": return new Locale("pt");
            case "ru": return new Locale("ru");
            case "tr": return new Locale("tr");
            case "ar": return new Locale("ar");
            case "hi": return new Locale("hi");
            case "vi": return new Locale("vi");
            case "th": return new Locale("th");
            case "id": return new Locale("id");
            case "pl": return new Locale("pl");
            case "uk": return new Locale("uk");
            case "nl": return new Locale("nl");
            case "sv": return new Locale("sv");
            case "cs": return new Locale("cs");
            case "el": return new Locale("el");
            case "he": return new Locale("he");
            case "ro": return new Locale("ro");
            case "hu": return new Locale("hu");
            case "da": return new Locale("da");
            case "fi": return new Locale("fi");
            case "no": return new Locale("no");
            default: return Locale.getDefault();
        }
    }

    private void speakBinaural(String text, String targetCode) {
        if (tts != null) {
            Locale locale = getTtsLocale(targetCode);
            int res = tts.setLanguage(locale);
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                res = tts.setLanguage(new Locale(locale.getLanguage()));
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.GERMAN);
                }
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
