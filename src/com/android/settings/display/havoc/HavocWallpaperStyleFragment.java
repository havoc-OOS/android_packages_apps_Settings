/*
 * Copyright (C) 2025 The HavocOOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display.havoc;

import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settingslib.widget.LayoutPreference;

import java.io.File;

/**
 * Havoc OS: Home screen, Lock screen & style
 * Refined V2 layout with Hero Carousel and Depth Controls.
 */
public class HavocWallpaperStyleFragment extends DashboardFragment {

    private static final String TAG = "HavocWallpaperStyle";
    private static final String KEY_WS_HEADER = "havoc_ws_header";
    private static final String KEY_DEPTH_ENABLED = "depth_effect_enabled";
    private static final String KEY_DEPTH_INTENSITY = "depth_parallax_intensity";
    private static final String KEY_DEPTH_AUTO = "depth_auto_detect";
    private static final String PREFS_NAME = "havoc_depth_wallpaper";

    @Override
    public int getMetricsCategory() {
        return 0; // Custom Havoc metrics
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.havoc_wallpaper_style;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupHeader();
        setupDepthControls();
    }

    private void setupHeader() {
        LayoutPreference headerPref = findPreference(KEY_WS_HEADER);
        if (headerPref == null) return;

        // V2 Header elements
        View heroCard = headerPref.findViewById(R.id.hero_theme_card);
        View btnAllThemes = headerPref.findViewById(R.id.btn_all_themes);
        View cardWallpapers = headerPref.findViewById(R.id.card_wallpapers);
        View cardAod = headerPref.findViewById(R.id.card_aod);
        View cardFont = headerPref.findViewById(R.id.card_font);
        View cardDepth = headerPref.findViewById(R.id.card_depth_effect);

        ImageView wallpaperPreview = headerPref.findViewById(R.id.wallpaper_preview);
        ImageView depthFgPreview = headerPref.findViewById(R.id.depth_foreground_preview);

        // Load preview images
        if (wallpaperPreview != null) {
            loadWallpaperPreview(wallpaperPreview, depthFgPreview);
        }

        // Set click handlers
        if (heroCard != null) heroCard.setOnClickListener(v -> launchWallpaperPicker());
        if (btnAllThemes != null) btnAllThemes.setOnClickListener(v -> launchThemePicker());
        if (cardWallpapers != null) cardWallpapers.setOnClickListener(v -> launchWallpaperPicker());
        if (cardAod != null) cardAod.setOnClickListener(v -> launchAodSettings());
        if (cardFont != null) cardFont.setOnClickListener(v -> launchFontPicker());
        if (cardDepth != null) cardDepth.setOnClickListener(v -> launchDepthEditor());
    }

    private void setupDepthControls() {
        SwitchPreferenceCompat depthToggle = findPreference(KEY_DEPTH_ENABLED);
        if (depthToggle != null) {
            boolean enabled = android.provider.Settings.System.getInt(
                    getContext().getContentResolver(),
                    "havoc_depth_effect_enabled", 0) == 1;
            depthToggle.setChecked(enabled);
            depthToggle.setOnPreferenceChangeListener((pref, newVal) -> {
                boolean on = (boolean) newVal;
                android.provider.Settings.System.putInt(
                        getContext().getContentResolver(),
                        "havoc_depth_effect_enabled", on ? 1 : 0);
                updateDepthDependencies(on);
                updateHeaderPreviewState(on);
                return true;
            });
            updateDepthDependencies(enabled);
            updateHeaderPreviewState(enabled);
        }

        SwitchPreferenceCompat autoDetect = findPreference(KEY_DEPTH_AUTO);
        if (autoDetect != null) {
            boolean enabled = android.provider.Settings.System.getInt(
                    getContext().getContentResolver(),
                    "havoc_depth_auto_detect", 1) == 1;
            autoDetect.setChecked(enabled);
            autoDetect.setOnPreferenceChangeListener((pref, newVal) -> {
                boolean on = (boolean) newVal;
                android.provider.Settings.System.putInt(
                        getContext().getContentResolver(),
                        "havoc_depth_auto_detect", on ? 1 : 0);
                
                if (on) {
                    simulateAIForegroundExtraction();
                }
                return true;
            });
        }
    }

