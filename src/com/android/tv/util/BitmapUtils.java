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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class BitmapUtils {
    private static final String TAG = "BitmapUtils";

    private static final int MARK_READ_LIMIT = 10 * 1024; // 10K

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
    public static Bitmap decodeSampledBitmapFromUriString(Context context, String uriString,
            int reqWidth, int reqHeight) {
        if (TextUtils.isEmpty(uriString)) {
            return null;
        }

        InputStream is = null;
        try {
            is = getInputStream(context, uriString);
            if (is == null) {
                return null;
            }

            // We doesn't trust TIS to provide us with proper sized image
            Bitmap bitmap = decodeSampledBitmapFromStream(is, reqWidth, reqHeight);
            if (bitmap != null) {
                return bitmap;
            }

            closeInputStream(is);
            is = getInputStream(context, uriString);
            if (is == null) {
                return null;
            }
            return BitmapFactory.decodeStream(is);
        } finally {
            closeInputStream(is);
        }
    }

    /*
     * Decode large sized bitmap into required size.
     * If it returns null, the InputStream should be closed and re-opened.
     */
    public static Bitmap decodeSampledBitmapFromStream(InputStream is, int reqWidth,
            int reqHeight) {
        // The input stream is read two times, so BufferedInputStream which supports marking should
        // be used.
        BufferedInputStream bis = new BufferedInputStream(is);
        // 10K is the sufficient for the image header, because only the image header will be read
        // at the first time.
        bis.mark(MARK_READ_LIMIT);

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(bis, null, options);

        // Reset the input stream to read from the start.
        try {
            bis.reset();
        } catch (IOException e) {
            Log.i(TAG, "Failed to reset input stream.", e);
            return null;
        }

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeStream(bis, null, options);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth,
            int reqHeight) {
        // Raw height and width of image
        // They are shifted right by one bit which causes an effect that inSampleSize is shifted
        // left by one bit.
        int width = options.outWidth >> 1;
        int height = options.outHeight >> 1;
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

    private static InputStream getInputStream(Context context, String uriString) {
        try {
            return new URL(uriString).openStream();
        } catch (MalformedURLException e) {
            try {
                return context.getContentResolver().openInputStream(Uri.parse(uriString));
            } catch (FileNotFoundException ex) {
                Log.i(TAG, "Unable to load uri: " + uriString);
            }
        } catch (IOException e) {
            Log.i(TAG, "Failed to open stream: " + uriString);
        }
        return null;
    }

    private static void closeInputStream(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                // Does nothing.
            }
        }
    }
}