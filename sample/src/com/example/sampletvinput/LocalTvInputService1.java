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

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

public class LocalTvInputService1 extends BaseLocalTvInputService {
    private static final String TAG = LocalTvInputService1.class.getSimpleName();
    private static final boolean DEBUG = true;

    private static final float WEBVIEW_ALPHA = 0.7f;
    private static final String WEBVIEW_SITE = "http://www.yahoo.com";

    @Override
    public TvInputSessionImpl onCreateSession() {
        TvInputSessionImpl impl = new BaseLocalTvInput1SessionImpl();
        impl.setOverlayViewEnabled(true);
        return impl;
    }

    @Override
    public void createSampleChannels() {
        mNumberOfChannels = 3;
        mSamples = new Sample[] {
                new Sample(R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_128kbps_22050hz,
                        "Sample Video A for LocalSampleTvInputService1"),
                new Sample(R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz,
                        "Sample Video B for LocalSampleTvInputService1") };
    }

    class BaseLocalTvInput1SessionImpl extends BaseLocalTvInputSessionImpl {
        private WebView mWebView;

        @Override
        public View onCreateOverlayView() {
            LayoutInflater inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.overlay, null);
            mWebView = (WebView) view.findViewById(R.id.webview);
            mWebView.getSettings().setJavaScriptEnabled(true);
            mWebView.setAlpha(WEBVIEW_ALPHA);
            mWebView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    view.loadUrl(url);
                    return true;
                }
            });
            mWebView.loadUrl(WEBVIEW_SITE);
            mWebView.setVisibility(View.INVISIBLE);

            Button webViewToggle = (Button) view.findViewById(R.id.toggle_webview);
            webViewToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (DEBUG) {
                        Log.d(TAG, "Toggle webview");
                    }
                    mWebView.setVisibility(mWebView.isShown() ? View.INVISIBLE : View.VISIBLE);
                }
            });
            return view;
        }
    }
}
