/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.backup.IBackupManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.util.SparseBooleanArray;
import android.util.Log;
import android.widget.ListView;

import com.android.internal.logging.MetricsLogger;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gesture lock pattern settings.
 */
public class PrivacySettings extends SettingsPreferenceFragment implements Indexable {

    // Vendor specific
    private static final String GSETTINGS_PROVIDER = "com.google.settings";
    private static final String BACKUP_DATA = "backup_data";
    private static final String AUTO_RESTORE = "auto_restore";
    private static final String RESET_PREFERENCES = "user_preferences_reset";
    private static final String CONFIGURE_ACCOUNT = "configure_account";
    private static final String BACKUP_INACTIVE = "backup_inactive";
    private static final String NETWORK_RESET = "network_reset";
    private static final String FACTORY_RESET = "factory_reset";
    private static final String TAG = "PrivacySettings";
    private IBackupManager mBackupManager;
    private PreferenceScreen mBackup;
    private SwitchPreference mAutoRestore;
    private Preference mResetUserPreferences;
    private PreferenceScreen mConfigure;
    private boolean mEnabled;

    private final HashMap<String, String> mResettablePrefs = new HashMap<String, String>();

    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.PRIVACY;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Don't allow any access if this is a secondary user
        mEnabled = Process.myUserHandle().isOwner();
        if (!mEnabled) {
            return;
        }

        addPreferencesFromResource(R.xml.privacy_settings);
        final PreferenceScreen screen = getPreferenceScreen();
        mBackupManager = IBackupManager.Stub.asInterface(
                ServiceManager.getService(Context.BACKUP_SERVICE));

        mBackup = (PreferenceScreen) screen.findPreference(BACKUP_DATA);

        mAutoRestore = (SwitchPreference) screen.findPreference(AUTO_RESTORE);
        mAutoRestore.setOnPreferenceChangeListener(preferenceChangeListener);

        mConfigure = (PreferenceScreen) screen.findPreference(CONFIGURE_ACCOUNT);

