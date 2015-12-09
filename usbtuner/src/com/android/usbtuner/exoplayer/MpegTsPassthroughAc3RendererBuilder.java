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

package com.android.usbtuner.exoplayer;

import android.media.MediaCodec;
import android.media.MediaDataSource;

import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.android.usbtuner.exoplayer.MpegTsPlayer.RendererBuilder;
import com.android.usbtuner.exoplayer.MpegTsPlayer.RendererBuilderCallback;
import com.android.usbtuner.exoplayer.ac3.Ac3TrackRenderer;
import com.android.usbtuner.tvinput.PlaybackCacheListener;

/**
 * Builder class for AC3 Passthrough track renderer objects.
 */
public class MpegTsPassthroughAc3RendererBuilder implements RendererBuilder {
    private static final int NUM_TRACKS = 3;
    private static final int VIDEO_PLAYBACK_DEADLINE_IN_MS = 5000;
    private static final int DROPPED_FRAMES_NOTIFICATION_THRESHOLD = 50;

    private final CacheManager mCacheManager;
    private final PlaybackCacheListener mCacheListener;

    public MpegTsPassthroughAc3RendererBuilder(CacheManager cacheManager,
            PlaybackCacheListener cacheListener) {
        mCacheManager = cacheManager;
        mCacheListener = cacheListener;
    }

    @Override
    public void buildRenderers(MpegTsPlayer mpegTsPlayer, MediaDataSource dataSource,
            RendererBuilderCallback callback) {
        // Build the video and audio renderers.
        SampleSource sampleSource = new DefaultSampleSource(
                new MpegTsSampleSourceExtractor(dataSource, mCacheManager, mCacheListener),
                mpegTsPlayer.isAc3Playable() ? NUM_TRACKS : NUM_TRACKS - 1);
        MediaCodecVideoTrackRenderer videoRenderer =
                new MediaCodecVideoTrackRenderer(sampleSource, null, true,
                        MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, VIDEO_PLAYBACK_DEADLINE_IN_MS,
                        null, mpegTsPlayer.getMainHandler(), mpegTsPlayer,
                        DROPPED_FRAMES_NOTIFICATION_THRESHOLD);
        Ac3TrackRenderer audioRenderer = null;
        if (mpegTsPlayer.isAc3Playable()) {
            audioRenderer = new Ac3TrackRenderer(sampleSource,
                    mpegTsPlayer.getMainHandler(), mpegTsPlayer, false);
        } else {
            mpegTsPlayer.onAudioUnplayable();
        }
        Cea708TextTrackRenderer textRenderer = new Cea708TextTrackRenderer(sampleSource);

        TrackRenderer[] renderers = new TrackRenderer[MpegTsPlayer.RENDERER_COUNT];
        renderers[MpegTsPlayer.TRACK_TYPE_VIDEO] = videoRenderer;
        renderers[MpegTsPlayer.TRACK_TYPE_AUDIO] = audioRenderer;
        renderers[MpegTsPlayer.TRACK_TYPE_TEXT] = textRenderer;
        callback.onRenderers(null, null, renderers);
    }
}
