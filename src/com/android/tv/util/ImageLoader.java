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

package com.android.tv.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.android.tv.R;
import com.android.tv.util.BitmapUtils.ScaledBitmapInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * This class wraps up completing some arbitrary long running work when loading a bitmap. It
 * handles things like using a memory cache, running the work in a background thread.
 */
public final class ImageLoader {
    private static final String TAG = "ImageLoader";
    private static final boolean DEBUG = false;

    /**
     * Interface definition for a callback to be invoked when image loading is finished.
     */
    @UiThread
    public interface ImageLoaderCallback {
        /**
         * Called when bitmap is loaded.
         */
        void onBitmapLoaded(@Nullable Bitmap bitmap);
    }

    private static final Map<String, LoadBitmapTask> sPendingListMap = new HashMap<>();

    /**
     * Preload a bitmap image into the cache.
     *
     * <p>Not to make heavy CPU load, AsyncTask.SERIAL_EXECUTOR is used for the image loading.
     */
    @UiThread
    public static void prefetchBitmap(Context context, String uriString,
            int maxWidth, int maxHeight) {
        if (DEBUG) {
            Log.d(TAG, "prefetchBitmap() " + uriString);
        }
        doLoadBitmap(context, uriString, maxWidth, maxHeight, null, AsyncTask.SERIAL_EXECUTOR);
    }

    /**
     * Load a bitmap image with the cache using a ContentResolver.
     *
     * <p><b>Note</b> that the callback will be called synchronously if the bitmap already is in
     * the cache.
     *
     * @return {@code true} if the load is complete and the callback is executed.
     */
    @UiThread
    public static boolean loadBitmap(Context context, String uriString,
            ImageLoaderCallback callback) {
        return loadBitmap(context, uriString, Integer.MAX_VALUE, Integer.MAX_VALUE, callback);
    }

