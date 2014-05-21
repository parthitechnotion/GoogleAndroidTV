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

package com.android.tv.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

import com.android.tv.TvActivity;

/**
 * Handles global keys.
 */
public class GlobalKeyReceiver extends BroadcastReceiver {
    private static final boolean DEBUG = true;
    private static final String TAG = "GlobalKeyReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_GLOBAL_BUTTON.equals(intent.getAction())) {
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (DEBUG)
                Log.d(TAG, "onReceive: " + event);
            int keyCode = event.getKeyCode();
            int action = event.getAction();
            if (keyCode == KeyEvent.KEYCODE_TV && action == KeyEvent.ACTION_DOWN) {
                Intent newIntent = new Intent(context, TvActivity.class);
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(newIntent);
            }
        }
    }
}