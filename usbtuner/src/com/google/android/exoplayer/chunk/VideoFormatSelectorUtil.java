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
package com.google.android.exoplayer.chunk;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;

import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects from possible video formats.
 */
public final class VideoFormatSelectorUtil {

  /**
   * If a dimension (i.e. width or height) of a video is greater or equal to this fraction of the
   * corresponding viewport dimension, then the video is considered as filling the viewport (in that
   * dimension).
   */
  private static final float FRACTION_TO_CONSIDER_FULLSCREEN = 0.98f;

  /**
   * Chooses a suitable subset from a number of video formats, to be rendered on the device's
   * default display.
   *
   * @param context A context.
   * @param formatWrappers Wrapped formats from which to select.
   * @param allowedContainerMimeTypes An array of allowed container mime types. Null allows all
   *     mime types.
   * @param filterHdFormats True to filter HD formats. False otherwise.
   * @return An array holding the indices of the selected formats.
   * @throws DecoderQueryException
   */
  public static int[] selectVideoFormatsForDefaultDisplay(Context context,
      List<? extends FormatWrapper> formatWrappers, String[] allowedContainerMimeTypes,
      boolean filterHdFormats) throws DecoderQueryException {
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    Display display = windowManager.getDefaultDisplay();
    Point displaySize = getDisplaySize(display);
    return selectVideoFormats(formatWrappers, allowedContainerMimeTypes, filterHdFormats, true,
        displaySize.x, displaySize.y);
  }

  /**
   * Chooses a suitable subset from a number of video formats.
   * <p>
   * A format is filtered (i.e. not selected) if:
   * <ul>
   * <li>{@code allowedContainerMimeTypes} is non-null and the format does not have one of the
   *     permitted mime types.
   * <li>{@code filterHdFormats} is true and the format is HD.
   * <li>It's determined that the video decoder isn't powerful enough to decode the format.
   * <li>There exists another format of lower resolution whose resolution exceeds the maximum size
   *     in pixels that the video can be rendered within the viewport.
   * </ul>
   *
   * @param formatWrappers Wrapped formats from which to select.
   * @param allowedContainerMimeTypes An array of allowed container mime types. Null allows all
   *     mime types.
   * @param filterHdFormats True to filter HD formats. False otherwise.
   * @param orientationMayChange True if the video's orientation may change with respect to the
   *     viewport during playback.
   * @param viewportWidth The width in pixels of the viewport within which the video will be
   *     displayed. If the viewport size may change, this should be set to the maximum possible
   *     width.
   * @param viewportHeight The height in pixels of the viewport within which the video will be
   *     displayed. If the viewport size may change, this should be set to the maximum possible
   *     height.
   * @return An array holding the indices of the selected formats.
   * @throws DecoderQueryException
   */
  public static int[] selectVideoFormats(List<? extends FormatWrapper> formatWrappers,
      String[] allowedContainerMimeTypes, boolean filterHdFormats, boolean orientationMayChange,
      int viewportWidth, int viewportHeight) throws DecoderQueryException {
    int maxVideoPixelsToRetain = Integer.MAX_VALUE;
    ArrayList<Integer> selectedIndexList = new ArrayList<>();
    int maxDecodableFrameSize = MediaCodecUtil.maxH264DecodableFrameSize();

    // First pass to filter out formats that individually fail to meet the selection criteria.
    int formatWrapperCount = formatWrappers.size();
    for (int i = 0; i < formatWrapperCount; i++) {
      Format format = formatWrappers.get(i).getFormat();
      if (isFormatPlayable(format, allowedContainerMimeTypes, filterHdFormats,
          maxDecodableFrameSize)) {
        // Select the format for now. It may still be filtered in the second pass below.
        selectedIndexList.add(i);
        // Keep track of the number of pixels of the selected format whose resolution is the
        // smallest to exceed the maximum size at which it can be displayed within the viewport.
        // We'll discard formats of higher resolution in a second pass.
        if (format.width > 0 && format.height > 0) {
          Point maxVideoSizeInViewport = getMaxVideoSizeInViewport(orientationMayChange,
              viewportWidth, viewportHeight, format.width, format.height);
          int videoPixels = format.width * format.height;
          if (format.width >= (int) (maxVideoSizeInViewport.x * FRACTION_TO_CONSIDER_FULLSCREEN)
              && format.height >= (int) (maxVideoSizeInViewport.y * FRACTION_TO_CONSIDER_FULLSCREEN)
              && videoPixels < maxVideoPixelsToRetain) {
            maxVideoPixelsToRetain = videoPixels;
          }
        }
      }
    }

    // Second pass to filter out formats that exceed maxVideoPixelsToRetain. These formats are have
    // unnecessarily high resolution given the size at which the video will be displayed within the
    // viewport.
    for (int i = selectedIndexList.size() - 1; i >= 0; i--) {
      Format format = formatWrappers.get(i).getFormat();
      if (format.width > 0 && format.height > 0
          && format.width * format.height > maxVideoPixelsToRetain) {
        selectedIndexList.remove(i);
      }
    }

    return Util.toArray(selectedIndexList);
  }

