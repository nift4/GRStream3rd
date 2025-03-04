/*
 * Copyright (C) 2014 The CyanogenMod Project
 * Copyright (C) 2019-2021 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.hearnsoft.gensokyoradio.trd.widgets;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.core.content.ContextCompat;

import net.hearnsoft.gensokyoradio.trd.R;

public class VisualizerView extends View {
    private static final String TAG = VisualizerView.class.getSimpleName();

    private static final int DEFAULT_ALPHA = 140;

    private static final long DURATION_LINK = 800;
    private static final long DURATION_UNLINK = 600;
    private int mSessionId;

    private Paint mPaint;
    private Visualizer mVisualizer;
    private ObjectAnimator mVisualizerColorAnimator;

    private final ValueAnimator[] mValueAnimators = new ValueAnimator[32];
    private final float[] mFFTPoints = new float[128];

    private boolean mVisible = false;
    private boolean mPlaying = false;
    private boolean mPowerSaveMode = false;
    private boolean mDisplaying = false; // the state we're animating to

    private int mColor;

    private final Visualizer.OnDataCaptureListener mVisualizerListener =
            new Visualizer.OnDataCaptureListener() {
                byte rfk, ifk;
                int dbValue;
                float magnitude;

                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes,
                                                  int samplingRate) {
                    // empty
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft,
                                             int samplingRate) {
                    for (int i = 0; i < 32; i++) {
                        mValueAnimators[i].cancel();

                        rfk = fft[i * 2 + 2];
                        ifk = fft[i * 2 + 3];
                        magnitude = rfk * rfk + ifk * ifk;
                        dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;

                        mValueAnimators[i].setFloatValues(
                                mFFTPoints[i * 4 + 1],
                                mFFTPoints[3] - (dbValue * 16f));
                        mValueAnimators[i].start();
                    }
                }
            };

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            try {
                mVisualizer = new Visualizer(0);
            } catch (Exception e) {
                Log.e(TAG, "error initializing visualizer", e);
                return;
            }

            mVisualizer.setEnabled(false);
            mVisualizer.setCaptureSize(66);
            mVisualizer.setDataCaptureListener(mVisualizerListener, Visualizer.getMaxCaptureRate(),
                    false, true);
            mVisualizer.setEnabled(true);
        }
    };

    private final Runnable mAsyncUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            AsyncTask.execute(mUnlinkVisualizer);
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (mVisualizer != null) {
                mVisualizer.setEnabled(false);
                mVisualizer.release();
                mVisualizer = null;
            }
        }
    };

    public VisualizerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public VisualizerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VisualizerView(Context context) {
        this(context, null, 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int size = Math.min(getMeasuredWidth(), getMeasuredHeight());
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float barUnit = w / 32f;
        float barWidth = barUnit * 8f / 9f;
        barUnit = barWidth + (barUnit - barWidth) * 32f / 31f;
        mPaint.setStrokeWidth(barWidth);

        for (int i = 0; i < 32; i++) {
            mFFTPoints[i * 4] = mFFTPoints[i * 4 + 2] = i * barUnit + (barWidth / 2);
            mFFTPoints[i * 4 + 1] = h;
            mFFTPoints[i * 4 + 3] = h;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mVisualizer != null) {
            canvas.drawLines(mFFTPoints, mPaint);
        }
    }

    public void initialize(Context context) {
        Log.d(TAG, "initialize");
        mColor = ContextCompat.getColor(context, R.color.system_accent);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(mColor);

        for (int i = 0; i < 32; i++) {
            final int j = i * 4 + 1;
            mValueAnimators[i] = new ValueAnimator();
            mValueAnimators[i].setDuration(128);
            mValueAnimators[i].addUpdateListener(animation ->
                    mFFTPoints[j] = (float) animation.getAnimatedValue());
        }

        mValueAnimators[31].addUpdateListener(animation -> postInvalidate());
    }

    public void setVisible(boolean visible) {
        if (mVisible != visible) {
            mVisible = visible;
            checkStateChanged();
        }
    }

    public void setPlaying(boolean playing) {
        if (mPlaying != playing) {
            mPlaying = playing;
            checkStateChanged();
        }
    }

    public void setPowerSaveMode(boolean powerSaveMode) {
        if (mPowerSaveMode != powerSaveMode) {
            mPowerSaveMode = powerSaveMode;
            checkStateChanged();
        }
    }

    public void setColor(int color) {
        if (color == Color.TRANSPARENT) {
            color = Color.WHITE;
        }

        color = Color.argb(DEFAULT_ALPHA, Color.red(color), Color.green(color), Color.blue(color));

        if (mColor != color) {
            mColor = color;

            if (mVisualizer != null) {
                if (mVisualizerColorAnimator != null) {
                    mVisualizerColorAnimator.cancel();
                }

                mVisualizerColorAnimator = ObjectAnimator.ofArgb(mPaint, "color",
                        mPaint.getColor(), mColor);
                mVisualizerColorAnimator.setStartDelay(600);
                mVisualizerColorAnimator.setDuration(1200);
                mVisualizerColorAnimator.start();
            } else {
                mPaint.setColor(mColor);
            }
        }
    }

    private void checkStateChanged() {
        if (mVisible && mPlaying && !mPowerSaveMode) {
            if (!mDisplaying) {
                mDisplaying = true;

                AsyncTask.execute(mLinkVisualizer);
                animate()
                        .alpha(1f)
                        .setDuration(DURATION_LINK);
            }
        } else {
            if (mDisplaying) {
                mDisplaying = false;

                final long unlinkDuration = (mVisible ? DURATION_UNLINK : 0);
                animate()
                        .alpha(0f)
                        .withEndAction(mAsyncUnlinkVisualizer)
                        .setDuration(unlinkDuration);
            }
        }
    }
}
