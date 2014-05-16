// Copyright 2014 Google Inc. All Rights Reserved.

package com.android.tv.ui;

import com.android.tv.ChannelMap;
import com.android.tv.Program;
import com.android.tv.R;
import com.android.tv.Utils;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.TvContract;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A view to render channel banner.
 */
public class ChannelBannerView extends LinearLayout {

    private static final String TAG = "ChannelBannerView";

    private TextView mChannelTextView;
    private TextView mInputSourceText;
    private TextView mProgramTextView;
    private TextView mProgramTimeTextView;
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

        mChannelTextView = (TextView) findViewById(R.id.channel_text);
        mInputSourceText = (TextView) findViewById(R.id.input_source_text);
        mProgramTextView = (TextView) findViewById(R.id.program_text);
        mProgramTimeTextView = (TextView) findViewById(R.id.program_time_text);
    }

    public void updateViews(ChannelMap channelMap) {
        mProgramTextView.setText(mContext.getText(R.string.no_program_information));
        if (channelMap == null || !channelMap.isLoadFinished()) {
            return;
        }

        String channelBannerString = "";
        String displayNumber = channelMap.getCurrentDisplayNumber();
        if (displayNumber != null) {
            channelBannerString += displayNumber;
        }
        String displayName = channelMap.getCurrentDisplayName();
        if (displayName != null) {
            channelBannerString += " " + displayName;
        }
        mChannelTextView.setText(channelBannerString);
        mInputSourceText.setText(Utils.getDisplayNameForInput(mContext,
                channelMap.getTvInputInfo(), channelMap.isUnifiedTvInput()));

        mCurrentChannelUri = channelMap.getCurrentChannelUri();
        updateProgramInfo();
    }

    private String getFormattedTimeString(long time) {
        return DateFormat.format(
                getContext().getString(R.string.channel_banner_time_format), time).toString();
    }

    public void updateProgramInfo() {
        if (mCurrentChannelUri == null) {
            return;
        }

        Program program = Utils.getCurrentProgram(mContext, mCurrentChannelUri);
        if (program == null) {
            return;
        }
        if (!TextUtils.isEmpty(program.getTitle())) {
            mProgramTextView.setText(program.getTitle());

            if (program.getStartTimeUtcMillis() > 0 && program.getEndTimeUtcMillis() > 0) {
                String startTime = getFormattedTimeString(program.getStartTimeUtcMillis());
                String endTime = getFormattedTimeString(program.getEndTimeUtcMillis());
                mProgramTimeTextView.setText(mContext.getString(
                        R.string.channel_banner_program_time_format, startTime, endTime));
            } else {
                mProgramTimeTextView.setText(null);
            }
        } else {
            // Program title might not be available at this point. Setting the text to null to
            // clear the previous program title for now. It will be filled as soon as we get the
            // updated program information.
            mProgramTimeTextView.setText(null);
        }
    }
}
