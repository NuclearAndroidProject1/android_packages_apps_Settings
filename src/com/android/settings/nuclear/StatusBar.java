package com.android.settings.nuclear;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.app.IActivityManager;
import android.app.ActivityManagerNative;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
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
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import android.provider.Settings.SettingNotFoundException;
import com.android.internal.logging.MetricsLogger;

public class StatusBar extends SettingsPreferenceFragment implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "StatusBar";
    
	private static final String STATUS_BAR_BRIGHTNESS_CONTROL = "status_bar_brightness_control";
    private static final String CUSTOM_HEADER_IMAGE = "status_bar_custom_header";
    private static final String DAYLIGHT_HEADER_PACK = "daylight_header_pack";
    private static final String DEFAULT_HEADER_PACKAGE = "com.android.systemui";
    private static final String PRE_QUICK_PULLDOWN = "quick_pulldown";

    private SwitchPreference mStatusBarBrightnessControl;
    private ListPreference mDaylightHeaderPack;
    private CheckBoxPreference mCustomHeaderImage;
    private ListPreference mQuickPulldown;

	protected Context mContext;

	@Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.nuclear_statusbar);

        mStatusBarBrightnessControl = (SwitchPreference) findPreference(STATUS_BAR_BRIGHTNESS_CONTROL);
        mStatusBarBrightnessControl.setOnPreferenceChangeListener(this);
        int statusBarBrightnessControl = Settings.System.getInt(getContentResolver(),
                STATUS_BAR_BRIGHTNESS_CONTROL, 0);
        mStatusBarBrightnessControl.setChecked(statusBarBrightnessControl != 0);
        try {
            if (Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                mStatusBarBrightnessControl.setEnabled(false);
                mStatusBarBrightnessControl.setSummary(R.string.status_bar_brightness_control_info);
            }
        } catch (SettingNotFoundException e) {
        }

        mContext = getActivity().getApplicationContext();
        ContentResolver resolver = getActivity().getContentResolver();
        PreferenceScreen prefSet = getPreferenceScreen();

		mQuickPulldown = (ListPreference) findPreference(PRE_QUICK_PULLDOWN);
        if (!Utils.isPhone(getActivity())) {
            prefSet.removePreference(mQuickPulldown);
        } else {
            // Quick Pulldown
            mQuickPulldown.setOnPreferenceChangeListener(this);
            int statusQuickPulldown = Settings.System.getInt(getContentResolver(),
                    Settings.System.STATUS_BAR_QUICK_QS_PULLDOWN, 1);
            mQuickPulldown.setValue(String.valueOf(statusQuickPulldown));
            updateQuickPulldownSummary(statusQuickPulldown);
        }


        final boolean customHeaderImage = Settings.System.getInt(getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER, 0) == 1;
        mCustomHeaderImage = (CheckBoxPreference) findPreference(CUSTOM_HEADER_IMAGE);
        mCustomHeaderImage.setChecked(customHeaderImage);

        String settingHeaderPackage = Settings.System.getString(getContentResolver(),
                Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK);
        if (settingHeaderPackage == null) {
            settingHeaderPackage = DEFAULT_HEADER_PACKAGE;
        }
        mDaylightHeaderPack = (ListPreference) findPreference(DAYLIGHT_HEADER_PACK);
        mDaylightHeaderPack.setEntries(getAvailableHeaderPacksEntries());
        mDaylightHeaderPack.setEntryValues(getAvailableHeaderPacksValues());

        int valueIndex = mDaylightHeaderPack.findIndexOfValue(settingHeaderPackage);
        if (valueIndex == -1) {
            // no longer found
            settingHeaderPackage = DEFAULT_HEADER_PACKAGE;
            Settings.System.putString(getContentResolver(),
                    Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK, settingHeaderPackage);
            valueIndex = mDaylightHeaderPack.findIndexOfValue(settingHeaderPackage);
        }
        mDaylightHeaderPack.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
        mDaylightHeaderPack.setSummary(mDaylightHeaderPack.getEntry());
        mDaylightHeaderPack.setOnPreferenceChangeListener(this);
        mDaylightHeaderPack.setEnabled(customHeaderImage);
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

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (PRE_QUICK_PULLDOWN.equals(key)) {
            try {
                int statusQuickPulldown = Integer.valueOf((String) objValue);
                Settings.System.putInt(getContentResolver(),
                    Settings.System.STATUS_BAR_QUICK_QS_PULLDOWN,
                    statusQuickPulldown);
                    updateQuickPulldownSummary(statusQuickPulldown);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Could not set quick pulldown", e);;
            }
        } else if (preference == mStatusBarBrightnessControl) {
            boolean value = (Boolean) objValue;
            Settings.System.putInt(getContentResolver(), STATUS_BAR_BRIGHTNESS_CONTROL,
                    value ? 1 : 0);
            return true;
        } else if (preference == mDaylightHeaderPack) {
            String value = (String) objValue;
            Settings.System.putString(getContentResolver(),
                    Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK, value);
            int valueIndex = mDaylightHeaderPack.findIndexOfValue(value);
            mDaylightHeaderPack.setSummary(mDaylightHeaderPack.getEntries()[valueIndex]);
        }
        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mCustomHeaderImage) {
            final boolean value = ((CheckBoxPreference)preference).isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.STATUS_BAR_CUSTOM_HEADER, value ? 1 : 0);
            mDaylightHeaderPack.setEnabled(value);
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void updateQuickPulldownSummary(int value) {
        Resources res = getResources();

        if (value == 0) {
            // quick pulldown deactivated
            mQuickPulldown.setSummary(res.getString(R.string.quick_pulldown_off));
        } else {
            Locale l = Locale.getDefault();
            boolean isRtl = TextUtils.getLayoutDirectionFromLocale(l) == View.LAYOUT_DIRECTION_RTL;
            String direction = res.getString(value == 2
                    ? (isRtl ? R.string.quick_pulldown_right : R.string.quick_pulldown_left)
                    : (isRtl ? R.string.quick_pulldown_left : R.string.quick_pulldown_right));
            mQuickPulldown.setSummary(res.getString(R.string.summary_quick_pulldown, direction));
        }
    }

        private String[] getAvailableHeaderPacksValues() {
        List<String> headerPacks = new ArrayList<String>();
        Intent i = new Intent();
        PackageManager packageManager = getPackageManager();
        i.setAction("org.omnirom.DaylightHeaderPack");
        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
            String packageName = r.activityInfo.packageName;
            if (packageName.equals(DEFAULT_HEADER_PACKAGE)) {
                headerPacks.add(0, packageName);
            } else {
                headerPacks.add(packageName);
            }
        }
        return headerPacks.toArray(new String[headerPacks.size()]);
    }

    private String[] getAvailableHeaderPacksEntries() {
        List<String> headerPacks = new ArrayList<String>();
        Intent i = new Intent();
        PackageManager packageManager = getPackageManager();
        i.setAction("org.omnirom.DaylightHeaderPack");
        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
            String packageName = r.activityInfo.packageName;
            String label = r.activityInfo.loadLabel(getPackageManager()).toString();
            if (label == null) {
                label = r.activityInfo.packageName;
            }
            if (packageName.equals(DEFAULT_HEADER_PACKAGE)) {
                headerPacks.add(0, label);
            } else {
                headerPacks.add(label);
            }
        }
        return headerPacks.toArray(new String[headerPacks.size()]);
    }






}