    /**
     * Load a bitmap image with the cache and resize it with given params.
     *
     * <p><b>Note</b> that the callback will be called synchronously if the bitmap already is in
     * the cache.
     *
     * @return {@code true} if the load is complete and the callback is executed.
     */
    @UiThread
    public static boolean loadBitmap(Context context, String uriString, int maxWidth, int maxHeight,
            ImageLoaderCallback callback) {
        if (DEBUG) {
            Log.d(TAG, "loadBitmap() " + uriString);
        }
        return doLoadBitmap(context, uriString, maxWidth, maxHeight, callback,
                AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static boolean doLoadBitmap(Context context, String uriString,
            int maxWidth, int maxHeight, ImageLoaderCallback callback, Executor executor) {
        // Check the cache before creating a Task.  The cache will be checked again in doLoadBitmap
        // but checking a cache is much cheaper than creating an new task.
        ImageCache imageCache = ImageCache.getInstance();
        ScaledBitmapInfo bitmapInfo = imageCache.get(uriString);
        if (bitmapInfo != null && !bitmapInfo.needToReload(maxWidth, maxHeight)) {
            if (callback != null) {
                callback.onBitmapLoaded(bitmapInfo.bitmap);
            }
            return true;
        }
        return doLoadBitmap(callback, executor,
                new LoadBitmapFromUriTask(context, imageCache, uriString, maxWidth, maxHeight));
    }

    /**
     * Load a bitmap image with the cache and resize it with given params.
     *
     * <p>The LoadBitmapTask will be executed on a non ui thread.
     *
     * @return {@code true} if the load is complete and the callback is executed.
     */
    @UiThread
    public static boolean loadBitmap(ImageLoaderCallback callback, LoadBitmapTask loadBitmapTask) {
        if (DEBUG) {
            Log.d(TAG, "loadBitmap() " + loadBitmapTask);
        }
        return doLoadBitmap(callback, AsyncTask.THREAD_POOL_EXECUTOR, loadBitmapTask);
    }

    /**
     * @return {@code true} if the load is complete and the callback is executed.
     */
    private static boolean doLoadBitmap(ImageLoaderCallback callback, Executor executor,
            LoadBitmapTask loadBitmapTask) {
        ScaledBitmapInfo bitmapInfo = loadBitmapTask.getFromCache();
        boolean needToReload = loadBitmapTask.isReloadNeeded();
        if (bitmapInfo != null && !needToReload) {
            if (callback != null) {
                callback.onBitmapLoaded(bitmapInfo.bitmap);
            }
            return true;
        }
        LoadBitmapTask existingTask = sPendingListMap.get(loadBitmapTask.getKey());
        if (existingTask != null && !loadBitmapTask.isReloadNeeded(existingTask) ) {
            // The image loading is already scheduled and is large enough.
            if (callback != null) {
                existingTask.mCallbacks.add(callback);
            }
        } else {
            if (callback != null) {
                loadBitmapTask.mCallbacks.add(callback);
            }
            sPendingListMap.put(loadBitmapTask.getKey(), loadBitmapTask);
            try {
                loadBitmapTask.executeOnExecutor(executor);
            } catch (RejectedExecutionException e) {
                Log.e(TAG, "Failed to create new image loader", e);
                sPendingListMap.remove(loadBitmapTask.getKey());
            }
        }
        return false;
    }

/**
 * Loads and caches a a possibly scaled down version of a bitmap.
 *
 * <p>Implement {@link #doGetBitmapInBackground()} to to the actual loading.
 */
    public static abstract class LoadBitmapTask extends AsyncTask<Void, Void, ScaledBitmapInfo> {
        protected final int mMaxWidth;
        protected final int mMaxHeight;
        private final List<ImageLoader.ImageLoaderCallback> mCallbacks = new ArrayList<>();
        private final ImageCache mImageCache;
        private final String mKey;

        /**
         * Returns true if a reload is needed compared to current results in the cache or false if
         * there is not match in the cache.
         */
        private boolean isReloadNeeded() {
            ScaledBitmapInfo bitmapInfo = getFromCache();
            boolean needToReload = bitmapInfo != null && bitmapInfo
                    .needToReload(mMaxWidth, mMaxHeight);
            if (DEBUG) {
                if (needToReload) {
                    Log.d(TAG, "Bitmap needs to be reloaded. {originalWidth="
                            + bitmapInfo.bitmap.getWidth() + ", originalHeight="
                            + bitmapInfo.bitmap.getHeight() + ", reqWidth=" + mMaxWidth
                            + ", reqHeight="
                            + mMaxHeight);
                }
            }
            return needToReload;
        }

        /**
         * Checks if a reload would be needed if the results of other was available.
         */
        private boolean isReloadNeeded(LoadBitmapTask other) {
            return mMaxHeight >= other.mMaxHeight * 2 || mMaxWidth >= other.mMaxWidth * 2;
        }

        @Nullable
        public final ScaledBitmapInfo getFromCache() {
            return mImageCache.get(mKey);
        }

        public LoadBitmapTask(ImageCache imageCache, String key, int maxHeight, int maxWidth) {
            if (maxWidth == 0 || maxHeight == 0) {
                throw new IllegalArgumentException("Image size should not be 0. {width=" + maxWidth
                        + ", height=" + maxHeight + "}");
            }
            mKey = key;
            mImageCache = imageCache;
            mMaxHeight = maxHeight;
            mMaxWidth = maxWidth;
        }

        /**
         * Loads the bitmap returning a possibly scaled down version.
         */
        @Nullable
        @WorkerThread
        public abstract ScaledBitmapInfo doGetBitmapInBackground();

        @Override
        @Nullable
        public final ScaledBitmapInfo doInBackground(Void... params) {
            ScaledBitmapInfo bitmapInfo = getFromCache();
            if (bitmapInfo != null && !isReloadNeeded()) {
                return bitmapInfo;
            }
            bitmapInfo = doGetBitmapInBackground();
            if (bitmapInfo != null) {
                mImageCache.putIfNeeded(bitmapInfo);
            }
            return bitmapInfo;
        }

        @Override
        public final void onPostExecute(ScaledBitmapInfo scaledBitmapInfo) {
            if (ImageLoader.DEBUG) {
                Log.d(ImageLoader.TAG, "Bitmap is loaded " + mKey);
            }
            for (ImageLoader.ImageLoaderCallback callback : mCallbacks) {
                callback.onBitmapLoaded(scaledBitmapInfo == null ? null : scaledBitmapInfo.bitmap);
            }
            ImageLoader.sPendingListMap.remove(mKey);
        }

        public final String getKey() {
            return mKey;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + "(" + mKey + " "
                    + mMaxWidth + "x" + mMaxHeight + ")";
        }
    }

    private static final class LoadBitmapFromUriTask extends LoadBitmapTask {
        private final Context mContext;
        private LoadBitmapFromUriTask(Context context, ImageCache imageCache, String uriString,
                int maxWidth, int maxHeight) {
            super(imageCache, uriString, maxHeight, maxWidth);
            mContext = context;
        }

        @Override
        @Nullable
        public final ScaledBitmapInfo doGetBitmapInBackground() {
            return BitmapUtils
                    .decodeSampledBitmapFromUriString(mContext, getKey(), mMaxWidth, mMaxHeight);
        }
    }

    /**
     * Loads and caches the logo for a given {@link TvInputInfo}
     */
    public static final class LoadTvInputLogoTask extends LoadBitmapTask {
        private final TvInputInfo mInfo;
        private final Context mContext;

        public LoadTvInputLogoTask(Context context, ImageCache cache, TvInputInfo info) {
            super(cache,
                    info.getId() + "-logo",
                    context.getResources()
                            .getDimensionPixelSize(R.dimen.channel_banner_input_logo_size),
                    context.getResources()
                            .getDimensionPixelSize(R.dimen.channel_banner_input_logo_size)
            );
            mInfo = info;
            mContext = context;
        }

        @Nullable
        @Override
        public ScaledBitmapInfo doGetBitmapInBackground() {
            Drawable drawable = mInfo.loadIcon(mContext);
            if (!(drawable instanceof BitmapDrawable)) {
                return null;
            }
            Bitmap original = ((BitmapDrawable) drawable).getBitmap();
            if (original == null) {
                return null;
            }
            return BitmapUtils.createScaledBitmapInfo(getKey(), original, mMaxWidth, mMaxHeight);
        }
    }

    private ImageLoader() {
    }
}
