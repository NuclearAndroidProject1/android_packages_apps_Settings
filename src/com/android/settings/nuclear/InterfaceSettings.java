/*
 * Copyright (C) 2013 SlimRoms
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

package com.android.settings.nuclear;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.app.IActivityManager;
import android.app.ActivityManagerNative;
import android.app.ProgressDialog;
import android.content.Context;
import android.app.UiModeManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.view.Display;
import android.view.IWindowManager;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerImpl;
import android.widget.Toast;
import android.util.DisplayMetrics;
import android.util.Log;
import com.android.settings.Utils;
import java.util.Locale;
import android.text.TextUtils;
import android.view.View;

import java.util.List;
import java.util.ArrayList;
import com.android.settings.DropDownPreference;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import android.provider.Settings.SettingNotFoundException;
import com.android.internal.util.omni.DeviceUtils;
import com.android.internal.logging.MetricsLogger;

public class InterfaceSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

	private static final String TAG = "InterfaceSettings";
    
    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final int DIALOG_DENSITY = 0;
    private static final int DIALOG_DENSITY_WARNING = 1;

    private static final String KEY_LCD_DENSITY = "lcd_density";

    private ListPreference mLcdDensityPreference;
    private DropDownPreference mNightModePreference;

    protected Context mContext;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.nuclear_interface_settings);

        mContext = getActivity().getApplicationContext();
        ContentResolver resolver = getActivity().getContentResolver();
        PreferenceScreen prefSet = getPreferenceScreen();

        mLcdDensityPreference = (ListPreference) findPreference(KEY_LCD_DENSITY);
        if (mLcdDensityPreference != null) {
            int defaultDensity = getDefaultDensity();
            int currentDensity = getCurrentDensity();
            if (currentDensity < 10 || currentDensity >= 1000) {
                // Unsupported value, force default
                currentDensity = defaultDensity;
            }

            int factor = defaultDensity >= 480 ? 20 : 20;
            int minimumDensity = defaultDensity - 14 * factor;
            int currentIndex = -1;
            String[] densityEntries = new String[20];
            String[] densityValues = new String[20];
            for (int idx = 0; idx < 20; ++idx) {
                int val = minimumDensity + factor * idx;
                int valueFormatResId = val == defaultDensity
                        ? R.string.lcd_density_default_value_format
                        : R.string.lcd_density_value_format;

                densityEntries[idx] = getString(valueFormatResId, val);
                densityValues[idx] = Integer.toString(val);
                if (currentDensity == val) {
                    currentIndex = idx;
                }
            }
            mLcdDensityPreference.setEntries(densityEntries);
            mLcdDensityPreference.setEntryValues(densityValues);
            if (currentIndex != -1) {
                mLcdDensityPreference.setValueIndex(currentIndex);
            }
            mLcdDensityPreference.setOnPreferenceChangeListener(this);
            updateLcdDensityPreferenceDescription(currentDensity);
        }

        mNightModePreference = (DropDownPreference) findPreference(KEY_NIGHT_MODE);
        final UiModeManager uiManager = (UiModeManager) getSystemService(
                Context.UI_MODE_SERVICE);
        final int currentNightMode = uiManager.getNightMode();
        mNightModePreference.setSelectedValue(String.valueOf(currentNightMode));
        mNightModePreference.setCallback(new DropDownPreference.Callback() {
            @Override
            public boolean onItemSelected(int pos, Object newValue) {
                try {
                    final int value = Integer.parseInt((String) newValue);
                    final UiModeManager uiManager = (UiModeManager) getSystemService(
                            Context.UI_MODE_SERVICE);
                    uiManager.setNightMode(value);
                    return true;
                } catch (NumberFormatException e) {
                    Log.e(TAG, "could not persist night mode setting", e);
                    return false;
                }
            }
        });
        

    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.DONT_TRACK_ME_BRO;
    }

    private int getDefaultDensity() {
        IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.checkService(
                Context.WINDOW_SERVICE));
        try {
            return wm.getInitialDisplayDensity(Display.DEFAULT_DISPLAY);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return DisplayMetrics.DENSITY_DEVICE;
    }

    private int getCurrentDensity() {
        IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.checkService(
                Context.WINDOW_SERVICE));
       try {
            return wm.getBaseDisplayDensity(Display.DEFAULT_DISPLAY);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return DisplayMetrics.DENSITY_DEVICE;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_LCD_DENSITY.equals(key)) {
            try {
                int value = Integer.parseInt((String) objValue);
                writeLcdDensityPreference(preference.getContext(), value);
                updateLcdDensityPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist display density setting", e);
            }
        } 
        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void updateLcdDensityPreferenceDescription(int currentDensity) {
        final int summaryResId = currentDensity == getDefaultDensity()
                ? R.string.lcd_density_default_value_format : R.string.lcd_density_value_format;
        mLcdDensityPreference.setSummary(getString(summaryResId, currentDensity));
    }

    private void writeLcdDensityPreference(final Context context, final int density) {
        final IActivityManager am = ActivityManagerNative.asInterface(
                ServiceManager.checkService("activity"));
        final IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.checkService(
                Context.WINDOW_SERVICE));
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                ProgressDialog dialog = new ProgressDialog(context);
                dialog.setMessage(getResources().getString(R.string.restarting_ui));
                dialog.setCancelable(false);
                dialog.setIndeterminate(true);
                dialog.show();
            }
            @Override
            protected Void doInBackground(Void... params) {
                // Give the user a second to see the dialog
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
                try {
                    wm.setForcedDisplayDensity(Display.DEFAULT_DISPLAY, density);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to set density to " + density, e);
                }
                // Restart the UI
                try {
                    am.restart();
                } catch (RemoteException e) {
                   Log.e(TAG, "Failed to restart");
                }
                return null;
            }
        };
        task.execute();
    }

}