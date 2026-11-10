package com.meinos.htctranslator;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class BinauralAudioEngine {

    public interface AudioListener {
        void onAudioChunk(short[] pcm, int length);
    }

    public static final int SAMPLE_RATE = 16000;
    private AudioRecord audioRecord;
    private AudioListener listener;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private Thread recordThread;

    // Circular buffer for last ~10 seconds of audio (16000 samples/sec * 10 = 160000)
    private final short[] recentBuffer = new short[160000];
    private int bufferWriteIndex = 0;
    private final Object bufferLock = new Object();

    public void setAudioListener(AudioListener listener) {
        this.listener = listener;
    }

    public synchronized void startMicRecording() {
        stopRecording();
        int bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 2
            );

            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording();
                isRecording.set(true);
                startWorker(bufferSize);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public synchronized void startPlaybackCapture(MediaProjection projection) {
        stopRecording();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && projection != null) {
            try {
                AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build();

                int bufferSize = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                );

                audioRecord = new AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(config)
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build())
                        .setBufferSizeInBytes(bufferSize * 2)
                        .build();

                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording();
                    isRecording.set(true);
                    startWorker(bufferSize);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startWorker(int bufferSize) {
        recordThread = new Thread(() -> {
            short[] buffer = new short[Math.max(512, bufferSize / 2)];
            while (isRecording.get() && audioRecord != null) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    synchronized (bufferLock) {
                        for (int i = 0; i < read; i++) {
                            recentBuffer[bufferWriteIndex] = buffer[i];
                            bufferWriteIndex = (bufferWriteIndex + 1) % recentBuffer.length;
                        }
                    }
                    if (listener != null) {
                        listener.onAudioChunk(buffer, read);
                    }
                }
            }
        });
        recordThread.start();
    }

    public synchronized void stopRecording() {
        isRecording.set(false);
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }
    }

    /**
     * Extracts the last 3.5 seconds of recorded PCM audio as a standard 16kHz mono WAV byte array.
     */
    public byte[] getLastWavBytes(int numSamples) {
        short[] snapshot = new short[numSamples];
        synchronized (bufferLock) {
            int start = (bufferWriteIndex - numSamples + recentBuffer.length) % recentBuffer.length;
            for (int i = 0; i < numSamples; i++) {
                snapshot[i] = recentBuffer[(start + i) % recentBuffer.length];
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            writeWavHeader(baos, numSamples * 2, SAMPLE_RATE, 1, 16);
            DataOutputStream dos = new DataOutputStream(baos);
            for (short s : snapshot) {
                dos.writeByte(s & 0xff);
                dos.writeByte((s >> 8) & 0xff);
            }
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private void writeWavHeader(ByteArrayOutputStream out, int pcmDataLen, int sampleRate, int channels, int bitsPerSample) throws IOException {
        int byteRate = sampleRate * channels * (bitsPerSample / 8);
        int totalDataLen = pcmDataLen + 36;
        out.write(new byte[]{
                'R', 'I', 'F', 'F',
                (byte) (totalDataLen & 0xff), (byte) ((totalDataLen >> 8) & 0xff), (byte) ((totalDataLen >> 16) & 0xff), (byte) ((totalDataLen >> 24) & 0xff),
                'W', 'A', 'V', 'E',
                'f', 'm', 't', ' ',
                16, 0, 0, 0,
                1, 0, // AudioFormat 1 = PCM
                (byte) channels, 0,
                (byte) (sampleRate & 0xff), (byte) ((sampleRate >> 8) & 0xff), (byte) ((sampleRate >> 16) & 0xff), (byte) ((sampleRate >> 24) & 0xff),
                (byte) (byteRate & 0xff), (byte) ((byteRate >> 8) & 0xff), (byte) ((byteRate >> 16) & 0xff), (byte) ((byteRate >> 24) & 0xff),
                (byte) (channels * (bitsPerSample / 8)), 0,
                (byte) bitsPerSample, 0,
                'd', 'a', 't', 'a',
                (byte) (pcmDataLen & 0xff), (byte) ((pcmDataLen >> 8) & 0xff), (byte) ((pcmDataLen >> 16) & 0xff), (byte) ((pcmDataLen >> 24) & 0xff)
        });
    }

    public void playBinauralStereo(short[] originalPcm, short[] translatedPcm) {
        int maxLen = Math.max(originalPcm.length, translatedPcm.length);
        short[] stereoBuffer = new short[maxLen * 2];

        for (int i = 0; i < maxLen; i++) {
            short left = (i < originalPcm.length) ? originalPcm[i] : 0;
            short right = (i < translatedPcm.length) ? translatedPcm[i] : 0;
            stereoBuffer[i * 2] = left;
            stereoBuffer[i * 2 + 1] = right;
        }

        int bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build())
                .setBufferSizeInBytes(Math.max(bufferSize, stereoBuffer.length * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();

        track.write(stereoBuffer, 0, stereoBuffer.length);
        track.play();
    }
}
