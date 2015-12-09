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
 * limitations under the License
 */

package com.android.tv.dvr;

import android.test.suitebuilder.annotation.SmallTest;
import android.util.Range;

import com.android.tv.testing.dvr.RecordingTestUtils;

import junit.framework.TestCase;

/**
 * Tests for {@link RecordingTest}
 */
@SmallTest
public class RecordingTest extends TestCase {
    public void testIsOverLapping() throws Exception {
        Recording r = RecordingTestUtils.createTestRecordingWithPeriod(1, 10L, 20L);
        assertOverLapping(false, 1L, 9L, r);

        assertOverLapping(true, 1L, 20L, r);
        assertOverLapping(true, 1L, 10L, r);
        assertOverLapping(true, 10L, 19L, r);
        assertOverLapping(true, 10L, 20L, r);
        assertOverLapping(true, 11L, 20L, r);
        assertOverLapping(true, 11L, 21L, r);
        assertOverLapping(true, 20L, 21L, r);

        assertOverLapping(false, 21L, 29L, r);
    }

    private void assertOverLapping(boolean expected, long lower, long upper, Recording r) {
        assertEquals("isOverlapping(Range(" + lower + "," + upper + "), recording " + r, expected,
                r.isOverLapping(new Range<Long>(lower, upper)));
    }
}
