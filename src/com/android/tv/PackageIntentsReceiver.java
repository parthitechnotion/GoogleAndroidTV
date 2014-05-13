package com.android.tv;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.TvContract;
import android.tv.TvInputInfo;
import android.tv.TvInputManager;
import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A class for handling the broadcast intents from PackageManager.
 */
public class PackageIntentsReceiver extends BroadcastReceiver {
    private TvInputManager mTvInputManager;
    private SharedPreferences mPreferences;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mPreferences == null) {
            mPreferences = context.getSharedPreferences(TvSettings.PREFS_FILE,
                    Context.MODE_PRIVATE);
        }
        if (mTvInputManager == null) {
            mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        }

        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            Uri uri = intent.getData();
            onPackageFullyRemoved(context, uri != null ? uri.getSchemeSpecificPart() : null);
        }
    }

    private void onPackageFullyRemoved(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        cleanupUnusedDisplayInputName();
    }

    private void cleanupUnusedDisplayInputName() {
        Set<String> keys = mPreferences.getAll().keySet();
        HashSet<String> unusedKeys = new HashSet<String>(keys);
        for (String key : keys) {
            if (!key.startsWith(TvSettings.PREF_DISPLAY_INPUT_NAME)) {
                unusedKeys.remove(key);
            }
        }
        List<TvInputInfo> inputs = mTvInputManager.getTvInputList();
        for (TvInputInfo input : inputs) {
            unusedKeys.remove(TvSettings.PREF_DISPLAY_INPUT_NAME + input.getId());
        }
        if (!unusedKeys.isEmpty()) {
            SharedPreferences.Editor editor = mPreferences.edit();
            for (String key : unusedKeys) {
                editor.remove(key);
            }
            editor.commit();
        }
    }
}