        mResetUserPreferences = screen.findPreference(RESET_PREFERENCES);
        mResetUserPreferences.setShouldDisableView(true);
        mResetUserPreferences.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                showResetList();
                return true;
            }
        });
        updateResetUserPreferences();

        Set<String> keysToRemove = new HashSet<>();
        getNonVisibleKeys(getActivity(), keysToRemove);
        final int screenPreferenceCount = screen.getPreferenceCount();
        for (int i = screenPreferenceCount - 1; i >= 0; --i) {
            Preference preference = screen.getPreference(i);
            if (keysToRemove.contains(preference.getKey())) {
                screen.removePreference(preference);
            }
        }

        updateToggles();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Refresh UI
        if (mEnabled) {
            updateToggles();
        }
        updateResetUserPreferences();
    }

    private OnPreferenceChangeListener preferenceChangeListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (!(preference instanceof SwitchPreference)) {
                return true;
            }
            boolean nextValue = (Boolean) newValue;
            boolean result = false;
            if (preference == mAutoRestore) {
                try {
                    mBackupManager.setAutoRestore(nextValue);
                    result = true;
                } catch (RemoteException e) {
                    mAutoRestore.setChecked(!nextValue);
                }
            }
            return result;
        }
    };


    /*
     * Creates toggles for each backup/reset preference.
     */
    private void updateToggles() {
        ContentResolver res = getContentResolver();

        boolean backupEnabled = false;
        Intent configIntent = null;
        String configSummary = null;
        try {
            backupEnabled = mBackupManager.isBackupEnabled();
            String transport = mBackupManager.getCurrentTransport();
            configIntent = mBackupManager.getConfigurationIntent(transport);
            configSummary = mBackupManager.getDestinationString(transport);

            mBackup.setSummary(backupEnabled
                    ? R.string.accessibility_feature_state_on
                    : R.string.accessibility_feature_state_off);
        } catch (RemoteException e) {
            // leave it 'false' and disable the UI; there's no backup manager
            mBackup.setEnabled(false);
        }

        mAutoRestore.setChecked(Settings.Secure.getInt(res,
                Settings.Secure.BACKUP_AUTO_RESTORE, 1) == 1);
        mAutoRestore.setEnabled(backupEnabled);

        final boolean configureEnabled = (configIntent != null) && backupEnabled;
        mConfigure.setEnabled(configureEnabled);
        mConfigure.setIntent(configIntent);
        setConfigureSummary(configSummary);
    }

    private void showResetList() {
        updateResetUserPreferences();
        if (mResettablePrefs.size() == 0) {
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.reset_user_preferences_dialog_title);

        final CharSequence[] c = mResettablePrefs.values()
                .toArray(new CharSequence[mResettablePrefs.size()]);
        builder.setMultiChoiceItems(c, null, null);

        builder.setPositiveButton(R.string.reset_user_preferences_dialog_choice_reset,
                new DialogInterface.OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                final ListView resetList = ((AlertDialog) dialog).getListView();

                final ContentResolver resolver = getContentResolver();
                final SparseBooleanArray checked = resetList.getCheckedItemPositions();
                for (int i = 0; i < resetList.getCount(); i++) {
                    if (checked.get(i)) {
                        final String value = resetList.getItemAtPosition(i).toString();
                        Settings.System.putInt(resolver, getKey(value), 0);
                    }
                }
                updateResetUserPreferences();
                dialog.dismiss();
            }
        });
        
        builder.setNegativeButton(R.string.reset_user_preferences_dialog_choice_cancel,
             new DialogInterface.OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                dialog.dismiss();
            }
        });

        builder.create().show();
    }

    private void updateResetUserPreferences() {
        mResettablePrefs.clear();
        try {
            final ContentResolver resolver = getContentResolver();
            final Resources r = getActivity().getApplicationContext()
                    .createPackageContext("com.android.systemui", 0).getResources();

            for (final String setting : Settings.System.SETTINGS_TO_RESET) {
                if (Settings.System.getInt(resolver, setting, 0) != 0) {
                    final int resId = r.getIdentifier(setting, "string", "com.android.systemui");
                    if (resId == 0) {
                        Log.v(TAG, "Missing string for: " + setting);
                    } else {
                    	try {
                        final String value = r.getString(resId);
                            mResettablePrefs.put(setting.toLowerCase(), value);
                        } catch (final Resources.NotFoundException e) {
                            Log.e(TAG, "Resource not found for: " + setting, e);
                        }
                    }
                }
            }
        } catch (final PackageManager.NameNotFoundException e) {
            Log.e(TAG, "SystemUI package not found.", e);
        }

        if (mResetUserPreferences != null) {
            final boolean enabled = mResettablePrefs.size() > 0;
            mResetUserPreferences.setEnabled(enabled);
            mResetUserPreferences.setSummary(enabled ?
                    R.string.reset_user_preferences_summary :
                    R.string.reset_user_preferences_disabled_summary);
        }
    }

    private String getKey(final String value) {
        for (final Map.Entry<String, String> entry : mResettablePrefs.entrySet()) {
            if (value.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void setConfigureSummary(String summary) {
        if (summary != null) {
            mConfigure.setSummary(summary);
        } else {
            mConfigure.setSummary(R.string.backup_configure_account_default_summary);
        }
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_backup_reset;
    }

    /**
     * For Search.
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new PrivacySearchIndexProvider();

    private static class PrivacySearchIndexProvider extends BaseSearchIndexProvider {

        boolean mIsPrimary;

        public PrivacySearchIndexProvider() {
            super();

            mIsPrimary = UserHandle.myUserId() == UserHandle.USER_OWNER;
        }

        @Override
        public List<SearchIndexableResource> getXmlResourcesToIndex(
                Context context, boolean enabled) {

            List<SearchIndexableResource> result = new ArrayList<SearchIndexableResource>();

            // For non-primary user, no backup or reset is available
            if (!mIsPrimary) {
                return result;
            }

            SearchIndexableResource sir = new SearchIndexableResource(context);
            sir.xmlResId = R.xml.privacy_settings;
            result.add(sir);

            return result;
        }

        @Override
        public List<String> getNonIndexableKeys(Context context) {
            final List<String> nonVisibleKeys = new ArrayList<>();
            getNonVisibleKeys(context, nonVisibleKeys);
            return nonVisibleKeys;
        }
    }

    private static void getNonVisibleKeys(Context context, Collection<String> nonVisibleKeys) {
        final IBackupManager backupManager = IBackupManager.Stub.asInterface(
                ServiceManager.getService(Context.BACKUP_SERVICE));
        boolean isServiceActive = false;
        try {
            isServiceActive = backupManager.isBackupServiceActive(UserHandle.myUserId());
        } catch (RemoteException e) {
            Log.w(TAG, "Failed querying backup manager service activity status. " +
                    "Assuming it is inactive.");
        }
        boolean vendorSpecific = context.getPackageManager().
                resolveContentProvider(GSETTINGS_PROVIDER, 0) == null;
        if (vendorSpecific || isServiceActive) {
            nonVisibleKeys.add(BACKUP_INACTIVE);
        }
        if (vendorSpecific || !isServiceActive) {
            nonVisibleKeys.add(BACKUP_DATA);
            nonVisibleKeys.add(AUTO_RESTORE);
            nonVisibleKeys.add(CONFIGURE_ACCOUNT);
        }
        if (UserManager.get(context).hasUserRestriction(
                UserManager.DISALLOW_FACTORY_RESET)) {
            nonVisibleKeys.add(FACTORY_RESET);
        }
        if (UserManager.get(context).hasUserRestriction(
                UserManager.DISALLOW_NETWORK_RESET)) {
            nonVisibleKeys.add(NETWORK_RESET);
        }
    }
}
