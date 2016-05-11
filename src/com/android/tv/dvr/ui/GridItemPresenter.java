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

package com.android.tv.dvr.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.support.v17.leanback.widget.Presenter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.Recording;
import com.android.tv.util.Utils;

import java.util.List;

public class GridItemPresenter extends Presenter {
    private static final int GRID_ITEM_WIDTH = 200;
    private static final int GRID_ITEM_HEIGHT = 200;

    private final DvrBrowseFragment mainFragment;
    
    public GridItemPresenter(DvrBrowseFragment mainFragment) {
        this.mainFragment = mainFragment;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        TextView view = new TextView(parent.getContext());
        view.setLayoutParams(new ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT));
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setBackgroundColor(
                Utils.getColor(mainFragment.getResources(), R.color.setup_background));
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object recording) {
        if (recording == null) {
            ((TextView) viewHolder.view).setText(viewHolder.view.getContext()
                    .getString(R.string.dvr_msg_no_recording_on_the_row));
        } else {
            final Recording r = (Recording) recording;
            StringBuilder sb = new StringBuilder();
            List<Program> programs = r.getPrograms();
            if (programs != null && programs.size() > 0) {
                sb.append(programs.get(0).getTitle());
            } else {
                sb.append(viewHolder.view.getContext()
                        .getString(R.string.dvr_msg_program_title_unknown));
            }
            sb.append(" ");
            Channel channel = r.getChannel();
            if (channel != null) {
                sb.append(channel.getDisplayName());
            } else {
                sb.append(viewHolder.view.getContext().getString(R.string.dvr_msg_channel_unknown));
            }
            sb.append(" ").append(Utils.toIsoDateTimeString(r.getStartTimeMs()));
            ((TextView) viewHolder.view).setText(sb.toString());
            final Context context = viewHolder.view.getContext();
            viewHolder.view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switch (r.getState()) {
                        case Recording.STATE_RECORDING_NOT_STARTED: {
                            new AlertDialog.Builder(context)
                                    .setNegativeButton(R.string.dvr_detail_cancel,
                                            new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Toast.makeText(context, "Not implemented yet",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .show();
                            break;
                        }
                        case Recording.STATE_RECORDING_IN_PROGRESS: {
                            new AlertDialog.Builder(context)
                                    .setNegativeButton(R.string.dvr_detail_stop_delete,
                                            new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Toast.makeText(context, "Not implemented yet",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .setPositiveButton(R.string.dvr_detail_stop_keep,
                                            new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Toast.makeText(context, "Not implemented yet",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .show();
                            break;
                        }
                        case Recording.STATE_RECORDING_FINISHED: {
                            new AlertDialog.Builder(context)
                                    .setNegativeButton(R.string.dvr_detail_delete,
                                            new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            DvrManager dvrManager = TvApplication
                                                    .getSingletons(mainFragment.getContext())
                                                    .getDvrManager();
                                            // TODO(DVR) handle success/failure.
                                            dvrManager.removeRecording(r);
                                        }
                                    })
                                    .setPositiveButton(R.string.dvr_detail_play,
                                            new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Intent intent = new Intent(context, MainActivity.class);
                                            intent.putExtra(Utils.EXTRA_KEY_RECORDING_URI,
                                                    r.getUri());
                                            context.startActivity(intent);
                                            ((Activity) context).finish();
                                        }
                                    })
                                    .show();
                            break;
                        }
                    }
                }
            });
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }
}