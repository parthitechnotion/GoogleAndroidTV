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

import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.TrackInfo;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Programs;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.KeyEvent;
import android.view.Surface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract public class BaseTvInputService extends TvInputService {
    private static final String TAG = "BaseTvInputService";
    private static final boolean DEBUG = true;

    private final LongSparseArray<ChannelInfo> mChannelMap = new LongSparseArray<ChannelInfo>();
    private String mSelectedAudioTrack;
    private String mSelectedVideoTrack;
    private String mSelectedSubtitleTrack;
    private final Handler mHandler = new Handler();

    protected List<ChannelInfo> mChannels;
    private Uri mChannelUri;

    private boolean mIsPlaying;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate()");
        super.onCreate();

        // TODO: Build channel map when the session is connected. At that point, we will come to
        // know the input ID.
        buildChannelMap();
        setTheme(android.R.style.Theme_Holo_Light_NoActionBar);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    @Override
    public Session onCreateSession(String inputId) {
        if (DEBUG) Log.d(TAG, "onCreateSession(inputId=" + inputId + ")");
        return new BaseTvInputSessionImpl(this);
    }

    abstract public List<ChannelInfo> createSampleChannels();

    private synchronized void buildChannelMap() {
        Uri uri = TvContract.buildChannelsUriForInput(Utils.getInputIdFromComponentName(this,
                new ComponentName(this, this.getClass())), false);
        String[] projection = {
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_DISPLAY_NUMBER
        };
        // TODO: The app should write all channel information into DB and reconstruct channel map
        // from it.
        mChannels = createSampleChannels();
        if (mChannels == null || mChannels.isEmpty()) {
            Log.w(TAG, "No channel list.");
            return;
        }
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, projection, null, null, null);

            if (cursor == null || cursor.getCount() == 0) {
                Log.e(TAG, "Couldn't find the channel list.");
                return;
            }

            while (cursor.moveToNext()) {
                long channelId = cursor.getLong(0);
                String channelNumber = cursor.getString(1);
                if (DEBUG) Log.d(TAG, "Channel mapping: ID(" + channelId + ") -> " + channelNumber);
                mChannelMap.put(channelId, getChannelByNumber(channelNumber, false));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private ChannelInfo getChannelByNumber(String channelNumber, boolean isRetry) {
        for (ChannelInfo info : mChannels) {
            if (info.mNumber.equals(channelNumber)) {
                return info;
            }
        }
        if (!isRetry) {
            buildChannelMap();
            return getChannelByNumber(channelNumber, true);
        }
        throw new IllegalArgumentException("Unknown channel: " + channelNumber);
    }

    private ChannelInfo getChannelByUri(Uri channelUri, boolean isRetry) {
        ChannelInfo info = mChannelMap.get(ContentUris.parseId(channelUri));
        if (info == null) {
            if (!isRetry) {
                buildChannelMap();
                return getChannelByUri(channelUri, true);
            }
            throw new IllegalArgumentException("Unknown channel: " + channelUri);
        }
        return info;
    }

    class BaseTvInputSessionImpl extends TvInputService.Session {
        private MediaPlayer mPlayer;
        private float mVolume;
        private boolean mMute;
        private Map<String, TvTrackInfo> mTracks;


        protected BaseTvInputSessionImpl(Context context) {
            super(context);
            mPlayer = new MediaPlayer();
            mVolume = 1.0f;
            mMute = false;

            mPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer player, int what, int arg) {
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                        notifyVideoUnavailable(
                                TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
                        return true;
                    } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                        notifyVideoAvailable();
                        return true;
                    }
                    return false;
                }
            });
        }

        @Override
        public void onRelease() {
            if (mPlayer != null) {
                mPlayer.release();
                mPlayer = null;
            }
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            if (DEBUG) Log.d(TAG, "onSetSurface(" + surface + ")");
            mPlayer.setSurface(surface);
            return true;
        }

        @Override
        public void onSetStreamVolume(float volume) {
            if (DEBUG) Log.d(TAG, "onSetStreamVolume(" + volume + ")");
            mVolume = volume;
            mPlayer.setVolume(volume, volume);
        }

        private boolean setDataSource(MediaPlayer player, ChannelInfo channel) {
            ProgramInfo program = channel.mProgram;
            try {
                if (program.mUrl != null) {
                    player.setDataSource(program.mUrl);
                } else {
                    AssetFileDescriptor afd = getResources().openRawResourceFd(program.mResourceId);
                    if (afd == null) {
                        return false;
                    }
                    player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                            afd.getDeclaredLength());
                    afd.close();
                }
                // Android media player does not support looping for HLS.
                if (program.mUrl == null || !program.mUrl.startsWith("http")) {
                    player.setLooping(true);
                }
            } catch (IllegalArgumentException | IllegalStateException | IOException e) {
                // Do nothing.
            }
            return true;
        }

        private boolean changeChannel(Uri channelUri) {
            final ChannelInfo channel = getChannelByUri(channelUri, false);
            if (!startPlayback(channel)) {
                return false;
            }
            // Create empty program information and insert it into the database.
            // Delay intentionally to see whether the updated program information dynamically
            // replaces the previous one on the channel banner (for testing). This is to simulate
            // the actual case where we get parsed program data only after tuning is done.
            final long DELAY_FOR_TESTING_IN_MILLIS = 1000; // 1 second
            mHandler.postDelayed(
                    new AddProgramRunnable(channelUri, channel.mProgram),
                    DELAY_FOR_TESTING_IN_MILLIS);
            return true;
        }

        private boolean startPlayback(final ChannelInfo channel) {
            mIsPlaying = false;
            mPlayer.reset();
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
            if (!setDataSource(mPlayer, channel)) {
                if (DEBUG) Log.d(TAG, "Failed to set the data source");
                return false;
            }
            try {
                mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer player) {
                        if (mPlayer != null && !mPlayer.isPlaying()) {
                            int duration = mPlayer.getDuration();
                            if (duration > 0) {
                                int seekPosition = (int) (System.currentTimeMillis() % duration);
                                mPlayer.seekTo(seekPosition);
                            }
                            MediaPlayer.TrackInfo[] tracks = mPlayer.getTrackInfo();
                            setupTrackInfo(tracks, channel);
                            notifyVideoAvailable();
                            try {
                                mPlayer.start();
                                mIsPlaying = true;
                            } catch (IllegalStateException e) {
                                mIsPlaying = false;
                            }
                        }
                    }
                });
                mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                // Loop only if onPrepared has been called normally.
                                if (mIsPlaying) {
                                    startPlayback(channel);
                                }
                            }
                        });
                    }
                });
                mPlayer.prepareAsync();
            } catch (IllegalStateException e1) {
                return false;
            }
            return true;
        }

        @Override
        public boolean onTune(Uri channelUri) {
            if (DEBUG) Log.d(TAG, "onTune(" + channelUri + ")");
            return changeChannel(channelUri);
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_M) {
                mMute = !mMute;
                if (mMute) {
                    mPlayer.setVolume(0.0f, 0.0f);
                } else {
                    mPlayer.setVolume(mVolume, mVolume);
                }
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_C) {
                // This simulates the case TV input changes the channel without tune request from
                // TV app. e.g. Channel change from STB.
                if (mChannelUri != null) {
                    int index = mChannelMap.indexOfKey(ContentUris.parseId(mChannelUri));
                    long nextChannelId = mChannelMap.keyAt((index + 1) % mChannelMap.size());
                    Uri nextChannelUri = TvContract.buildChannelUri(nextChannelId);
                    changeChannel(TvContract.buildChannelUri(nextChannelId));
                    notifyChannelRetuned(nextChannelUri);
                }
                return true;
            }
            return false;
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            if (DEBUG) Log.d(TAG, "onSetCaptionEnabled(" + enabled + ")");
        }

        @Override
        public boolean onSelectTrack(int type, String trackId) {
            Log.d(TAG, "onSelectTrack(" + type + " ," + trackId + ")");
            if (mPlayer != null) {
                if (type == TvTrackInfo.TYPE_SUBTITLE) {
                    // SelectTrack only works on subtitle tracks.
                    if (trackId == null) {
                        if (mSelectedSubtitleTrack != null) {
                            mPlayer.deselectTrack(Integer.parseInt(mSelectedSubtitleTrack));
                        }
                    } else {
                        TvTrackInfo track = mTracks.get(trackId);
                        if (track == null) {
                            return false;
                        }
                        mPlayer.selectTrack(Integer.parseInt(trackId));
                        mSelectedSubtitleTrack = trackId;
                    }
                    notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, trackId);
                    return true;
                }
            }
            return false;
        }

        private void setupTrackInfo(MediaPlayer.TrackInfo[] infos, ChannelInfo channel) {
            Map<String, TvTrackInfo> tracks = new HashMap<String, TvTrackInfo>();
            // Add subtitle tracks from the real media.
            int i;
            for (i = 0; i < infos.length; ++i) {
                if (infos[i].getTrackType() == TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT
                        || infos[i].getTrackType() == TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE) {
                    tracks.put(Integer.toString(i), new TvTrackInfo.Builder(
                            TvTrackInfo.TYPE_SUBTITLE, Integer.toString(i))
                            .setLanguage("und".equals(infos[i].getLanguage()) ? null
                                    : infos[i].getLanguage())
                            .build());
                }
                Log.d(TAG, "tracks " + i + " " + infos[i].getTrackType() + " "
                        + infos[i].getLanguage());
            }
            // Add predefine video and audio track.
            mSelectedVideoTrack = Integer.toString(i++);
            tracks.put(mSelectedVideoTrack, new TvTrackInfo.Builder(
                    TvTrackInfo.TYPE_VIDEO, mSelectedVideoTrack)
                    .setVideoWidth(channel.mVideoWidth)
                    .setVideoHeight(channel.mVideoHeight)
                    .build());
            mSelectedAudioTrack = Integer.toString(i++);
            tracks.put(mSelectedAudioTrack, new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO,
                    mSelectedAudioTrack)
                    .setAudioChannelCount(channel.mAudioChannel)
                    .build());

            mTracks = tracks;
            notifyTracksChanged(new ArrayList<TvTrackInfo>(mTracks.values()));
            notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, mSelectedVideoTrack);
            notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, mSelectedAudioTrack);
        }

        private class AddProgramRunnable implements Runnable {
            private static final int PROGRAM_REPEAT_COUNT = 24;
            private final Uri mChannelUri;
            private final ProgramInfo mProgram;

            public AddProgramRunnable(Uri channelUri, ProgramInfo program) {
                mChannelUri = channelUri;
                mProgram = program;
            }

            @Override
            public void run() {
                if (mProgram.mDurationSec == 0) {
                    return;
                }
                long nowSec = System.currentTimeMillis() / 1000;
                long startTimeSec = nowSec
                        - positiveMod((nowSec - mProgram.mStartTimeSec), mProgram.mDurationSec);
                ContentValues values = new ContentValues();
                values.put(Programs.COLUMN_CHANNEL_ID, ContentUris.parseId(mChannelUri));
                values.put(Programs.COLUMN_TITLE, mProgram.mTitle);
                values.put(Programs.COLUMN_SHORT_DESCRIPTION, mProgram.mDescription);
                values.put(Programs.COLUMN_CONTENT_RATING,
                        Utils.contentRatingsToString(mProgram.mContentRatings));
                if (!TextUtils.isEmpty(mProgram.mPosterArtUri)) {
                    values.put(Programs.COLUMN_POSTER_ART_URI, mProgram.mPosterArtUri);
                }

                for (int i = 0; i < PROGRAM_REPEAT_COUNT; ++i) {
                    if (!hasProgramInfo((startTimeSec + i * mProgram.mDurationSec + 1) * 1000)) {
                        values.put(Programs.COLUMN_START_TIME_UTC_MILLIS,
                                (startTimeSec + i * mProgram.mDurationSec) * 1000);
                        values.put(Programs.COLUMN_END_TIME_UTC_MILLIS,
                                (startTimeSec + (i + 1) * mProgram.mDurationSec) * 1000);
                        getContentResolver().insert(TvContract.Programs.CONTENT_URI, values);
                    }
                }
            }

            private long positiveMod(long x, long modulo) {
                return ((x % modulo) + modulo)  % modulo;
            }

            private boolean hasProgramInfo(long timeMs) {
                Uri uri = TvContract.buildProgramsUriForChannel(mChannelUri, timeMs, timeMs);
                String[] projection = { TvContract.Programs._ID };
                Cursor cursor = null;
                try {
                    cursor = getContentResolver().query(uri, projection, null, null, null);
                    if (cursor.getCount() > 0) {
                        return true;
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                return false;
            }
        }
    }

    public static final class ChannelInfo {
        public final String mNumber;
        public final String mName;
        public final String mLogoUrl;
        public final int mVideoWidth;
        public final int mVideoHeight;
        public final int mAudioChannel;
        public final boolean mHasClosedCaption;
        public final ProgramInfo mProgram;

        public ChannelInfo(String number, String name, String logoUrl, int videoWidth,
                int videoHeight, int audioChannel, boolean hasClosedCaption, ProgramInfo program) {
            mNumber = number;
            mName = name;
            mLogoUrl = logoUrl;
            mVideoWidth = videoWidth;
            mVideoHeight = videoHeight;
            mAudioChannel = audioChannel;
            mHasClosedCaption = hasClosedCaption;
            mProgram = program;
        }
    }

    public static final class ProgramInfo {
        public final String mTitle;
        public final String mPosterArtUri;
        public final String mDescription;
        public final long mStartTimeSec;
        public final long mDurationSec;
        public final TvContentRating[] mContentRatings;
        public final String mUrl;
        public final int mResourceId;

        public ProgramInfo(String title, String posterArtUri, String description, long startTimeSec,
                long durationSec, TvContentRating[] contentRatings, String url, int resourceId) {
            mTitle = title;
            mPosterArtUri = posterArtUri;
            mDescription = description;
            mStartTimeSec = startTimeSec;
            mDurationSec = durationSec;
            mContentRatings = contentRatings;
            mUrl = url;
            mResourceId = resourceId;
        }
    }
}
