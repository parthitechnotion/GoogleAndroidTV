/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.guide;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.analytics.Tracker;
import com.android.tv.data.Channel;
import com.android.tv.guide.ProgramManager.TableEntry;
import com.android.tv.util.Utils;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;

public class ProgramItemView extends TextView {
    private static final String TAG = "ProgramItemView";

    private static final long FOCUS_UPDATE_FREQUENCY = TimeUnit.SECONDS.toMillis(1);
    private static final int MAX_PROGRESS = 10000; // From android.widget.ProgressBar.MAX_VALUE

    // State indicating the focused program is the current program
    private static final int[] STATE_CURRENT_PROGRAM = { R.attr.state_current_program };

    // Workaround state in order to not use too much texture memory for RippleDrawable
    private static final int[] STATE_TOO_WIDE = { R.attr.state_program_too_wide };

    private static int sVisibleThreshold;
    private static int sMinProgramDisplayDurationPixels;
    private static int sItemPadding;
    private static TextAppearanceSpan sProgramTitleStyle;
    private static TextAppearanceSpan sGrayedOutProgramTitleStyle;
    private static TextAppearanceSpan sEpisodeTitleStyle;
    private static TextAppearanceSpan sGrayedOutEpisodeTitleStyle;

    private TableEntry mTableEntry;
    private int mMaxWidthForRipple;

    // If set this flag disables requests to re-layout the parent view as a result of changing
    // this view, improving performance. This also prevents the parent view to lose child focus
    // as a result of the re-layout (see b/21378855).
    private boolean mPreventParentRelayout;

