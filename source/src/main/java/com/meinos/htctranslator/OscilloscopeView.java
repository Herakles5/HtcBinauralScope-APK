package com.meinos.htctranslator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class OscilloscopeView extends View {

    private Paint gridPaint;
    private Paint centerPaint;
    private Paint audioGlowPaint;
    private Paint audioTracePaint;
    private Paint rfGlowPaint;
    private Paint rfTracePaint;
    private Paint textPaint;

    private float[] audioSamples = new float[256];
    private float[] rfHistory = new float[100];
    private float currentRssi = -30f;
    private float currentLqi = 95f;

    public OscilloscopeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint = new Paint();
        gridPaint.setColor(Color.parseColor("#0D1B1A"));
        gridPaint.setStrokeWidth(2f);
        gridPaint.setStyle(Paint.Style.STROKE);

        centerPaint = new Paint();
        centerPaint.setColor(Color.parseColor("#122C2A"));
        centerPaint.setStrokeWidth(2.5f);
        centerPaint.setStyle(Paint.Style.STROKE);

        audioGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        audioGlowPaint.setColor(Color.parseColor("#004433"));
        audioGlowPaint.setStrokeWidth(6f);
        audioGlowPaint.setStyle(Paint.Style.STROKE);

        audioTracePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        audioTracePaint.setColor(Color.parseColor("#00E5FF"));
        audioTracePaint.setStrokeWidth(2.5f);
        audioTracePaint.setStyle(Paint.Style.STROKE);

        rfGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rfGlowPaint.setColor(Color.parseColor("#005522"));
        rfGlowPaint.setStrokeWidth(6f);
        rfGlowPaint.setStyle(Paint.Style.STROKE);

        rfTracePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rfTracePaint.setColor(Color.parseColor("#00FF66"));
        rfTracePaint.setStrokeWidth(3f);
        rfTracePaint.setStyle(Paint.Style.STROKE);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#50FA7B"));
        textPaint.setTextSize(26f);

        for (int i = 0; i < rfHistory.length; i++) {
            rfHistory[i] = -30f;
        }
    }

    public void updateAudio(short[] pcm, int length) {
        if (pcm == null || length <= 0) return;
        int step = Math.max(1, length / audioSamples.length);
        for (int i = 0; i < audioSamples.length; i++) {
            int idx = i * step;
            if (idx < length) {
                audioSamples[i] = pcm[idx] / 32768f;
            }
        }
        postInvalidate();
    }

    public void updateRf(float rssi, float lqi) {
        this.currentRssi = rssi;
        this.currentLqi = lqi;
        System.arraycopy(rfHistory, 1, rfHistory, 0, rfHistory.length - 1);
        rfHistory[rfHistory.length - 1] = rssi;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Draw Laboratory Graticule Grid
        int cols = 8;
        int rows = 6;
        for (int i = 1; i < cols; i++) {
            float x = i * (w / (float) cols);
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        for (int j = 1; j < rows; j++) {
            float y = j * (h / (float) rows);
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        // Center reticle
        float cy = h / 2f;
        canvas.drawLine(0, cy, w, cy, centerPaint);

        // --- Channel 2 (Top Half): RF Bluetooth Scope ---
        float rfHeight = h * 0.45f;
        canvas.drawText("CH2: 2.4GHz RF [RSSI: " + (int) currentRssi + " dBm]", 20f, 32f, textPaint);

        Path rfPath = new Path();
        float rfStep = w / (float) (rfHistory.length - 1);
        for (int i = 0; i < rfHistory.length; i++) {
            float x = i * rfStep;
            float norm = (rfHistory[i] - (-90f)) / 90f;
            norm = Math.max(0f, Math.min(1f, norm));
            float y = 45f + (1f - norm) * (rfHeight - 50f);
            if (i == 0) rfPath.moveTo(x, y);
            else rfPath.lineTo(x, y);
        }
        canvas.drawPath(rfPath, rfGlowPaint);
        canvas.drawPath(rfPath, rfTracePaint);

        // --- Channel 1 (Bottom Half): Audio Waveform ---
        float audioBaseY = h * 0.75f;
        float audioMaxH = h * 0.22f;
        textPaint.setColor(Color.parseColor("#00E5FF"));
        canvas.drawText("CH1: AUDIO WAVEFORM", 20f, cy + 32f, textPaint);

        Path audioPath = new Path();
        float aStep = w / (float) audioSamples.length;
        for (int i = 0; i < audioSamples.length; i++) {
            float x = i * aStep;
            float y = audioBaseY - (audioSamples[i] * audioMaxH);
            if (i == 0) audioPath.moveTo(x, y);
            else audioPath.lineTo(x, y);
        }
        canvas.drawPath(audioPath, audioGlowPaint);
        canvas.drawPath(audioPath, audioTracePaint);
    }
}

