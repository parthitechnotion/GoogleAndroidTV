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
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelMap;
import com.android.tv.data.Program;
import com.android.tv.data.StreamInfo;
import com.android.tv.util.Utils;

/**
 * A view to render channel banner.
 */
public class ChannelBannerView extends RelativeLayout implements Channel.LoadLogoCallback {
    private static final int CACHE_SIZE = 10;
    // TODO: Need to get UX design for how to show ratings in the channel banner.
    private TextView mRatingTextView;
    private TextView mClosedCaptionTextView;
    private TextView mResolutionTextView;
    private TextView mAspectRatioTextView;
    private TextView mAudioChannelTextView;
    private ProgressBar mRemainingTimeView;
    private TextView mProgrameDescriptionTextView;
    private TextView mChannelTextView;
    private TextView mChannelNameTextView;
    private ImageView mTvInputLogoImageView;
    private ImageView mChannelLogoImageView;
    private TextView mProgramTextView;
    private TextView mProgramTimeTextView;
    private View mAnchorView;
    private Uri mCurrentChannelUri;
    private final LruCache<TvInputInfo, Drawable> mChannelInfoLogoCache =
            new LruCache<TvInputInfo, Drawable> (CACHE_SIZE) {
                @Override
                protected Drawable create(TvInputInfo info) {
                    return info.loadIcon(getContext());
                }
            };

