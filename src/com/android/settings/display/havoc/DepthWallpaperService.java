/*
 * Copyright (C) 2025 The HavocOOS Project
 * SPDX-License-Identifier: Apache-2.0
 *
 * Havoc OS Depth Wallpaper Service
 * Renders two wallpaper layers (foreground + background) with gyroscope-driven
 * parallax effect for a 3D depth illusion.
 */

package com.android.settings.display.havoc;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.File;

/**
 * Live wallpaper service that renders two layers with parallax depth effect.
 *
 * Layer system:
 * - Background Layer: The main wallpaper image (sky, scenery, distant objects)
 * - Foreground Layer: A transparent PNG cutout (subject like a flower, mountain ridge)
 *
 * The foreground layer moves at a different rate than the background when the
 * device is tilted, creating a 3D parallax depth illusion.
 */
public class DepthWallpaperService extends WallpaperService {

    private static final String TAG = "DepthWallpaperService";
    private static final String PREFS_NAME = "havoc_depth_wallpaper";
    private static final String KEY_BG_PATH = "background_path";
    private static final String KEY_FG_PATH = "foreground_path";

    @Override
    public Engine onCreateEngine() {
        return new DepthEngine();
    }

    private class DepthEngine extends Engine implements SensorEventListener {

        private SensorManager mSensorManager;
        private Sensor mGyroscope;
        private Sensor mAccelerometer;

        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        private Bitmap mBackgroundBitmap;
        private Bitmap mForegroundBitmap;
        private Bitmap mScaledBackground;
        private Bitmap mScaledForeground;

        private float mOffsetX = 0f;
        private float mOffsetY = 0f;
        private float mTargetOffsetX = 0f;
        private float mTargetOffsetY = 0f;

        private int mWidth;
        private int mHeight;
        private float mIntensity = 0.65f; // 0.0 to 1.0
        private boolean mDepthEnabled = false;
        private boolean mVisible = false;

        // Smoothing factor for parallax movement
        private static final float SMOOTHING = 0.08f;
        // Max pixel offset for the foreground layer
        private static final float MAX_OFFSET_PX = 40f;

        private final Runnable mDrawRunnable = this::drawFrame;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

            loadSettings();
            loadWallpaperLayers();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            scaleLayersToFit();
            drawFrame();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;
            if (visible) {
                loadSettings();
                registerSensors();
                drawFrame();
            } else {
                unregisterSensors();
                mHandler.removeCallbacks(mDrawRunnable);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            unregisterSensors();
            mHandler.removeCallbacks(mDrawRunnable);
            recycleBitmaps();
        }

        private void loadSettings() {
            Context ctx = getApplicationContext();
            mDepthEnabled = android.provider.Settings.System.getInt(
                    ctx.getContentResolver(), "havoc_depth_effect_enabled", 0) == 1;
            int intensityInt = android.provider.Settings.System.getInt(
                    ctx.getContentResolver(), "havoc_depth_parallax_intensity", 65);
            mIntensity = intensityInt / 100f;
        }

        private void loadWallpaperLayers() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String bgPath = prefs.getString(KEY_BG_PATH, null);
            String fgPath = prefs.getString(KEY_FG_PATH, null);

            if (bgPath != null && new File(bgPath).exists()) {
                mBackgroundBitmap = BitmapFactory.decodeFile(bgPath);
            }
            if (fgPath != null && new File(fgPath).exists()) {
                mForegroundBitmap = BitmapFactory.decodeFile(fgPath);
            }
        }

        private void scaleLayersToFit() {
            if (mWidth == 0 || mHeight == 0) return;

            // Scale background to fill screen with slight overscan for parallax
            if (mBackgroundBitmap != null) {
                float overscan = 1.05f; // 5% overscan for subtle BG movement
                int scaledW = (int) (mWidth * overscan);
                int scaledH = (int) (mHeight * overscan);
                mScaledBackground = Bitmap.createScaledBitmap(
                        mBackgroundBitmap, scaledW, scaledH, true);
            }

            // Scale foreground to fit screen
            if (mForegroundBitmap != null) {
                float overscan = 1.1f; // 10% overscan for stronger FG movement
                int scaledW = (int) (mWidth * overscan);
                int scaledH = (int) (mHeight * overscan);
                mScaledForeground = Bitmap.createScaledBitmap(
                        mForegroundBitmap, scaledW, scaledH, true);
            }
        }