    private static final View.OnClickListener ON_CLICKED = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            TableEntry entry = ((ProgramItemView) view).mTableEntry;
            Tracker tracker = ((TvApplication) view.getContext().getApplicationContext())
                    .getTracker();
            tracker.sendEpgItemClicked();
            if (entry.isCurrentProgram()) {
                final MainActivity tvActivity = (MainActivity) view.getContext();
                final Channel channel = tvActivity.getChannelDataManager()
                        .getChannel(entry.channelId);
                view.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        tvActivity.tuneToChannel(channel);
                        tvActivity.hideOverlaysForTune();
                    }
                }, entry.getWidth() > ((ProgramItemView) view).mMaxWidthForRipple ? 0 :
                    view.getResources().getInteger(R.integer.program_guide_ripple_anim_duration));
            }
        }
    };

    private static final View.OnFocusChangeListener ON_FOCUS_CHANGED =
            new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean hasFocus) {
            if (hasFocus) {
                ((ProgramItemView) view).mUpdateFocus.run();
            } else {
                Handler handler = view.getHandler();
                if (handler != null) {
                    handler.removeCallbacks(((ProgramItemView) view).mUpdateFocus);
                }
            }
        }
    };

    private final Runnable mUpdateFocus = new Runnable() {
        @Override
        public void run() {
            refreshDrawableState();
            TableEntry entry = mTableEntry;
            if (entry.isCurrentProgram()) {
                Drawable background = getBackground();
                int progress = getProgress(entry.entryStartUtcMillis, entry.entryEndUtcMillis);
                setProgress(background, R.id.reverse_progress, MAX_PROGRESS - progress);
            }
            if (getHandler() != null) {
                getHandler().postAtTime(this,
                        Utils.ceilTime(SystemClock.uptimeMillis(), FOCUS_UPDATE_FREQUENCY));
            }
        }
    };

    public ProgramItemView(Context context) {
        this(context, null);
    }

    public ProgramItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProgramItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private void initIfNeeded() {
        if (sVisibleThreshold != 0) {
            return;
        }
        Resources res = getContext().getResources();

        sVisibleThreshold = res.getDimensionPixelOffset(
                R.dimen.program_guide_table_item_visible_threshold);
        sMinProgramDisplayDurationPixels = res.getDimensionPixelOffset(
                R.dimen.program_guide_table_item_min_program_display_width);

        sItemPadding = res.getDimensionPixelOffset(R.dimen.program_guide_table_item_padding);

        ColorStateList programTitleColor = ColorStateList.valueOf(res.getColor(
                R.color.program_guide_table_item_program_title_text_color));
        ColorStateList grayedOutProgramTitleColor = res.getColorStateList(
                R.color.program_guide_table_item_grayed_out_program_text_color);
        ColorStateList episodeTitleColor = ColorStateList.valueOf(res.getColor(
                R.color.program_guide_table_item_program_episode_title_text_color));
        ColorStateList grayedOutEpisodeTitleColor = ColorStateList.valueOf(res.getColor(
                R.color.program_guide_table_item_grayed_out_program_episode_title_text_color));
        int programTitleSize = res.getDimensionPixelSize(
                R.dimen.program_guide_table_item_program_title_font_size);
        int episodeTitleSize = res.getDimensionPixelSize(
                R.dimen.program_guide_table_item_program_episode_title_font_size);

        sProgramTitleStyle = new TextAppearanceSpan(null, 0, programTitleSize, programTitleColor,
                null);
        sGrayedOutProgramTitleStyle = new TextAppearanceSpan(null, 0, programTitleSize,
                grayedOutProgramTitleColor, null);
        sEpisodeTitleStyle = new TextAppearanceSpan(null, 0, episodeTitleSize, episodeTitleColor,
                null);
        sGrayedOutEpisodeTitleStyle = new TextAppearanceSpan(null, 0, episodeTitleSize,
                grayedOutEpisodeTitleColor, null);
    }

    @Override
    protected void onFinishInflate() {
        initIfNeeded();
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        if (mTableEntry != null) {
            int states[] = super.onCreateDrawableState(extraSpace
                    + STATE_CURRENT_PROGRAM.length + STATE_TOO_WIDE.length);
            if (mTableEntry.isCurrentProgram()) {
                mergeDrawableStates(states, STATE_CURRENT_PROGRAM);
            }
            if (mTableEntry.getWidth() > mMaxWidthForRipple) {
                mergeDrawableStates(states, STATE_TOO_WIDE);
            }
            return states;
        }
        return super.onCreateDrawableState(extraSpace);
    }

    public TableEntry getTableEntry() {
        return mTableEntry;
    }

    public void onBind(TableEntry entry, ProgramListAdapter adapter) {
        mTableEntry = entry;
        setOnClickListener(ON_CLICKED);
        setOnFocusChangeListener(ON_FOCUS_CHANGED);
        ProgramManager programManager = adapter.getProgramManager();

        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        layoutParams.width = entry.getWidth();
        setLayoutParams(layoutParams);

        String title = entry.program != null ? entry.program.getTitle() : null;
        String episode = entry.program != null ?
                entry.program.getEpisodeDisplayTitle(getContext()) : null;

        TextAppearanceSpan titleStyle = sGrayedOutProgramTitleStyle;
        TextAppearanceSpan episodeStyle = sGrayedOutEpisodeTitleStyle;

        if (entry.getWidth() < sVisibleThreshold) {
            setText(null);
        } else {
            if (entry.isGap()) {
                if (entry.isBlocked()) {
                    title = adapter.getBlockedProgramTitle();
                } else {
                    title = adapter.getNoInfoProgramTitle();
                }
                episode = null;
            } else if (entry.hasGenre(programManager.getSelectedGenreId())) {
                titleStyle = sProgramTitleStyle;
                episodeStyle = sEpisodeTitleStyle;
            }

            SpannableStringBuilder description = new SpannableStringBuilder();
            description.append(title);
            if (!TextUtils.isEmpty(episode)) {
                description.append('\n');

                // Add a 'zero-width joiner'/ZWJ in order to ensure we have the same line height for
                // all lines. This is a non-printing character so it will not change the horizontal
                // spacing however it will affect the line height. As we ensure the ZWJ has the same
                // text style as the title it will make sure the line height is consistent.
                description.append('\u200D');

                int middle = description.length();
                description.append(episode);

                description.setSpan(titleStyle, 0, middle, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                description.setSpan(episodeStyle, middle, description.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                description.setSpan(titleStyle, 0, description.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            setText(description);
        }

        int start = GuideUtils.convertMillisToPixel(entry.entryStartUtcMillis);
        int guideStart = GuideUtils.convertMillisToPixel(programManager.getFromUtcMillis());
        layoutVisibleArea(guideStart - start);

        // Maximum width for us to use a ripple
        mMaxWidthForRipple = GuideUtils.convertMillisToPixel(
                programManager.getFromUtcMillis(), programManager.getToUtcMillis());
    }

    /**
     * Layout title and episode according to visible area.
     *
     * Here's the spec.
     *   1. Don't show text if it's shorter than 48dp.
     *   2. Try showing whole text in visible area by placing and wrapping text,
     *      but do not wrap text less than 30min.
     *   3. Episode title is visible only if title isn't multi-line.
     *
     * @param offset Offset of the start position from the enclosing view's start position.
     */
     public void layoutVisibleArea(int offset) {
        int width = mTableEntry.getWidth();
        int startPadding = Math.max(0, offset);
        if (startPadding > 0 && width - startPadding < sMinProgramDisplayDurationPixels) {
            startPadding = Math.max(0, width - sMinProgramDisplayDurationPixels);
        }

        if (startPadding + sItemPadding != getPaddingStart()) {
            mPreventParentRelayout = true; // The size of this view is kept, no need to tell parent.
            setPaddingRelative(startPadding + sItemPadding, 0, sItemPadding, 0);
            mPreventParentRelayout = false;
        }
    }

    public void onUnbind() {
        if (getHandler() != null) {
            getHandler().removeCallbacks(mUpdateFocus);
        }

        setTag(null);
        setOnFocusChangeListener(null);
        setOnClickListener(null);
    }

    private static int getProgress(long start, long end) {
        long currentTime = System.currentTimeMillis();
        if (currentTime <= start) {
            return 0;
        } else if (currentTime >= end) {
            return MAX_PROGRESS;
        }
        return (int) (((currentTime - start) * MAX_PROGRESS) / (end - start));
    }

    private static void setProgress(Drawable drawable, int id, int progress) {
        if (drawable instanceof StateListDrawable) {
            StateListDrawable stateDrawable = (StateListDrawable) drawable;
            for (int i = 0; i < getStateCount(stateDrawable); ++i) {
                setProgress(getStateDrawable(stateDrawable, i), id, progress);
            }
        } else if (drawable instanceof LayerDrawable) {
            LayerDrawable layerDrawable = (LayerDrawable) drawable;
            for (int i = 0; i < layerDrawable.getNumberOfLayers(); ++i) {
                setProgress(layerDrawable.getDrawable(i), id, progress);
                if (layerDrawable.getId(i) == id) {
                    layerDrawable.getDrawable(i).setLevel(progress);
                }
            }
        }
    }

    private static int getStateCount(StateListDrawable stateListDrawable) {
        try {
            Object stateCount = StateListDrawable.class.getDeclaredMethod("getStateCount")
                    .invoke(stateListDrawable);
            return (int) stateCount;
        } catch (NoSuchMethodException|IllegalAccessException|IllegalArgumentException
                |InvocationTargetException e) {
            Log.e(TAG, "Failed to call StateListDrawable.getStateCount()", e);
            return 0;
        }
    }

    private static Drawable getStateDrawable(StateListDrawable stateListDrawable, int index) {
        try {
            Object drawable = StateListDrawable.class
                    .getDeclaredMethod("getStateDrawable", Integer.TYPE)
                    .invoke(stateListDrawable, index);
            return (Drawable) drawable;
        } catch (NoSuchMethodException|IllegalAccessException|IllegalArgumentException
                |InvocationTargetException e) {
            Log.e(TAG, "Failed to call StateListDrawable.getStateDrawable(" + index + ")", e);
            return null;
        }
    }

    @Override
    public void requestLayout() {
        if (mPreventParentRelayout) {
            // Trivial layout, no need to tell parent.
            forceLayout();
        } else {
            super.requestLayout();
        }
    }
}
