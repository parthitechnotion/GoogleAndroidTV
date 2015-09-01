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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.format.DateUtils;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Tests for {@link com.android.tv.util.Utils#getDurationString}.
 * <p/>
 * This test uses deprecated flags {@link DateUtils#FORMAT_12HOUR} and
 * {@link DateUtils#FORMAT_24HOUR} to run this test independent to system's 12/24h format.
 * Note that changing system setting requires permission android.permission.WRITE_SETTINGS
 * and it should be defined in TV app, not this test.
 */
@SmallTest
public class UtilsTest_GetDurationString extends AndroidTestCase {
    // TODO: Mock Context so we can specify current time and locale for test.
    private Locale mLocale;
    private static final long DATE_2015_2_1_MS = getFeb2015InMillis(1, 0, 0);

    // All possible list for a paramter to test parameter independent result.
    private static final boolean[] PARAM_USE_SHORT_FORMAT = {false, true};

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Set locale to US
        mLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    /**
     * Return time in millis assuming that whose year is 2015 and month is Jan.
     */
    private static long getJan2015InMillis(int date, int hour, int minutes) {
        return new GregorianCalendar(
                2015, Calendar.JANUARY, date, hour, minutes).getTimeInMillis();
    }

    private static long getJan2015InMillis(int date, int hour) {
        return getJan2015InMillis(date, hour, 0);
    }

    /**
     * Return time in millis assuming that whose year is 2015 and month is Feb.
     */
    private static long getFeb2015InMillis(int date, int hour, int minutes) {
        return new GregorianCalendar(
                2015, Calendar.FEBRUARY, date, hour, minutes).getTimeInMillis();
    }

    private static long getFeb2015InMillis(int date, int hour) {
        return getFeb2015InMillis(date, hour, 0);
    }

    public void testSameDateAndTime() {
        assertEquals("3:00 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(1, 3), getFeb2015InMillis(1, 3), false,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("03:00",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(1, 3), getFeb2015InMillis(1, 3), false,
                        DateUtils.FORMAT_24HOUR));
    }

    public void testDurationWithinToday() {
        assertEquals("12:00 – 3:00 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        DATE_2015_2_1_MS, getFeb2015InMillis(1, 3), false,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("00:00 – 03:00",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        DATE_2015_2_1_MS, getFeb2015InMillis(1, 3), false,
                        DateUtils.FORMAT_24HOUR));
    }

    public void testDurationFromYesterdayToToday() {
        assertEquals("Jan 31, 3:00 AM – Feb 1, 4:00 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getJan2015InMillis(31, 3), getFeb2015InMillis(1, 4), false,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("Jan 31, 03:00 – Feb 1, 04:00",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getJan2015InMillis(31, 3), getFeb2015InMillis(1, 4), false,
                        DateUtils.FORMAT_24HOUR));
        assertEquals("1/31, 11:30 PM – 12:30 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getJan2015InMillis(31, 23, 30), getFeb2015InMillis(1, 0, 30), true,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("1/31, 23:30 – 00:30",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getJan2015InMillis(31, 23, 30), getFeb2015InMillis(1, 0, 30), true,
                        DateUtils.FORMAT_24HOUR));
    }

    public void testDurationFromTodayToTomorrow() {
        assertEquals("Feb 1, 3:00 AM – Feb 2, 4:00 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(1, 3), getFeb2015InMillis(2, 4), false,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("Feb 1, 03:00 – Feb 2, 04:00",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(1, 3), getFeb2015InMillis(2, 4), false,
                        DateUtils.FORMAT_24HOUR));
        assertEquals("2/1, 3:00 AM – 2/2, 4:00 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(1, 3), getFeb2015InMillis(2, 4), true,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("2/1, 03:00 – 2/2, 04:00",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(1, 3), getFeb2015InMillis(2, 4), true,
                        DateUtils.FORMAT_24HOUR));

        assertEquals("Feb 1, 11:30 PM – Feb 2, 12:30 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(1, 23, 30), getFeb2015InMillis(2, 0, 30), false,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("Feb 1, 23:30 – Feb 2, 00:30",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(1, 23, 30), getFeb2015InMillis(2, 0, 30), false,
                        DateUtils.FORMAT_24HOUR));
        assertEquals("11:30 PM – 12:30 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(1, 23, 30), getFeb2015InMillis(2, 0, 30), true,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("23:30 – 00:30",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(1, 23, 30), getFeb2015InMillis(2, 0, 30), true,
                        DateUtils.FORMAT_24HOUR));
    }

    public void testDurationWithinTomorrow() {
        assertEquals("Feb 2, 2:00 – 4:00 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(2, 2), getFeb2015InMillis(2, 4), false,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("Feb 2, 02:00 – 04:00",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(2, 2), getFeb2015InMillis(2, 4), false,
                        DateUtils.FORMAT_24HOUR));
        assertEquals("2/2, 2:00 – 4:00 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(2, 2), getFeb2015InMillis(2, 4), true,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("2/2, 02:00 – 04:00",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(2, 2), getFeb2015InMillis(2, 4), true,
                        DateUtils.FORMAT_24HOUR));
    }

    public void testStartOfDay() {
        assertEquals("12:00 – 1:00 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        DATE_2015_2_1_MS, getFeb2015InMillis(1, 1), false,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("00:00 – 01:00",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        DATE_2015_2_1_MS, getFeb2015InMillis(1, 1), false,
                        DateUtils.FORMAT_24HOUR));

        assertEquals("Feb 2, 12:00 – 1:00 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(2, 0), getFeb2015InMillis(2, 1), false,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("Feb 2, 00:00 – 01:00",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(2, 0), getFeb2015InMillis(2, 1), false,
                        DateUtils.FORMAT_24HOUR));
        assertEquals("2/2, 12:00 – 1:00 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(2, 0), getFeb2015InMillis(2, 1), true,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("2/2, 00:00 – 01:00",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(2, 0), getFeb2015InMillis(2, 1), true,
                        DateUtils.FORMAT_24HOUR));
    }

    public void testEndOfDay() {
        for (boolean useShortFormat : PARAM_USE_SHORT_FORMAT) {
            assertEquals("11:00 PM – 12:00 AM",
                    Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                            getFeb2015InMillis(1, 23), getFeb2015InMillis(2, 0), useShortFormat,
                            DateUtils.FORMAT_12HOUR));
            assertEquals("23:00 – 00:00",
                    Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                            getFeb2015InMillis(1, 23), getFeb2015InMillis(2, 0), useShortFormat,
                            DateUtils.FORMAT_24HOUR));
        }

        assertEquals("Feb 2, 11:00 PM – 12:00 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(2, 23), getFeb2015InMillis(3, 0), false,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("Feb 2, 23:00 – 00:00",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(2, 23), getFeb2015InMillis(3, 0), false,
                        DateUtils.FORMAT_24HOUR));
        assertEquals("2/2, 11:00 PM – 12:00 AM",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(2, 23), getFeb2015InMillis(3, 0), true,
                        DateUtils.FORMAT_12HOUR));
        assertEquals("2/2, 23:00 – 00:00",
                Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                        getFeb2015InMillis(2, 23), getFeb2015InMillis(3, 0), true,
                        DateUtils.FORMAT_24HOUR));
    }

    public void testMidnight() {
        for (boolean useShortFormat : PARAM_USE_SHORT_FORMAT) {
            assertEquals("12:00 AM",
                    Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                            DATE_2015_2_1_MS, DATE_2015_2_1_MS, useShortFormat,
                            DateUtils.FORMAT_12HOUR));
            assertEquals("00:00",
                    Utils.getDurationString(getContext(), DATE_2015_2_1_MS,
                            DATE_2015_2_1_MS, DATE_2015_2_1_MS, useShortFormat,
                            DateUtils.FORMAT_24HOUR));
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        // Revive system locale.
        Locale.setDefault(mLocale);
    }
}
