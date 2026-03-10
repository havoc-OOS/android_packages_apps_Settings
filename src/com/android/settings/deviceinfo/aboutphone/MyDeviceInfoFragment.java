/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.deviceinfo.aboutphone;

import static androidx.core.content.ContextCompat.getMainExecutor;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.deviceinfo.BluetoothAddressPreferenceController;
import com.android.settings.deviceinfo.BuildNumberPreferenceController;
import com.android.settings.deviceinfo.DeviceNamePreferenceController;
import com.android.settings.deviceinfo.FccEquipmentIdPreferenceController;
import com.android.settings.deviceinfo.FeedbackPreferenceController;
import com.android.settings.deviceinfo.IpAddressPreferenceController;
import com.android.settings.deviceinfo.ManualPreferenceController;
import com.android.settings.deviceinfo.RegulatoryInfoPreferenceController;
import com.android.settings.deviceinfo.SafetyInfoPreferenceController;
import com.android.settings.deviceinfo.UptimePreferenceController;
import com.android.settings.deviceinfo.WifiMacAddressPreferenceController;
import com.android.settings.deviceinfo.imei.ImeiInfoPreferenceController;
import com.android.settings.deviceinfo.simstatus.EidStatus;
import com.android.settings.deviceinfo.simstatus.SimEidPreferenceController;
import com.android.settings.deviceinfo.simstatus.SimStatusPreferenceController;
import com.android.settings.deviceinfo.simstatus.SlotSimStatus;
import com.android.settings.flags.Flags;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.EntityHeaderController;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.LayoutPreference;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@SearchIndexable
public class MyDeviceInfoFragment extends DashboardFragment
        implements DeviceNamePreferenceController.DeviceNamePreferenceHost {

    private static final String LOG_TAG = "MyDeviceInfoFragment";
    private static final String KEY_EID_INFO = "eid_info";
    private static final String KEY_MY_DEVICE_INFO_HEADER = "my_device_info_header";

    private BuildNumberPreferenceController mBuildNumberPreferenceController;

    private DeviceInfoViewModel mDeviceInfoViewModel;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DEVICEINFO;
    }

    @Override
    public int getHelpResource() {
        return R.string.help_uri_about;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        use(DeviceNamePreferenceController.class).setHost(this /* parent */);
        mBuildNumberPreferenceController = use(BuildNumberPreferenceController.class);
        mBuildNumberPreferenceController.setHost(this /* parent */);
    }

    @Override
    public void onCreate(@Nullable Bundle icicle) {
        super.onCreate(icicle);
        mDeviceInfoViewModel = new ViewModelProvider(getActivity()).get(DeviceInfoViewModel.class);
    }

    @Override
    protected @NonNull Set<String> getPreferenceKeysInHierarchy() {
        Set<String> keys = super.getPreferenceKeysInHierarchy();
        // add async preference key manually
        keys.add(KEY_EID_INFO);
        return keys;
    }

    @Override
    protected void onPreferenceScreenCreatedFromResource(
            @NonNull PreferenceScreen preferenceScreen) {
        if (isCatalystEnabled()) {
            // remove the preference created from resource to avoid duplicated key
            preferenceScreen.removePreferenceRecursively(KEY_EID_INFO);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        initHeader();
    }

    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.my_device_info;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, this /* fragment */, getSettingsLifecycle());
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(
            Context context, MyDeviceInfoFragment fragment, Lifecycle lifecycle) {
        // disable catalyst for settings search (i.e. fragment is null)
        boolean isCatalystEnabled = Flags.catalystMyDeviceInfoPrefScreen() && fragment != null;
        final List<AbstractPreferenceController> controllers = new ArrayList<>();

        final Executor executor = (fragment == null) ? getMainExecutor(context) :
                Executors.newSingleThreadExecutor();
        androidx.lifecycle.Lifecycle lifecycleObject = (fragment == null) ? null :
                fragment.getLifecycle();
        final SlotSimStatus slotSimStatus = new SlotSimStatus(context, executor, lifecycleObject);

        controllers.add(new IpAddressPreferenceController(context, lifecycle));
        controllers.add(new WifiMacAddressPreferenceController(context, lifecycle));
        controllers.add(new BluetoothAddressPreferenceController(context, lifecycle));
        controllers.add(new RegulatoryInfoPreferenceController(context));
        controllers.add(new SafetyInfoPreferenceController(context));
        controllers.add(new ManualPreferenceController(context));
        controllers.add(new FeedbackPreferenceController(fragment, context));
        controllers.add(new FccEquipmentIdPreferenceController(context));
        controllers.add(new UptimePreferenceController(context, lifecycle));

        Consumer<String> imeiInfoList = imeiKey -> {
            if (Flags.catalystMyDeviceInfoPrefScreen()) {
                return;
            }
            ImeiInfoPreferenceController imeiRecord =
                    new ImeiInfoPreferenceController(context, imeiKey);
            imeiRecord.init(fragment, slotSimStatus);
            controllers.add(imeiRecord);
        };

        if (fragment != null) {
            imeiInfoList.accept(ImeiInfoPreferenceController.DEFAULT_KEY);
        }

        for (int slotIndex = 0; slotIndex < slotSimStatus.size(); slotIndex++) {
            SimStatusPreferenceController slotRecord =
                    new SimStatusPreferenceController(context,
                            slotSimStatus.getPreferenceKey(slotIndex));
            slotRecord.init(fragment, slotSimStatus);
            controllers.add(slotRecord);

            if (fragment != null) {
                imeiInfoList.accept(ImeiInfoPreferenceController.DEFAULT_KEY + (1 + slotIndex));
            }
        }

        if (!isCatalystEnabled) {
            EidStatus eidStatus = new EidStatus(slotSimStatus, context, executor);
            SimEidPreferenceController simEid = new SimEidPreferenceController(context,
                    KEY_EID_INFO);
            simEid.init(slotSimStatus, eidStatus);
            controllers.add(simEid);
        }

        if (executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdown();
        }
        return controllers;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mBuildNumberPreferenceController.onActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void initHeader() {
        // Havoc OS: Bind data to the hero card header
        final LayoutPreference headerPreference =
                getPreferenceScreen().findPreference(KEY_MY_DEVICE_INFO_HEADER);
        if (headerPreference == null) {
            return;
        }
        headerPreference.setVisible(true);

        // Device image — load dynamically from device tree overlay
        // Device trees provide their own havoc_device_image drawable via RRO overlay.
        // Falls back to havoc_device_default if no overlay is set.
        final ImageView deviceImageView = headerPreference.findViewById(R.id.havoc_device_icon);
        if (deviceImageView != null) {
            int deviceImageResId = getContext().getResources().getIdentifier(
                    "havoc_device_image", "drawable", getContext().getPackageName());
            if (deviceImageResId != 0) {
                deviceImageView.setImageResource(deviceImageResId);
            } else {
                // Default fallback device silhouette
                deviceImageView.setImageResource(R.drawable.havoc_device_default);
            }
        }

        // Device name
        final String deviceName = Settings.Global.getString(
                getContext().getContentResolver(), Settings.Global.DEVICE_NAME);
        final TextView deviceNameView = headerPreference.findViewById(R.id.havoc_device_name);
        if (deviceNameView != null) {
            deviceNameView.setText(deviceName != null ? deviceName : Build.MODEL);
        }

        // Havoc OS version + codename
        final String havocVersion = SystemProperties.get("ro.havoc.build.version", "Unknown");
        final String havocCodename = SystemProperties.get("ro.havoc.build.codename", "");
        final TextView osVersionView = headerPreference.findViewById(R.id.havoc_os_version);
        if (osVersionView != null) {
            if (!havocCodename.isEmpty()) {
                osVersionView.setText("v" + havocVersion + " | " + havocCodename);
            } else {
                osVersionView.setText("v" + havocVersion);
            }
        }

        // Quick info grid: Android version
        final TextView androidVersionView =
                headerPreference.findViewById(R.id.havoc_grid_android_version);
        if (androidVersionView != null) {
            androidVersionView.setText("Android " + Build.VERSION.RELEASE);
        }

        // Quick info grid: Security patch
        final TextView securityPatchView =
                headerPreference.findViewById(R.id.havoc_grid_security_patch);
        if (securityPatchView != null) {
            securityPatchView.setText(Build.VERSION.SECURITY_PATCH);
        }

        // Quick info grid: Storage
        final TextView storageView = headerPreference.findViewById(R.id.havoc_grid_storage);
        if (storageView != null) {
            final StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
            final long totalBytes = statFs.getTotalBytes();
            final String totalStorage = Formatter.formatShortFileSize(getContext(), totalBytes);
            storageView.setText(totalStorage);
        }

        // Quick info grid: Battery
        final TextView batteryView = headerPreference.findViewById(R.id.havoc_grid_battery);
        if (batteryView != null) {
            final BatteryManager batteryManager =
                    (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
            final int batteryLevel = batteryManager != null
                    ? batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    : -1;
            batteryView.setText(batteryLevel >= 0 ? batteryLevel + "%" : "N/A");
        }
    }

    @Override
    public void showDeviceNameWarningDialog(String deviceName) {
        mDeviceInfoViewModel.setDeviceName(deviceName);
        DeviceNameWarningDialog.show(this);
    }

    public void onSetDeviceNameConfirm(boolean confirm) {
        if (!isCatalystEnabled() || !Flags.catalystAboutPhoneDeviceName()) {
            final DeviceNamePreferenceController controller = use(
                    DeviceNamePreferenceController.class);
            controller.updateDeviceName(confirm);
        } else {
            if (confirm) {
                final String deviceName = mDeviceInfoViewModel.getDeviceName();
                if (deviceName != null) {
                    UtilsKt.updateDeviceName(getActivity(), deviceName);
                }
            }
        }
        mDeviceInfoViewModel.clearDeviceNme();
    }

    @Override
    public @Nullable String getPreferenceScreenBindingKey(@NonNull Context context) {
        return MyDeviceInfoScreen.KEY;
    }

    /**
     * For Search.
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.my_device_info) {

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return buildPreferenceControllers(context, null /* fragment */,
                            null /* lifecycle */);
                }
            };
}
