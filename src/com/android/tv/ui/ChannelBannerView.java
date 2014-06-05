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
import android.database.ContentObserver;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.data.ChannelMap;
import com.android.tv.data.Program;
import com.android.tv.data.StreamInfo;
import com.android.tv.util.Utils;

/**
 * A view to render channel banner.
 */
public class ChannelBannerView extends LinearLayout {
    private TextView mClosedCaptionTextView;
    private TextView mResolutionTextView;
    private TextView mAspectRatioTextView;
    private TextView mAudioChannelTextView;
    private ProgressBar mRemainingTimeView;
    private TextView mProgrameDescriptionTextView;
    private TextView mChannelTextView;
    private TextView mChannelNameTextView;
    private TextView mProgramTextView;
    private TextView mProgramTimeTextView;
    private RelativeLayout mChannelInfoBarView;
    private Uri mCurrentChannelUri;

    private final ContentObserver mProgramUpdateObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateProgramInfo();
        }
    };

    public ChannelBannerView(Context context) {
        super(context);
        mContext = context;
    }

    public ChannelBannerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public ChannelBannerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getContext().getContentResolver().registerContentObserver(TvContract.Programs.CONTENT_URI,
                true, mProgramUpdateObserver);
    }

    @Override
    protected void onDetachedFromWindow() {
        getContext().getContentResolver().unregisterContentObserver(mProgramUpdateObserver);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mClosedCaptionTextView = (TextView) findViewById(R.id.closed_caption);
        mResolutionTextView = (TextView) findViewById(R.id.resolution);
        mAspectRatioTextView = (TextView) findViewById(R.id.aspect_ratio);
        mAudioChannelTextView = (TextView) findViewById(R.id.audio_channel);
        mRemainingTimeView = (ProgressBar) findViewById(R.id.remaining_time);
        mChannelTextView = (TextView) findViewById(R.id.channel_text);
        mChannelNameTextView = (TextView) findViewById(R.id.channel_name);
        mProgramTimeTextView = (TextView) findViewById(R.id.program_time_text);
        mProgrameDescriptionTextView = (TextView) findViewById(R.id.program_description);
        mProgramTextView = (TextView) findViewById(R.id.program_text);
        mChannelInfoBarView = (RelativeLayout) findViewById(R.id.channel_info_bar);
        mChannelInfoBarView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                alignBaseline();
            }
        });
    }

    public void updateViews(ChannelMap channelMap, StreamInfo info) {
        if (channelMap == null || !channelMap.isLoadFinished()) {
            return;
        }

        if (info.hasClosedCaption()) {
            mClosedCaptionTextView.setVisibility(View.VISIBLE);
            mClosedCaptionTextView.setText("CC");
            mClosedCaptionTextView.setVisibility(View.VISIBLE);
        } else {
            mClosedCaptionTextView.setVisibility(View.GONE);
        }
        if (info.getVideoDefinitionLevel() != StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN) {
            mResolutionTextView.setVisibility(View.VISIBLE);
            mResolutionTextView.setText(Utils.getVideoDefinitionLevelString(
                    info.getVideoDefinitionLevel()));
            mResolutionTextView.setVisibility(View.VISIBLE);
        } else {
            mResolutionTextView.setVisibility(View.GONE);
        }
        // TODO: implement aspect ratio.
        mAspectRatioTextView.setVisibility(View.GONE);
        if (!TextUtils.isEmpty(Utils.getAudioChannelString(info.getAudioChannelCount()))) {
            mAudioChannelTextView.setVisibility(View.VISIBLE);
            mAudioChannelTextView.setText(Utils.getAudioChannelString(info.getAudioChannelCount()));
            mAudioChannelTextView.setVisibility(View.VISIBLE);
        } else {
            mAudioChannelTextView.setVisibility(View.GONE);
        }

        String displayNumber = channelMap.getCurrentDisplayNumber();
        if (displayNumber == null) {
            displayNumber = "";
        }
        if (displayNumber.length() <= 3) {
            mChannelTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    mContext.getResources().getDimension(
                        R.dimen.channel_banner_title_large_text_size));
        } else if (displayNumber.length() <= 4) {
            mChannelTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    mContext.getResources().getDimension(
                        R.dimen.channel_banner_title_medium_text_size));
        } else {
            mChannelTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    mContext.getResources().getDimension(
                        R.dimen.channel_banner_title_small_text_size));
        }
        mChannelTextView.setText(displayNumber);

        String displayName = channelMap.getCurrentDisplayName();
        if (displayName == null) {
            displayName = "";
        }
        mChannelNameTextView.setText(displayName);

        mCurrentChannelUri = channelMap.getCurrentChannelUri();
        updateProgramInfo();
    }

    private String getFormattedTimeString(long time) {
        return DateFormat.format(
                getContext().getString(R.string.channel_banner_time_format), time).toString();
    }

    public void updateProgramInfo() {
        if (mCurrentChannelUri == null) {
            handleNoProgramInformation();
            return;
        }

        Program program = Utils.getCurrentProgram(mContext, mCurrentChannelUri);
        if (program == null) {
            handleNoProgramInformation();
            return;
        }
        if (!TextUtils.isEmpty(program.getTitle())) {
            mProgramTextView.setText(program.getTitle());

            long startTime = program.getStartTimeUtcMillis();
            long endTime = program.getEndTimeUtcMillis();
            if (startTime > 0 && endTime > 0) {
                mProgramTimeTextView.setVisibility(View.VISIBLE);
                mRemainingTimeView.setVisibility(View.VISIBLE);

                String startTimeText = getFormattedTimeString(startTime);
                String endTimeText = getFormattedTimeString(endTime);

                mProgramTimeTextView.setText(mContext.getString(
                        R.string.channel_banner_program_time_format, startTimeText, endTimeText));

                long currTime = System.currentTimeMillis();
                if (currTime <= startTime) {
                    mRemainingTimeView.setProgress(0);
                } else if (currTime >= endTime) {
                    mRemainingTimeView.setProgress(100);
                } else {
                    mRemainingTimeView.setProgress(
                            (int) (100 *(currTime - startTime) / (endTime - startTime)));
                }
            } else {
                mProgramTimeTextView.setVisibility(View.GONE);
                mRemainingTimeView.setVisibility(View.GONE);
            }
        } else {
            mProgramTextView.setText(mContext.getString(R.string.channel_banner_no_title));
            mProgramTimeTextView.setVisibility(View.GONE);
            mRemainingTimeView.setVisibility(View.GONE);
        }
        if (!TextUtils.isEmpty(program.getDescription())) {
            mProgrameDescriptionTextView.setVisibility(View.VISIBLE);
            mProgrameDescriptionTextView.setText(program.getDescription());
        } else {
            mProgrameDescriptionTextView.setVisibility(View.GONE);
        }
    }

    private void alignBaseline() {
        final int dummyPadding = mChannelInfoBarView.getHeight() -
                (int) mContext.getResources().getDimension(
                        R.dimen.channel_banner_channel_info_bar_height);

        for (int i = 0; i < mChannelInfoBarView.getChildCount(); i++) {
            View view = mChannelInfoBarView.getChildAt(i);
            int[] rules = ((RelativeLayout.LayoutParams) view.getLayoutParams()).getRules();

            if (rules[RelativeLayout.ALIGN_PARENT_BOTTOM] == RelativeLayout.TRUE) {
                int marginBottom = dummyPadding;
                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    if (!tv.getIncludeFontPadding()) {
                        marginBottom -= (tv.getHeight() - tv.getBaseline());
                    }
                } else if (view instanceof ImageView) {
                    // TV Input Logo.
                    marginBottom += mContext.getResources().getDimension(
                        R.dimen.channel_banner_tvinput_logo_padding_bottom);
                }

                ViewGroup.MarginLayoutParams layout =
                        (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                if (marginBottom >= 0 && marginBottom != layout.bottomMargin) {
                    layout.setMargins(
                            layout.leftMargin, layout.topMargin, layout.rightMargin, marginBottom);
                    view.setLayoutParams(layout);
                }
            }
        }
    }

    private void handleNoProgramInformation() {
        mProgramTextView.setText(mContext.getString(R.string.channel_banner_no_title));
        mProgramTimeTextView.setVisibility(View.GONE);
        mRemainingTimeView.setVisibility(View.GONE);
        mProgrameDescriptionTextView.setVisibility(View.GONE);
    }
}
