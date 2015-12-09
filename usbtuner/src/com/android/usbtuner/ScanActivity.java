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

package com.android.usbtuner;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.usbtuner.ChannelScanFileParser.ScanChannel;
import com.android.usbtuner.data.Channel;
import com.android.usbtuner.data.PsipData.EitItem;
import com.android.usbtuner.data.TunerChannel;
import com.android.usbtuner.tvinput.ChannelDataManager;
import com.android.usbtuner.tvinput.EventDetector.EventListener;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the activity of scanning frequencies in search of available channels and building the
 * lineup.
 */
public class ScanActivity extends Activity {
    private static final String TAG = "ScanActivity";
    private static final boolean DEBUG = false;

    private static final long CHANNEL_SCAN_SHOW_DELAY_MS = 10000;
    private static final long CHANNEL_SCAN_PERIOD_MS = 4000;

    // Build channels out of the locally stored TS streams.
    private static final boolean SCAN_LOCAL_STREAMS = true;

    private ChannelDataManager mChannelDataManager;
    private ChannelScanTask mChannelScanTask;
    private ProgressBar mProgressBar;
    private TextView mScanningMessage;
    private View mChannelHolder;
    private ListView mChannelList;
    private ChannelAdapter mAdapter;
    private volatile boolean mChannelListVisible;
    private Button mCancelButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mChannelDataManager = new ChannelDataManager(this);
        mChannelDataManager.checkDataVersion(this);
        setContentView(R.layout.ut_channel_scan);
        mAdapter = new ChannelAdapter();
        mProgressBar = (ProgressBar) findViewById(R.id.tune_progress);
        mScanningMessage = (TextView) findViewById(R.id.tune_description);
        mChannelList = (ListView) findViewById(R.id.channel_list);
        mChannelList.setAdapter(mAdapter);
        mChannelList.setOnItemClickListener(null);
        ViewGroup progressHolder = (ViewGroup) findViewById(R.id.progress_holder);
        LayoutTransition transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        progressHolder.setLayoutTransition(transition);
        mChannelHolder = findViewById(R.id.channel_holder);
        mChannelHolder.setVisibility(View.GONE);
        mCancelButton = (Button) findViewById(R.id.tune_cancel);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        startScan(getIntent().getIntExtra(TunerSetupActivity.EXTRA_FOR_CHANNEL_SCAN_FILE, 0));
    }

    @Override
    public void onBackPressed() {
        cancelScan();
    }

    private void scrollChannelList(boolean down) {
        int start = mChannelList.getFirstVisiblePosition();
        int end = mChannelList.getLastVisiblePosition();
        if (end < 0 || start == end) {
            return;
        }
        int count = mChannelList.getCount();
        int delta = Math.max((end - start) / 2, 1);
        int pos = down ? Math.min(end + delta, count - 1) : Math.max(start - delta, 0);
        mChannelList.smoothScrollToPosition(pos);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent key) {
        if (key.getAction() == KeyEvent.ACTION_UP && mChannelListVisible) {
            switch(key.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    scrollChannelList(true);
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_UP: {
                    scrollChannelList(false);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(key);
    }

    @Override
    public void onDestroy() {
        // Ensure scan task will stop.
        mChannelScanTask.stopScan();
        super.onDestroy();
    }

    private void startScan(int channelMapId) {
        mChannelScanTask = new ChannelScanTask(channelMapId);
        mChannelScanTask.execute();
    }

    private void cancelScan() {
        if (mChannelScanTask != null) {
            mChannelScanTask.stopScan();

            // Notifies a user of waiting to finish the scanning process.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mChannelScanTask.showFinishingProgressDialog();
                }
            }, 300);

            // Hides the cancel button.
            mCancelButton.setVisibility(View.INVISIBLE);
        }
    }

    private class ChannelAdapter extends BaseAdapter {
        private final ArrayList<TunerChannel> mChannels;

        public ChannelAdapter() {
            mChannels = new ArrayList<>();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int pos) {
            return false;
        }

        @Override
        public int getCount() {
            return mChannels.size();
        }

        @Override
        public Object getItem(int pos) {
            return pos;
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Context context = parent.getContext();

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.ut_channel_list, parent, false);
            }

            TextView channelNum = (TextView) convertView.findViewById(R.id.channel_num);
            channelNum.setText(mChannels.get(position).getDisplayNumber());

            TextView channelName = (TextView) convertView.findViewById(R.id.channel_name);
            channelName.setText(mChannels.get(position).getName());
            return convertView;
        }

        public void add(TunerChannel channel) {
            mChannels.add(channel);
            notifyDataSetChanged();
        }
    }

    private class ChannelScanTask extends AsyncTask<Void, Integer, Void> implements EventListener {
        private static final int MAX_PROGRESS = 100;

        private final int mChannelMapId;
        private final UsbTunerTsScannerSource mTunerSource;
        private final FileDataSource mFileSource;
        private final ConditionVariable mConditionStopped;

        private List<ScanChannel> mScanChannelList;
        private boolean mIsFinished;
        private ProgressDialog mFinishingProgressDialog;

        public ChannelScanTask(int channelMapId) {
            mChannelMapId = channelMapId;
            mTunerSource = new UsbTunerTsScannerSource(getApplicationContext(), this);
            if (SCAN_LOCAL_STREAMS) {
                mFileSource = new FileDataSource(this);
            }
            mConditionStopped = new ConditionVariable();
        }

        private void maybeSetChannelListVisible() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int channelsFound = mAdapter.getCount();
                    if (!mChannelListVisible && channelsFound > 0) {
                        String format = getResources().getQuantityString(
                                R.plurals.ut_channel_scan_message, channelsFound, channelsFound);
                        mScanningMessage.setText(String.format(format, channelsFound));
                        mChannelHolder.setVisibility(View.VISIBLE);
                        mChannelListVisible = true;
                    }
                }
            });
        }

        private void addChannel(final TunerChannel channel) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapter.add(channel);
                    if (mChannelListVisible) {
                        int channelsFound = mAdapter.getCount();
                        String format = getResources().getQuantityString(
                                R.plurals.ut_channel_scan_message, channelsFound, channelsFound);
                        mScanningMessage.setText(String.format(format, channelsFound));
                    }
                }
            });
        }

        private synchronized void finishScan() {
            if (!mIsFinished) {
                Intent intent = getIntent();
                intent.putExtra(TunerSetupActivity.EXTRA_FOR_SCANNED_RESULT,
                        mChannelDataManager.getScannedChannelCount());
                setResult(Activity.RESULT_OK, intent);
                ScanActivity.this.finish();
                mIsFinished = true;
                if (mFinishingProgressDialog != null && mFinishingProgressDialog.isShowing()) {
                    mFinishingProgressDialog.dismiss();
                }
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            mScanChannelList = ChannelScanFileParser.parseScanFile(
                    getResources().openRawResource(mChannelMapId));
            if (SCAN_LOCAL_STREAMS) {
                FileDataSource.addLocalStreamFiles(mScanChannelList);
            }
            scanChannels();
            mChannelDataManager.setCurrentVersion(ScanActivity.this);
            mChannelDataManager.release();
            finishScan();
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            mProgressBar.setProgress(values[0]);
        }

        private void stopScan() {
            mConditionStopped.open();
        }

        private void scanChannels() {
            if (DEBUG) Log.i(TAG, "Channel scan starting");
            mChannelDataManager.notifyScanStarted();

            long startMs = System.currentTimeMillis();
            int i = 1;
            for (ScanChannel scanChannel : mScanChannelList) {
                int frequency = scanChannel.frequency;
                String modulation = scanChannel.modulation;
                Log.i(TAG, "Tuning to " + frequency + " " + modulation);

                InputStreamSource source = getDataSource(scanChannel.type);
                Assert.assertNotNull(source);
                if (source.setScanChannel(scanChannel)) {
                    source.startStream();
                    mConditionStopped.block(CHANNEL_SCAN_PERIOD_MS);
                    source.stopStream();

                    if (System.currentTimeMillis() > startMs + CHANNEL_SCAN_SHOW_DELAY_MS
                            && !mChannelListVisible) {
                            maybeSetChannelListVisible();
                    }
                }
                if (mConditionStopped.block(-1)) {
                    break;
                }
                onProgressUpdate(MAX_PROGRESS * i++ / mScanChannelList.size());
            }
            mTunerSource.release();
            mFileSource.release();
            mChannelDataManager.notifyScanCompleted();
            if (!mConditionStopped.block(-1)) {
                publishProgress(MAX_PROGRESS);
            }
            if (DEBUG) Log.i(TAG, "Channel scan ended");
        }


        private InputStreamSource getDataSource(int type) {
            switch (type) {
                case Channel.TYPE_TUNER:
                    return mTunerSource;
                case Channel.TYPE_FILE:
                    return mFileSource;
                default:
                    return null;
            }
        }

        @Override
        public void onEventDetected(TunerChannel channel, List<EitItem> items) {
            mChannelDataManager.notifyEventDetected(channel, items);
        }

        @Override
        public void onChannelDetected(TunerChannel channel, boolean channelArrivedAtFirstTime) {
            if (DEBUG && channelArrivedAtFirstTime) {
                Log.d(TAG, "Found channel " + channel);
            }
            if (channelArrivedAtFirstTime) {
                addChannel(channel);
            }
            mChannelDataManager.notifyChannelDetected(channel, channelArrivedAtFirstTime);
        }

        public synchronized void showFinishingProgressDialog() {
            // Show a progress dialog to wait for the scanning process if it's not done yet.
            if (!mIsFinished) {
                mFinishingProgressDialog = ProgressDialog.show(ScanActivity.this, "",
                        ScanActivity.this.getResources().getString(R.string.ut_setup_cancel), true);
            }
        }
    }
}
