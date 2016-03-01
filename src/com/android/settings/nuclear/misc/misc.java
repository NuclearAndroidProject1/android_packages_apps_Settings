/*
 *<!-- Copyright (C) 2012-2014 NuclearAndroidProject
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

package com.android.settings.nuclear.misc;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import com.android.internal.logging.MetricsLogger;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.IWindowManager;
import android.view.Display;
import android.view.Window;
import android.widget.Toast;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import android.preference.SwitchPreference;

import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.internal.util.omni.DeviceUtils;
import com.android.settings.dashboard.DashboardContainerView;
 
 public class misc extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String SCREENSHOT_SOUNDS = "screenshot_sounds";

    private static final String DASHBOARD_COLUMNS = "dashboard_columns";
    private static final String DASHBOARD_SWITCHES = "dashboard_switches";
 	
    private SwitchPreference mScreenshotSounds;
    private ListPreference mDashboardColumns;
	private ListPreference mDashboardSwitches;


  @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.nuclear_misc);
        mContext = getActivity().getApplicationContext();
        PreferenceScreen prefSet = getPreferenceScreen();
        final ContentResolver resolver = getActivity().getContentResolver();

        mScreenshotSounds = (SwitchPreference) findPreference(SCREENSHOT_SOUNDS);

        mDashboardColumns = (ListPreference) findPreference(DASHBOARD_COLUMNS);
        mDashboardColumns.setValue(String.valueOf(Settings.System.getInt(
                getContentResolver(), Settings.System.DASHBOARD_COLUMNS, DashboardContainerView.mDashboardValue)));
        mDashboardColumns.setSummary(mDashboardColumns.getEntry());
        mDashboardColumns.setOnPreferenceChangeListener(this);

        mDashboardSwitches = (ListPreference) findPreference(DASHBOARD_SWITCHES);
        mDashboardSwitches.setValue(String.valueOf(Settings.System.getInt(
                getContentResolver(), Settings.System.DASHBOARD_SWITCHES, 0)));
        mDashboardSwitches.setSummary(mDashboardSwitches.getEntry());
        mDashboardSwitches.setOnPreferenceChangeListener(this);

    }

    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.DONT_TRACK_ME_BRO;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		if (preference == mScreenshotSounds) {
 	      if (mScreenshotSounds.isChecked()) {
                Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.SCREENSHOT_SOUNDS, 2);
            }else{
                Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.SCREENSHOT_SOUNDS, 1);
            }

        
 	    }else {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }
            return false;
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        if (preference == mDashboardColumns) {
            Settings.System.putInt(getContentResolver(), Settings.System.DASHBOARD_COLUMNS,
                    Integer.valueOf((String) value));
            mDashboardColumns.setValue(String.valueOf(value));
            mDashboardColumns.setSummary(mDashboardColumns.getEntry());
            return true;
        }
        if (preference == mDashboardSwitches) {
            Settings.System.putInt(getContentResolver(), Settings.System.DASHBOARD_SWITCHES,
                    Integer.valueOf((String) value));
            mDashboardSwitches.setValue(String.valueOf(value));
            mDashboardSwitches.setSummary(mDashboardSwitches.getEntry());
            return true;
        }
          return true;
     }
}