  /**
   * Determines whether an individual format is playable, given an array of allowed container types,
   * whether HD formats should be filtered and a maximum decodable frame size in pixels.
   */
  private static boolean isFormatPlayable(Format format, String[] allowedContainerMimeTypes,
      boolean filterHdFormats, int maxDecodableFrameSize) {
    if (allowedContainerMimeTypes != null
        && !Util.contains(allowedContainerMimeTypes, format.mimeType)) {
      // Filtering format based on its container mime type.
      return false;
    }
    if (filterHdFormats && (format.width >= 1280 || format.height >= 720)) {
      // Filtering format because it's HD.
      return false;
    }
    if (format.width > 0 && format.height > 0) {
      // TODO: Use MediaCodecUtil.isSizeAndRateSupportedV21 on API levels >= 21 if we know the
      // mimeType of the media samples within the container. Remove the assumption that we're
      // dealing with H.264.
      if (format.width * format.height > maxDecodableFrameSize) {
        // Filtering stream that device cannot play
        return false;
      }
    }
    return true;
  }

  /**
   * Given viewport dimensions and video dimensions, computes the maximum size of the video as it
   * will be rendered to fit inside of the viewport.
   */
  private static Point getMaxVideoSizeInViewport(boolean orientationMayChange, int viewportWidth,
      int viewportHeight, int videoWidth, int videoHeight) {
    if (orientationMayChange && (videoWidth > videoHeight) != (viewportWidth > viewportHeight)) {
      // Rotation is allowed, and the video will be larger in the rotated viewport.
      int tempViewportWidth = viewportWidth;
      viewportWidth = viewportHeight;
      viewportHeight = tempViewportWidth;
    }

    if (videoWidth * viewportHeight >= videoHeight * viewportWidth) {
      // Horizontal letter-boxing along top and bottom.
      return new Point(viewportWidth, Util.ceilDivide(viewportWidth * videoHeight, videoWidth));
    } else {
      // Vertical letter-boxing along edges.
      return new Point(Util.ceilDivide(viewportHeight * videoWidth, videoHeight), viewportHeight);
    }
  }

  private static Point getDisplaySize(Display display) {
    Point displaySize = new Point();
    if (Util.SDK_INT >= 17) {
      getDisplaySizeV17(display, displaySize);
    } else if (Util.SDK_INT >= 16) {
      getDisplaySizeV16(display, displaySize);
    } else {
      getDisplaySizeV9(display, displaySize);
    }
    return displaySize;
  }

  @TargetApi(17)
  private static void getDisplaySizeV17(Display display, Point outSize) {
    display.getRealSize(outSize);
  }

  @TargetApi(16)
  private static void getDisplaySizeV16(Display display, Point outSize) {
    display.getSize(outSize);
  }

  @SuppressWarnings("deprecation")
  private static void getDisplaySizeV9(Display display, Point outSize) {
    outSize.x = display.getWidth();
    outSize.y = display.getHeight();
  }

  private VideoFormatSelectorUtil() {}

}