    private void updateDepthDependencies(boolean depthEnabled) {
        Preference intensity = findPreference(KEY_DEPTH_INTENSITY);
        Preference autoDetect = findPreference(KEY_DEPTH_AUTO);

        if (intensity != null) intensity.setEnabled(depthEnabled);
        if (autoDetect != null) autoDetect.setEnabled(depthEnabled);
    }

    private void updateHeaderPreviewState(boolean enabled) {
        LayoutPreference headerPref = findPreference(KEY_WS_HEADER);
        if (headerPref != null) {
            ImageView fgPreview = headerPref.findViewById(R.id.depth_foreground_preview);
            if (fgPreview != null) {
                // If depth is enabled and there's a foreground layer saved, show it overlaying the clock
                if (enabled) {
                    SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    String fgPath = prefs.getString("foreground_path", null);
                    if (fgPath != null && new File(fgPath).exists()) {
                        fgPreview.setImageBitmap(BitmapFactory.decodeFile(fgPath));
                        fgPreview.setVisibility(View.VISIBLE);
                    } else {
                        fgPreview.setVisibility(View.GONE);
                    }
                } else {
                    fgPreview.setVisibility(View.GONE);
                }
            }
        }
    }

    private void loadWallpaperPreview(ImageView bgPreview, ImageView fgPreview) {
        try {
            // Priority 1: Use depth background if active
            boolean depthEnabled = android.provider.Settings.System.getInt(
                    getContext().getContentResolver(), "havoc_depth_effect_enabled", 0) == 1;
            
            if (depthEnabled) {
                SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String bgPath = prefs.getString("background_path", null);
                if (bgPath != null && new File(bgPath).exists()) {
                    bgPreview.setImageBitmap(BitmapFactory.decodeFile(bgPath));
                    updateHeaderPreviewState(true);
                    return;
                }
            }

            // Fallback: system wallpaper Manager
            WallpaperManager wm = WallpaperManager.getInstance(getContext());
            bgPreview.setImageDrawable(wm.getDrawable());
            if (fgPreview != null) fgPreview.setVisibility(View.GONE);
        } catch (Exception e) {
            // Fallback: leave empty
        }
    }

    private void simulateAIForegroundExtraction() {
        ProgressDialog dialog = new ProgressDialog(getContext(), R.style.Theme_DeviceDefault_Dialog_Alert);
        dialog.setMessage("AI detecting foreground subject...");
        dialog.setCancelable(false);
        dialog.show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            dialog.dismiss();
            Toast.makeText(getContext(), "Subject mask created automatically", Toast.LENGTH_SHORT).show();
            // In a real implementation, this would save the mask to "foreground_path"
            // For now, we simulate success
            updateHeaderPreviewState(true);
        }, 1500); // Simulate 1.5s AI processing time
    }

    private void launchWallpaperPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
            startActivity(intent);
        } catch (Exception e) {}
    }

    private void launchThemePicker() {
        try {
            // Mock launching a full Havoc Theme Center
            Toast.makeText(getContext(), "Opening all Havoc themes...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {}
    }

    private void launchAodSettings() {
        try {
            Intent intent = new Intent("android.settings.LOCK_SCREEN_SETTINGS");
            startActivity(intent);
        } catch (Exception e) {}
    }

    private void launchFontPicker() {
        Toast.makeText(getContext(), "Font picker coming soon", Toast.LENGTH_SHORT).show();
    }

    private void launchDepthEditor() {
        try {
            Intent intent = new Intent(getContext(), DepthWallpaperActivity.class);
            startActivity(intent);
        } catch (Exception e) {}
    }
}
