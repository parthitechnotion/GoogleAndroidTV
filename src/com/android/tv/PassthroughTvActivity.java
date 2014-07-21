/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.util.List;

/**
 * The activity for passthrough TV inputs.
 */
public class PassthroughTvActivity extends Activity {
    // STOPSHIP: Turn debugging off
    private static final boolean DEBUG = true;
    private static final String TAG = "PassthroughTvActivity";

    TvView mTvView;
    String mTvInputId;
    TvInputManager mTvInputManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passthrough_tv);

        mTvInputManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        mTvView = (TvView) findViewById(R.id.tv_view);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        startPassthroughTvInput(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        startPassthroughTvInput(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        setMediaPlaying(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mTvView.reset();
        mTvView.setStreamVolume(0f);
        setMediaPlaying(false);
    }

    @Override
    public void onStopMediaPlaying() {
        mTvView.reset();
        mTvView.setStreamVolume(0f);
        setMediaPlaying(false);
        super.onStopMediaPlaying();
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (DEBUG) Log.d(TAG, "onKeyLongPress(" + event);
        // Treat the BACK key long press as the normal press since we changed the behavior in
        // onBackPressed().
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            super.onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }

    private void startPassthroughTvInput(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            mTvInputId = null;
            if (intent.getData().getHost().equals("android.media.tv")) {
                String uriString = intent.getData().toString();
                if (DEBUG) Log.d(TAG, "Intent: " + uriString);
                Uri uri = Uri.parse(uriString);
                if (!TvContract.isChannelUriForPassthroughTvInput(uri)) {
                    Toast.makeText(this, R.string.not_passthrough_input, Toast.LENGTH_SHORT)
                            .show();
                    finish();
                    return;
                }
                mTvInputId = uri.getPathSegments().get(1);
            }
        }
        if (mTvInputId != null) {
            TvInputInfo info = mTvInputManager.getTvInputInfo(mTvInputId);
            if (info == null) {
                Toast.makeText(this, R.string.input_is_not_available, Toast.LENGTH_SHORT).show();
                finish();
                return;
            } else if (!info.isPassthroughInputType()) {
                Toast.makeText(this, R.string.not_passthrough_input, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } else {
            Toast.makeText(this, R.string.not_passthrough_input, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mTvView.tune(mTvInputId, TvContract.buildChannelUriForPassthroughTvInput(mTvInputId));
        mTvView.setStreamVolume(1.0f);
    }
}
