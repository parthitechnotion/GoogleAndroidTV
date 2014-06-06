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

package com.example.sampletvinput;

import android.app.Activity;
import android.content.ContentValues;
import android.os.Bundle;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;

import com.example.sampletvinput.BaseTvInputService.ChannelInfo;

import java.util.List;

public class ExternalFileTvInputSettingsActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        setContentView(layout);

        Button btn = new Button(this);
        btn.setText(getString(R.string.update_channels_button));
        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                updateChannels();
                finish();
            }
        });

        layout.addView(btn);
    }

    private void updateChannels() {
        getContentResolver().delete(TvContract.Channels.CONTENT_URI, null, null);
        getContentResolver().delete(TvContract.Programs.CONTENT_URI, null, null);
        List<ChannelInfo> channels = ExternalFileTvInputService.parseSampleChannels();
        ChannelUtils.populateChannels(this, ExternalFileTvInputService.class.getName(), channels);
    }
}
