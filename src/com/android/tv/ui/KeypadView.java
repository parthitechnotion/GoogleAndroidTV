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
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.android.tv.R;

import java.util.HashMap;
import java.util.Map;

public class KeypadView extends RelativeLayout implements View.OnClickListener,
        View.OnFocusChangeListener {
    private static final IdToKeyCodeBiMap sIdToKeyCodeBiMap;
    static {
        sIdToKeyCodeBiMap = new IdToKeyCodeBiMap();
        sIdToKeyCodeBiMap.put(R.id.key_red, KeyEvent.KEYCODE_PROG_RED);
        sIdToKeyCodeBiMap.put(R.id.key_green, KeyEvent.KEYCODE_PROG_GREEN);
        sIdToKeyCodeBiMap.put(R.id.key_yellow, KeyEvent.KEYCODE_PROG_YELLOW);
        sIdToKeyCodeBiMap.put(R.id.key_blue, KeyEvent.KEYCODE_PROG_BLUE);
        sIdToKeyCodeBiMap.put(R.id.key_1, KeyEvent.KEYCODE_1);
        sIdToKeyCodeBiMap.put(R.id.key_2, KeyEvent.KEYCODE_2);
        sIdToKeyCodeBiMap.put(R.id.key_3, KeyEvent.KEYCODE_3);
        sIdToKeyCodeBiMap.put(R.id.key_4, KeyEvent.KEYCODE_4);
        sIdToKeyCodeBiMap.put(R.id.key_5, KeyEvent.KEYCODE_5);
        sIdToKeyCodeBiMap.put(R.id.key_6, KeyEvent.KEYCODE_6);
        sIdToKeyCodeBiMap.put(R.id.key_7, KeyEvent.KEYCODE_7);
        sIdToKeyCodeBiMap.put(R.id.key_8, KeyEvent.KEYCODE_8);
        sIdToKeyCodeBiMap.put(R.id.key_9, KeyEvent.KEYCODE_9);
        sIdToKeyCodeBiMap.put(R.id.key_l, KeyEvent.KEYCODE_SPACE);  // TODO find better mapping
        sIdToKeyCodeBiMap.put(R.id.key_0, KeyEvent.KEYCODE_0);
        sIdToKeyCodeBiMap.put(R.id.key_r, KeyEvent.KEYCODE_MINUS);  // TODO find better mapping
    }

    public KeypadView(Context context) {
        this(context, null);
    }

    public KeypadView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.keypadViewStyle);
    }

    public KeypadView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public KeypadView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        inflate(getContext(), R.layout.keypad_view, this);
    }

    public boolean wantKeys() {
        return isShown();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        for (int i = 0; i < getChildCount(); ++i) {
            getChildAt(i).setOnFocusChangeListener(this);
            getChildAt(i).setOnClickListener(this);
        }
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        if (hasFocus && view.isInTouchMode()) {
            view.performClick();
        }
    }

    @Override
    public void onClick(View view) {
        int keyCode = getKeyCodeForView(view);
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            Toast.makeText(getContext(), R.string.not_implemented_yet, Toast.LENGTH_SHORT).show();
        }
    }

    private int getKeyCodeForView(View view) {
        return sIdToKeyCodeBiMap.getKeyCode(view.getId());
    }

    private static class IdToKeyCodeBiMap {
        private Map<Integer, Integer> idToKeyCodeMap = new HashMap<>();
        private Map<Integer, Integer> keyCodeToIdMap = new HashMap<>();

        public void put(int id, int keyCode){
            if (id == 0 || id == View.NO_ID) {
                throw new IllegalArgumentException("Invalid id");
            }
            if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                throw new IllegalArgumentException("Invalid key code");
            }
            if (idToKeyCodeMap.put(id, keyCode) != null) {
                throw new IllegalArgumentException("The given id is already added");
            }
            if (keyCodeToIdMap.put(keyCode, id) != null) {
                throw new IllegalArgumentException("The given key code is already added");
            }
        }

        public int getKeyCode(int id){
            Integer keyCode = idToKeyCodeMap.get(id);
            if (keyCode == null) {
                return View.NO_ID;
            }
            return keyCode;
        }
    }
}