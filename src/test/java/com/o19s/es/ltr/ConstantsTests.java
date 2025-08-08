/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.o19s.es.ltr;

import static org.junit.Assert.*;

import org.junit.Test;
import org.opensearch.LegacyESVersion;
import org.opensearch.Version;

public class ConstantsTests {

    private static Version getLegacyVersion(int major, int minor, int revision, int build) {
        return Version.fromId(Version.computeLegacyID(major, minor, revision, build));
    }

    @Test
    public void testVersionCreation() {
        Version v7_7_0 = getLegacyVersion(7, 7, 0, 99);
        assertEquals(7, v7_7_0.major);
        assertEquals(7, v7_7_0.minor);
        assertEquals(0, v7_7_0.revision);
        assertEquals(99, v7_7_0.build);
    }

    @Test
    public void testVersionEquality() {
        Version v7_2_0_created = getLegacyVersion(7, 2, 0, 99);
        assertEquals(LegacyESVersion.V_7_2_0, v7_2_0_created);
        assertEquals(7020099, v7_2_0_created.id);
    }

    @Test
    public void testVersionComparison() {
        Version v6_5_0 = getLegacyVersion(6, 5, 0, 99);
        Version v7_2_0 = getLegacyVersion(7, 2, 0, 99);
        Version v7_7_0 = getLegacyVersion(7, 7, 0, 99);

        assertTrue(v6_5_0.before(v7_2_0));
        assertTrue(v7_2_0.before(v7_7_0));
        assertTrue(v7_7_0.after(v7_2_0));
        assertTrue(v7_2_0.after(v6_5_0));
    }

    @Test
    public void testLegacyVsNewVersionComparison() {
        Version v7_7_0_legacy = getLegacyVersion(7, 7, 0, 99);
        Version v2_0_0_new = Version.V_2_0_0;

        // Legacy versions should be older than new versions
        assertTrue(v7_7_0_legacy.before(v2_0_0_new));
        assertTrue(v2_0_0_new.after(v7_7_0_legacy));
        assertFalse(v7_7_0_legacy.onOrAfter(v2_0_0_new));
        assertTrue(v2_0_0_new.onOrAfter(v7_7_0_legacy));
    }
}
