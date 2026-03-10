/*
 * Copyright (C) 2025 The HavocOOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display.havoc;

import android.content.Context;

import androidx.preference.Preference;

import com.android.settings.core.SliderPreferenceController;

/**
 * Controller for the depth wallpaper parallax intensity slider.
 * Reads/writes from Settings.System "havoc_depth_parallax_intensity" (0-100).
 */
public class DepthParallaxController extends SliderPreferenceController {

    private static final String KEY = "depth_parallax_intensity";
    private static final String SETTING_KEY = "havoc_depth_parallax_intensity";
    private static final int DEFAULT_VALUE = 65;
    private static final int MAX_VALUE = 100;
    private static final int MIN_VALUE = 0;

    public DepthParallaxController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public int getSliderPosition() {
        return android.provider.Settings.System.getInt(
                mContext.getContentResolver(), SETTING_KEY, DEFAULT_VALUE);
    }

    @Override
    public boolean setSliderPosition(int position) {
        return android.provider.Settings.System.putInt(
                mContext.getContentResolver(), SETTING_KEY, position);
    }

    @Override
    public int getMax() {
        return MAX_VALUE;
    }

    @Override
    public int getMin() {
        return MIN_VALUE;
    }
}
