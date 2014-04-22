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
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.TvContract;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * The main activity for demonstrating TvInput app.
 */
public class SampleTvInputActivity extends Activity {
  private Cursor mCursor;

  private Cursor getChannels() {
      String[] projection = {TvContract.Channels._ID};
      Cursor cursor = getContentResolver().query(
              TvContract.Channels.CONTENT_URI, projection, null, null, null);
      if (cursor == null || cursor.getCount() < 1) {
          if (cursor != null) {
              cursor.close();
          }
          return null;
      }
      return cursor;
  }

  private Uri getChannelUri(int offset) {
      if (mCursor == null) {
          mCursor = getChannels();
      }
      mCursor.moveToFirst();
      if (!mCursor.move(offset)){
          return null;
      }
      long id = mCursor.getLong(0);
      return ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, id);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mCursor = null;
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    setContentView(layout);

    for (int i = 0; i < 4; i++) {
        Button btn = new Button(this);
        btn.setText("Tune to Channel #" + (i + 1) + " of SampleTvInput");
        {
            final int offset = i;
            btn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, getChannelUri(offset));
                    startActivity(intent);
                }
            });
        }
        layout.addView(btn);
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (mCursor != null) {
        mCursor.close();
    }
  }
}
