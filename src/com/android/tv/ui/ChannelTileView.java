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

package com.android.tv.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.util.Preconditions;
import com.android.tv.R;
import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.util.BitmapUtils;
import com.android.tv.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * A view to render channel tile.
 */
public class ChannelTileView extends ShadowContainer
        implements ItemListView.TileView, Channel.LoadLogoCallback {
    private static final String TAG = "ChannelTileView";

    private float mRoundRadius;
    private float mPosterArtWidth;
    private float mPosterArtHeight;
    private LinearLayout mChannelInfosLayout;
    private ImageView mProgramPosterArtView;
    private ImageView mChannelLogoView;
    private TextView mChannelNameView;
    private TextView mChannelNumberView;
    private TextView mProgramNameView;
    private Channel mChannel;
    private Drawable mNormalBackgroud;
    private Drawable mBackgroundOnImage;

    public ChannelTileView(Context context) {
        super(context);
    }

    public ChannelTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ChannelTileView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void loadViews() {
        mChannelInfosLayout = (LinearLayout) findViewById(R.id.channel_infos);
        mChannelLogoView = (ImageView) findViewById(R.id.channel_logo);
        mChannelNameView = (TextView) findViewById(R.id.channel_name);
        mChannelNumberView = (TextView) findViewById(R.id.channel_number);
        mProgramNameView = (TextView) findViewById(R.id.program_name);
        mProgramPosterArtView = (ImageView) findViewById(R.id.program_poster_art);
        mChannelNameView.setVisibility(INVISIBLE);

        mNormalBackgroud = getResources().getDrawable(R.drawable.channel_tile_top);
        mBackgroundOnImage = getResources().getDrawable(R.drawable.channel_tile_top_on_image);
        mRoundRadius = getResources().getDimension(R.dimen.channel_tile_round_radius);
        mPosterArtWidth = getResources().getDimension(R.dimen.channel_tile_poster_art_width);
        mPosterArtHeight = getResources().getDimension(R.dimen.channel_tile_poster_art_height);
    }

    @Override
    public void populateViews(View.OnClickListener listener, Object item) {
        Preconditions.checkNotNull(item);

        mChannel = (Channel) item;
        setOnClickListener(listener);
        setTag(MainMenuView.MenuTag.buildTag(mChannel));

        if (mChannel.getType() == R.integer.channel_type_guide) {
            mChannelInfosLayout.setBackground(mNormalBackgroud);
            mChannelNumberView.setVisibility(INVISIBLE);
            mChannelNameView.setVisibility(INVISIBLE);
            mProgramPosterArtView.setVisibility(INVISIBLE);
            mChannelLogoView.setImageResource(R.drawable.ic_channel_guide);
            mChannelLogoView.setVisibility(VISIBLE);
            mProgramNameView.setText(R.string.menu_program_guide);
        } else {
            mChannelNumberView.setText(mChannel.getDisplayNumber());
            mChannelNumberView.setVisibility(VISIBLE);
            if (!mChannel.isLogoLoaded()) {
                showName();
            }
            mChannel.loadLogo(getContext(), this);
            updateProgramInformation();
        }
    }

    @Override
    public void onLoadLogoFinished(Channel channel, Bitmap logo) {
        if (channel.getId() != mChannel.getId()) {
            return;
        }
        if (logo == null) {
            showName();
        } else {
            showLogo(logo);
        }
    }

    private void showName() {
        mChannelNameView.setText(mChannel.getDisplayName());
        mChannelNameView.setVisibility(VISIBLE);
        mChannelLogoView.setVisibility(INVISIBLE);
    }

    private void showLogo(Bitmap logo) {
        mChannelLogoView.setImageBitmap(logo);
        mChannelLogoView.setVisibility(VISIBLE);
        mChannelNameView.setVisibility(INVISIBLE);
    }

    private void setProgramPosterArt(Bitmap bm) {
        mProgramPosterArtView.setImageBitmap(BitmapUtils.getRoundedCornerBitmap(bm,
                mRoundRadius, mPosterArtWidth, mPosterArtHeight));
        mProgramPosterArtView.setVisibility(VISIBLE);
        mChannelInfosLayout.setBackground(mBackgroundOnImage);
    }

    public void updateProgramInformation() {
        if (mProgramNameView == null || mProgramPosterArtView == null || mChannel == null
                || mChannel.getType() == R.integer.channel_type_guide) {
            return;
        }

        Program program = Utils.getCurrentProgram(getContext(),
                Utils.getChannelUri(mChannel.getId()));
        if (program == null || TextUtils.isEmpty(program.getTitle())) {
            mProgramNameView.setText(getContext().getText(R.string.no_program_information));
        } else {
            mProgramNameView.setText(program.getTitle());
        }
        String posterArtUri = program.getPosterArtUri();
        if (!TextUtils.isEmpty(posterArtUri)) {
            Bitmap bm = null;
            InputStream is = null;
            try {
                is = getContext().getContentResolver().openInputStream(Uri.parse(posterArtUri));
                bm = BitmapUtils.decodeSampledBitmapFromStream(is, (int) mPosterArtWidth,
                        (int) mPosterArtHeight);
            } catch (IOException ie) {
                // Ignore exception
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error in closing file", e);
                    }
                }
            }

            if (bm != null) {
                setProgramPosterArt(bm);
            } else {
                mProgramPosterArtView.setVisibility(INVISIBLE);
                mChannelInfosLayout.setBackground(mNormalBackgroud);
                try {
                    URL imageUrl = new URL(posterArtUri);
                    AsyncTask<URL, Void, Bitmap> task = new AsyncTask<URL, Void, Bitmap>() {
                        @Override
                        protected Bitmap doInBackground(URL... params) {
                            URL imageUrl = params[0];
                            try {
                                return BitmapFactory.decodeStream(imageUrl.openStream());
                            } catch (IOException ie) {
                                Log.w(TAG, "failed to read url: " + imageUrl, ie);
                            }

                            return null;
                        }

                        @Override
                        protected void onPostExecute(Bitmap bm) {
                            if (bm != null) {
                                setProgramPosterArt(bm);
                            }
                        }
                    };
                    task.execute(imageUrl);
                } catch (IOException ie) {
                    Log.w(TAG, "failed to read uri: " + posterArtUri, ie);
                }
            }
        } else {
            mProgramPosterArtView.setVisibility(INVISIBLE);
            mChannelInfosLayout.setBackground(mNormalBackgroud);
        }
    }
}