/*
 * Copyright (C) 2025 The HavocOOS Project
 *
 * Licensed under the Apache License, Version 2.0
 *
 * Controller for the Havoc OS account header on the Settings homepage.
 * Loads the signed-in Google account's avatar, name, and email.
 * Tapping the avatar opens the account chooser/switcher.
 * Search icon opens Settings search.
 * QR code icon opens the device sharing/QR code screen.
 */

package com.android.settings.homepage;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.accounts.AccountDashboardFragment;
import com.android.settings.search.SearchFeatureProvider;
import com.android.settingslib.widget.LayoutPreference;

/**
 * Manages the account header card on the Settings homepage.
 * Dynamically loads the primary Google account's profile photo,
 * display name, and email address. Falls back to default avatar
 * and "Set up your account" message if no Google account is found.
 */
public class HavocAccountHeaderController {

    private final Context mContext;
    private final LayoutPreference mHeaderPreference;

    public HavocAccountHeaderController(Context context, LayoutPreference headerPreference) {
        mContext = context;
        mHeaderPreference = headerPreference;
    }

    /**
     * Binds account data, sets up click listeners for avatar (account switch),
     * search icon, and QR code icon.
     */
    public void init() {
        if (mHeaderPreference == null) return;

        final ImageView avatarView = mHeaderPreference.findViewById(R.id.havoc_account_avatar);
        final TextView nameView = mHeaderPreference.findViewById(R.id.havoc_account_name);
        final TextView emailView = mHeaderPreference.findViewById(R.id.havoc_account_email);
        final ImageView searchIcon = mHeaderPreference.findViewById(R.id.havoc_search_icon);
        final ImageView qrIcon = mHeaderPreference.findViewById(R.id.havoc_qr_icon);
        final View searchBar = mHeaderPreference.findViewById(R.id.havoc_search_bar);

        // Load primary Google account
        final AccountManager accountManager = AccountManager.get(mContext);
        final Account[] googleAccounts = accountManager.getAccountsByType("com.google");

        if (googleAccounts != null && googleAccounts.length > 0) {
            final Account primaryAccount = googleAccounts[0];

            // Set email
            if (emailView != null) {
                emailView.setText(primaryAccount.name);
                emailView.setVisibility(View.VISIBLE);
            }

            // Try to get display name from contacts
            if (nameView != null) {
                String displayName = getProfileDisplayName();
                if (displayName != null && !displayName.isEmpty()) {
                    nameView.setText(displayName);
                } else {
                    // Use email username as fallback name
                    String name = primaryAccount.name;
                    int atIndex = name.indexOf('@');
                    if (atIndex > 0) {
                        name = name.substring(0, atIndex);
                        // Capitalize first letter
                        name = name.substring(0, 1).toUpperCase() + name.substring(1);
                    }
                    nameView.setText(name);
                }
            }

            // Load profile photo from contacts
            if (avatarView != null) {
                Drawable profilePhoto = getProfilePhoto();
                if (profilePhoto != null) {
                    avatarView.setImageDrawable(profilePhoto);
                    avatarView.setBackground(null);
                    avatarView.setClipToOutline(true);
                }
            }
        } else {
            // No Google account — show defaults
            if (nameView != null) {
                nameView.setText(R.string.havoc_setup_account);
            }
            if (emailView != null) {
                emailView.setText(R.string.havoc_account_tap_to_switch);
                emailView.setVisibility(View.VISIBLE);
            }
        }

        // Avatar click → open account settings / account switcher
        if (avatarView != null) {
            avatarView.setOnClickListener(v -> {
                Intent intent = new Intent(android.provider.Settings.ACTION_SYNC_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    mContext.startActivity(intent);
                } catch (Exception e) {
                    // Fallback: open Accounts settings fragment
                    Intent fallback = new Intent(android.provider.Settings.ACTION_SETTINGS);
                    mContext.startActivity(fallback);
                }
            });
        }

        // Search icon click → open Settings search
        if (searchIcon != null) {
            searchIcon.setOnClickListener(v -> {
                try {
                    Intent searchIntent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                    searchIntent.setAction("com.android.settings.action.SETTINGS_SEARCH");
                    mContext.startActivity(searchIntent);
                } catch (Exception e) {
                    // Ignore
                }
            });
        }

        // Search bar click → also opens search
        if (searchBar != null) {
            searchBar.setOnClickListener(v -> {
                if (searchIcon != null) searchIcon.performClick();
            });
        }

        // QR icon click → open device sharing QR code
        if (qrIcon != null) {
            qrIcon.setOnClickListener(v -> {
                try {
                    // Try to open NearbyShare / Quick Share QR
                    Intent qrIntent = new Intent("com.google.android.gms.nearby.sharing.SHOW_QR_CODE");
                    qrIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(qrIntent);
                } catch (Exception e) {
                    // Fallback: open Connected Devices
                    try {
                        Intent fallback = new Intent(android.provider.Settings.ACTION_SETTINGS);
                        mContext.startActivity(fallback);
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    /**
     * Gets the profile display name from the device owner's contact profile.
     */
    private String getProfileDisplayName() {
        try {
            android.database.Cursor cursor = mContext.getContentResolver().query(
                    ContactsContract.Profile.CONTENT_URI,
                    new String[]{ContactsContract.Profile.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        return cursor.getString(0);
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            // Permission not granted or profile not available
        }
        return null;
    }

    /**
     * Gets the profile photo from the device owner's contact profile.
     */
    private Drawable getProfilePhoto() {
        try {
            Uri profilePhotoUri = Uri.withAppendedPath(
                    ContactsContract.Profile.CONTENT_URI,
                    ContactsContract.Contacts.Photo.DISPLAY_PHOTO);
            java.io.InputStream inputStream =
                    mContext.getContentResolver().openInputStream(profilePhotoUri);
            if (inputStream != null) {
                Drawable photo = Drawable.createFromStream(inputStream, "profile_photo");
                inputStream.close();
                return photo;
            }
        } catch (Exception e) {
            // No profile photo available
        }
        return null;
    }
}