        private void drawFrame() {
            if (!mVisible) return;

            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;

            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;

                // Clear canvas
                canvas.drawColor(0xFF000000);

                if (!mDepthEnabled || mScaledForeground == null) {
                    // No depth: just draw background normally
                    if (mScaledBackground != null) {
                        float bgX = (mWidth - mScaledBackground.getWidth()) / 2f;
                        float bgY = (mHeight - mScaledBackground.getHeight()) / 2f;
                        canvas.drawBitmap(mScaledBackground, bgX, bgY, mPaint);
                    }
                } else {
                    // Smooth the offset
                    mOffsetX += (mTargetOffsetX - mOffsetX) * SMOOTHING;
                    mOffsetY += (mTargetOffsetY - mOffsetY) * SMOOTHING;

                    float maxOffset = MAX_OFFSET_PX * mIntensity;

                    // Draw background layer (moves slightly in SAME direction as tilt -> feels distant)
                    if (mScaledBackground != null) {
                        float bgOffX = mOffsetX * 0.3f * maxOffset;
                        float bgOffY = mOffsetY * 0.3f * maxOffset;
                        float bgX = (mWidth - mScaledBackground.getWidth()) / 2f + bgOffX;
                        float bgY = (mHeight - mScaledBackground.getHeight()) / 2f + bgOffY;
                        canvas.drawBitmap(mScaledBackground, bgX, bgY, mPaint);
                    }

                    // Draw foreground layer (moves in OPPOSITE direction -> feels close)
                    if (mScaledForeground != null) {
                        float fgOffX = -mOffsetX * maxOffset;
                        float fgOffY = -mOffsetY * maxOffset;
                        float fgX = (mWidth - mScaledForeground.getWidth()) / 2f + fgOffX;
                        float fgY = (mHeight - mScaledForeground.getHeight()) / 2f + fgOffY;
                        canvas.drawBitmap(mScaledForeground, fgX, fgY, mPaint);
                    }

                    // Schedule next frame for smooth animation
                    mHandler.removeCallbacks(mDrawRunnable);
                    mHandler.postDelayed(mDrawRunnable, 16); // ~60fps
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }

        // --- Sensor handling ---

        private void registerSensors() {
            if (!mDepthEnabled) return;

            if (mGyroscope != null) {
                mSensorManager.registerListener(this, mGyroscope,
                        SensorManager.SENSOR_DELAY_GAME);
            } else if (mAccelerometer != null) {
                // Fallback to accelerometer if no gyroscope
                mSensorManager.registerListener(this, mAccelerometer,
                        SensorManager.SENSOR_DELAY_GAME);
            }
        }

        private void unregisterSensors() {
            mSensorManager.unregisterListener(this);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                // Gyroscope gives angular velocity — integrate for position
                mTargetOffsetX = clamp(mTargetOffsetX + event.values[1] * 0.02f, -1f, 1f);
                mTargetOffsetY = clamp(mTargetOffsetY + event.values[0] * 0.02f, -1f, 1f);

                // Slowly decay back to center
                mTargetOffsetX *= 0.98f;
                mTargetOffsetY *= 0.98f;
            } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                // Accelerometer gives tilt angle directly
                mTargetOffsetX = clamp(event.values[0] / 9.8f, -1f, 1f);
                mTargetOffsetY = clamp(event.values[1] / 9.8f - 1f, -1f, 1f);
            }

            if (mVisible) {
                drawFrame();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // No-op
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private void recycleBitmaps() {
            if (mScaledBackground != null && !mScaledBackground.isRecycled()) {
                mScaledBackground.recycle();
            }
            if (mScaledForeground != null && !mScaledForeground.isRecycled()) {
                mScaledForeground.recycle();
            }
            if (mBackgroundBitmap != null && !mBackgroundBitmap.isRecycled()) {
                mBackgroundBitmap.recycle();
            }
            if (mForegroundBitmap != null && !mForegroundBitmap.isRecycled()) {
                mForegroundBitmap.recycle();
            }
        }
    }
}
