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

import static com.android.tv.testing.dvr.RecordingTestUtils.createTestRecordingWithIdAndPeriod;
import static com.android.tv.testing.dvr.RecordingTestUtils.normalizePriority;

import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Range;

import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.testing.dvr.RecordingTestUtils;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link RecordingTest}
 */
@SmallTest
public class RecordingTest extends TestCase {
    public void testIsOverLapping() throws Exception {
        Recording r = createTestRecordingWithIdAndPeriod(1, 10L, 20L);
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

    public void testBuildProgram() {
        Channel c = new Channel.Builder().build();
        Program p = new Program.Builder().build();
        Recording actual = Recording.builder(c, p).build();
        assertEquals("type", Recording.TYPE_PROGRAM, actual.getType());
    }

    public void testBuildTime() {
        Recording actual = createTestRecordingWithIdAndPeriod(1, 10L, 20L);
        assertEquals("type", Recording.TYPE_TIMED, actual.getType());
    }

    public void testBuildFrom() {
        Recording expected = createTestRecordingWithIdAndPeriod(1, 10L, 20L);
        Recording actual = Recording.buildFrom(expected).build();
        RecordingTestUtils.assertRecordingEquals(expected, actual);
    }

    public void testBuild_priority() {
        Recording a = normalizePriority(createTestRecordingWithIdAndPeriod(1, 10L, 20L));
        Recording b = normalizePriority(createTestRecordingWithIdAndPeriod(2, 10L, 20L));
        Recording c = normalizePriority(createTestRecordingWithIdAndPeriod(3, 10L, 20L));

        // default priority
        MoreAsserts.assertContentsInOrder(sortByPriority(c,b,a), a, b, c);

        // make C preferred over B
        c = Recording.buildFrom(c).setPriority(b.getPriority() - 1).build();
        MoreAsserts.assertContentsInOrder(sortByPriority(c,b,a), a, c, b);
    }

    public Collection<Recording> sortByPriority(Recording a, Recording b, Recording c) {
        List<Recording> list = Arrays.asList(a, b, c);
        Collections.sort(list, Recording.PRIORITY_COMPARATOR);
        return list;
    }

    private void assertOverLapping(boolean expected, long lower, long upper, Recording r) {
        assertEquals("isOverlapping(Range(" + lower + "," + upper + "), recording " + r, expected,
                r.isOverLapping(new Range<Long>(lower, upper)));
    }
}
