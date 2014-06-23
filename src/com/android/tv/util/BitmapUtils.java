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

package com.android.tv.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.InputStream;

public class BitmapUtils {
    private BitmapUtils() { /* cannot be instantiated */ }

    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap, float roundPx, float targetWidth,
            float targetHeight) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);

        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawRoundRect(rectF, roundPx * bitmap.getWidth() / targetWidth,
                roundPx * bitmap.getHeight() / targetHeight, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    public static Bitmap scaleBitmap(Bitmap bm, int maxWidth, int maxHeight) {
        final double ratio = maxHeight / (double) maxWidth;
        final double bmRatio = bm.getHeight() / (double) bm.getWidth();
        Bitmap result = null;
        Rect rect = new Rect();
        if (ratio > bmRatio) {
            rect.right = maxWidth;
            rect.bottom = Math.round((float) bm.getHeight() * maxWidth / bm.getWidth());
        } else {
            rect.right = Math.round((float) bm.getWidth() * maxHeight / bm.getHeight());
            rect.bottom = maxHeight;
        }
        result = Bitmap.createBitmap(rect.right, rect.bottom, bm.getConfig());
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(bm, null, rect, null);
        return result;
    }

    /*
     * Decode large sized bitmap into required size.
     */
    public static Bitmap decodeSampledBitmapFromStream(InputStream is, int reqWidth,
            int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeStream(is, null, options);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth,
            int reqHeight) {
        // Raw height and width of image
        int width = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;

        // Calculate the largest inSampleSize value that is a power of 2 and keeps either
        // height and width larger than the requested height and width.
        while (width > reqWidth || height > reqHeight) {
            width >>= 1;
            height >>= 1;
            inSampleSize <<= 1;
        }

        return inSampleSize;
    }
}