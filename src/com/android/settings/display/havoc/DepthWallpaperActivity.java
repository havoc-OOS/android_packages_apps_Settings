/*
 * Copyright (C) 2025 The HavocOOS Project
 * SPDX-License-Identifier: Apache-2.0
 *
 * Havoc OS Depth Wallpaper Layer Editor
 * Allows users to pick background and foreground layers for the depth wallpaper,
 * preview the parallax effect, and apply the depth wallpaper.
 */

package com.android.settings.display.havoc;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Activity for editing depth wallpaper layers.
 * Users can:
 * 1. Pick a background image (the distant scenery)
 * 2. Pick a foreground image (the subject/cutout with transparency)
 * 3. Preview the combined depth effect
 * 4. Adjust parallax intensity
 * 5. Apply as live wallpaper
 */
public class DepthWallpaperActivity extends Activity {

    private static final String TAG = "DepthWallpaperActivity";
    private static final int REQUEST_PICK_BG = 1001;
    private static final int REQUEST_PICK_FG = 1002;
    private static final String PREFS_NAME = "havoc_depth_wallpaper";

    private ImageView mPreviewBackground;
    private ImageView mPreviewForeground;
    private ImageView mCombinedPreview;
    private SeekBar mIntensitySlider;
    private TextView mIntensityLabel;
    private Button mPickBgButton;
    private Button mPickFgButton;
    private Button mApplyButton;

    private Bitmap mBgBitmap;
    private Bitmap mFgBitmap;
    private String mBgPath;
    private String mFgPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.havoc_depth_editor);

        initViews();
        loadExistingLayers();
        setupListeners();
    }

    private void initViews() {
        mPreviewBackground = findViewById(R.id.preview_background);
        mPreviewForeground = findViewById(R.id.preview_foreground);
        mCombinedPreview = findViewById(R.id.combined_preview);
        mIntensitySlider = findViewById(R.id.intensity_slider);
        mIntensityLabel = findViewById(R.id.intensity_label);
        mPickBgButton = findViewById(R.id.btn_pick_background);
        mPickFgButton = findViewById(R.id.btn_pick_foreground);
        mApplyButton = findViewById(R.id.btn_apply_depth);
    }

    private void loadExistingLayers() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mBgPath = prefs.getString("background_path", null);
        mFgPath = prefs.getString("foreground_path", null);

        if (mBgPath != null && new File(mBgPath).exists()) {
            mBgBitmap = BitmapFactory.decodeFile(mBgPath);
            if (mPreviewBackground != null) {
                mPreviewBackground.setImageBitmap(mBgBitmap);
            }
        }
        if (mFgPath != null && new File(mFgPath).exists()) {
            mFgBitmap = BitmapFactory.decodeFile(mFgPath);
            if (mPreviewForeground != null) {
                mPreviewForeground.setImageBitmap(mFgBitmap);
            }
        }

        updateCombinedPreview();

        // Load intensity
        int intensity = android.provider.Settings.System.getInt(
                getContentResolver(), "havoc_depth_parallax_intensity", 65);
        if (mIntensitySlider != null) {
            mIntensitySlider.setProgress(intensity);
        }
        if (mIntensityLabel != null) {
            mIntensityLabel.setText(intensity + "%");
        }
    }

    private void setupListeners() {
        if (mPickBgButton != null) {
            mPickBgButton.setOnClickListener(v -> pickImage(REQUEST_PICK_BG));
        }

        if (mPickFgButton != null) {
            mPickFgButton.setOnClickListener(v -> pickImage(REQUEST_PICK_FG));
        }

        if (mApplyButton != null) {
            mApplyButton.setOnClickListener(v -> applyDepthWallpaper());
        }

        if (mIntensitySlider != null) {
            mIntensitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (mIntensityLabel != null) {
                        mIntensityLabel.setText(progress + "%");
                    }
                    if (fromUser) {
                        android.provider.Settings.System.putInt(
                                getContentResolver(),
                                "havoc_depth_parallax_intensity", progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    private void pickImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri imageUri = data.getData();

        try {
            InputStream is = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (is != null) is.close();

            if (bitmap == null) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                return;
            }

            if (requestCode == REQUEST_PICK_BG) {
                mBgBitmap = bitmap;
                mBgPath = saveLayerToFile(bitmap, "depth_bg.png");
                if (mPreviewBackground != null) {
                    mPreviewBackground.setImageBitmap(bitmap);
                }
            } else if (requestCode == REQUEST_PICK_FG) {
                mFgBitmap = bitmap;
                mFgPath = saveLayerToFile(bitmap, "depth_fg.png");
                if (mPreviewForeground != null) {
                    mPreviewForeground.setImageBitmap(bitmap);
                }
            }

            updateCombinedPreview();
        } catch (Exception e) {
            Toast.makeText(this, "Error loading image: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String saveLayerToFile(Bitmap bitmap, String filename) {
        try {
            File dir = new File(getFilesDir(), "depth_wallpaper");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, filename);
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            // Save path to preferences
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String key = filename.contains("bg") ? "background_path" : "foreground_path";
            prefs.edit().putString(key, file.getAbsolutePath()).apply();

            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private void updateCombinedPreview() {
        if (mCombinedPreview == null) return;

        if (mBgBitmap == null && mFgBitmap == null) {
            mCombinedPreview.setVisibility(View.GONE);
            return;
        }

        mCombinedPreview.setVisibility(View.VISIBLE);

        // Create combined preview
        int width = mBgBitmap != null ? mBgBitmap.getWidth() :
                    (mFgBitmap != null ? mFgBitmap.getWidth() : 1080);
        int height = mBgBitmap != null ? mBgBitmap.getHeight() :
                     (mFgBitmap != null ? mFgBitmap.getHeight() : 1920);

        Bitmap combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(combined);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // Draw background
        if (mBgBitmap != null) {
            Bitmap scaledBg = Bitmap.createScaledBitmap(mBgBitmap, width, height, true);
            canvas.drawBitmap(scaledBg, 0, 0, paint);
            scaledBg.recycle();
        }

        // Draw foreground on top
        if (mFgBitmap != null) {
            Bitmap scaledFg = Bitmap.createScaledBitmap(mFgBitmap, width, height, true);
            canvas.drawBitmap(scaledFg, 0, 0, paint);
            scaledFg.recycle();
        }

        mCombinedPreview.setImageBitmap(combined);
    }

    private void applyDepthWallpaper() {
        if (mBgPath == null) {
            Toast.makeText(this, "Please select a background layer first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Enable depth effect
            android.provider.Settings.System.putInt(
                    getContentResolver(), "havoc_depth_effect_enabled", 1);

            // Set the depth wallpaper service as active wallpaper
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(getPackageName(),
                            DepthWallpaperService.class.getName()));
            startActivity(intent);

            Toast.makeText(this, "Depth wallpaper applied!",
                    Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Error applying wallpaper: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't recycle bitmaps here as they may be needed by the service
    }
}