    private final ContentObserver mProgramUpdateObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            // TODO: This {@code uri} argument may be a program which is not related to this
            // channel. Consider adding channel id as a parameter of program URI to avoid
            // unnecessary update.
            post(mProgramUpdateRunnable);
        }
    };

    private final Runnable mProgramUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            removeCallbacks(this);
            updateProgramInfo();
        }
    };

    public ChannelBannerView(Context context) {
        this(context, null);
    }

    public ChannelBannerView(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
    }

    public ChannelBannerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
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

        mRatingTextView = (TextView) findViewById(R.id.rating);
        mClosedCaptionTextView = (TextView) findViewById(R.id.closed_caption);
        mResolutionTextView = (TextView) findViewById(R.id.resolution);
        mAspectRatioTextView = (TextView) findViewById(R.id.aspect_ratio);
        mAudioChannelTextView = (TextView) findViewById(R.id.audio_channel);
        mRemainingTimeView = (ProgressBar) findViewById(R.id.remaining_time);
        mChannelTextView = (TextView) findViewById(R.id.channel_text);
        mChannelNameTextView = (TextView) findViewById(R.id.channel_name);
        mTvInputLogoImageView = (ImageView) findViewById(R.id.tvinput_logo);
        mChannelLogoImageView = (ImageView) findViewById(R.id.channel_logo);
        mProgramTimeTextView = (TextView) findViewById(R.id.program_time_text);
        mProgrameDescriptionTextView = (TextView) findViewById(R.id.program_description);
        mProgramTextView = (TextView) findViewById(R.id.program_text);
        mAnchorView = findViewById(R.id.anchor);
    }

    public void updateViews(ChannelMap channelMap, StreamInfo info) {
        if (channelMap == null || !channelMap.isLoadFinished()) {
            return;
        }

        TvInputInfo inputInfo = (info == null) ? null : info.getCurrentTvInputInfo();
        Drawable tvInputLogo = (inputInfo == null) ? null : mChannelInfoLogoCache.get(inputInfo);
        if (tvInputLogo != null) {
            mTvInputLogoImageView.setVisibility(View.VISIBLE);
            mTvInputLogoImageView.setImageDrawable(tvInputLogo);
        } else {
            mTvInputLogoImageView.setVisibility(View.GONE);
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

        String aspectRatio =
                Utils.getAspectRatioString(info.getVideoWidth(), info.getVideoHeight());
        if (!TextUtils.isEmpty(aspectRatio)) {
            mAspectRatioTextView.setVisibility(View.VISIBLE);
            mAspectRatioTextView.setText(aspectRatio);
        } else {
            mAspectRatioTextView.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(Utils.getAudioChannelString(info.getAudioChannelCount()))) {
            mAudioChannelTextView.setVisibility(View.VISIBLE);
            mAudioChannelTextView.setText(Utils.getAudioChannelString(info.getAudioChannelCount()));
        } else {
            mAudioChannelTextView.setVisibility(View.GONE);
        }

        String displayNumber = channelMap.getCurrentDisplayNumber();
        if (displayNumber == null) {
            displayNumber = "";
        }

        if (displayNumber.length() <= 3) {
            updateTextView(
                    mChannelTextView,
                    R.dimen.channel_banner_title_large_text_size,
                    R.dimen.channel_banner_title_large_margin_top);
        } else if (displayNumber.length() <= 4) {
            updateTextView(
                    mChannelTextView,
                    R.dimen.channel_banner_title_medium_text_size,
                    R.dimen.channel_banner_title_medium_margin_top);
        } else {
            updateTextView(
                    mChannelTextView,
                    R.dimen.channel_banner_title_small_text_size,
                    R.dimen.channel_banner_title_small_margin_top);
        }
        mChannelTextView.setText(displayNumber);

        String displayName = channelMap.getCurrentDisplayName();
        if (displayName == null) {
            displayName = "";
        }
        mChannelNameTextView.setText(displayName);

        mCurrentChannelUri = channelMap.getCurrentChannelUri();
        if (channelMap.getCurrentChannel() != null) {
            channelMap.getCurrentChannel().loadLogo(getContext(), this);
        }

        updateProgramInfo();
    }

    private void updateTextView(TextView textView, int sizeRes, int marginTopRes) {
        float textSize = getContext().getResources().getDimension(sizeRes);
        if (textView.getTextSize() != textSize) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        }
        updateTopMargin(textView, marginTopRes);
    }

    private void updateTopMargin(View view, int marginTopRes) {
        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        int topMargin = (int) getContext().getResources().getDimension(marginTopRes);
        if (lp.topMargin != topMargin) {
            lp.topMargin = topMargin;
            view.setLayoutParams(lp);
        }
    }

    @Override
    public void onLoadLogoFinished(Channel channel, Bitmap logo) {
        if (logo == null) {
            mChannelLogoImageView.setVisibility(View.GONE);
        } else {
            mChannelLogoImageView.setImageBitmap(logo);
            mChannelLogoImageView.setVisibility(View.VISIBLE);
        }
    }

    private String getFormattedTimeString(long time) {
        return DateFormat.format(
                getContext().getString(R.string.channel_banner_time_format), time).toString();
    }

    private void updateProgramInfo() {
        if (mCurrentChannelUri == null) {
            handleNoProgramInformation();
            return;
        }

        Program program = Utils.getCurrentProgram(getContext(), mCurrentChannelUri);
        if (program == null) {
            handleNoProgramInformation();
            return;
        }

        String title = program.getTitle();
        if (!TextUtils.isEmpty(title)) {
            int width = mProgramTextView.getWidth();
            if (width == 0) {
                post(mProgramUpdateRunnable);
            }
            float largeTextSize = getContext().getResources().getDimension(
                    R.dimen.channel_banner_program_large_text_size);
            Typeface font = mProgramTextView.getTypeface();
            int estimatedLineCount = estimateLineCount(title, font, largeTextSize, width);
            boolean oneline = true;
            if (estimatedLineCount > 1) {
                updateTextView(
                        mProgramTextView,
                        R.dimen.channel_banner_program_medium_text_size,
                        R.dimen.channel_banner_program_medium_margin_top);
                float mediumTextSize = getContext().getResources().getDimension(
                        R.dimen.channel_banner_program_medium_text_size);
                if (estimateLineCount(title, font, mediumTextSize, width) > 1) {
                    oneline = false;
                }
            } else {
                updateTextView(
                        mProgramTextView,
                        R.dimen.channel_banner_program_large_text_size,
                        R.dimen.channel_banner_program_large_margin_top);
            }
            updateTopMargin(mAnchorView, oneline
                    ? R.dimen.channel_banner_anchor_one_line_y
                    : R.dimen.channel_banner_anchor_two_line_y);
            mProgramTextView.setText(title);

            long startTime = program.getStartTimeUtcMillis();
            long endTime = program.getEndTimeUtcMillis();
            if (startTime > 0 && endTime > 0) {
                mProgramTimeTextView.setVisibility(View.VISIBLE);
                mRemainingTimeView.setVisibility(View.VISIBLE);

                String startTimeText = getFormattedTimeString(startTime);
                String endTimeText = getFormattedTimeString(endTime);

                mProgramTimeTextView.setText(getContext().getString(
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
            mProgramTextView.setText(getContext().getString(R.string.channel_banner_no_title));
            mProgramTimeTextView.setVisibility(View.GONE);
            mRemainingTimeView.setVisibility(View.GONE);
        }
        if (!TextUtils.isEmpty(program.getDescription())) {
            mProgrameDescriptionTextView.setVisibility(View.VISIBLE);
            mProgrameDescriptionTextView.setText(program.getDescription());
        } else {
            mProgrameDescriptionTextView.setVisibility(View.GONE);
        }
        if (program.getContentRatings() != null) {
            mRatingTextView.setText(Utils.contentRatingsToString(program.getContentRatings()));
            mRatingTextView.setVisibility(View.VISIBLE);
        } else {
            mRatingTextView.setVisibility(View.GONE);
        }
    }

    private void handleNoProgramInformation() {
        mProgramTextView.setText(getContext().getString(R.string.channel_banner_no_title));
        mProgramTimeTextView.setVisibility(View.GONE);
        mRemainingTimeView.setVisibility(View.GONE);
        mProgrameDescriptionTextView.setVisibility(View.GONE);
    }

    private int estimateLineCount(String str, Typeface font, float textSize, int width) {
        if (width == 0) {
            return -1;
        }
        Paint paint = new Paint();
        paint.setTypeface(font);
        paint.setTextSize(textSize);
        // Add +1 to measured size, because number of lines becomes 2
        // when measured size equals width.
        return divideRoundUp((int) paint.measureText(str) + 1, width);
    }

    private int divideRoundUp(int x, int y) {
        return (x + y - 1) / y;
    }
}
