/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.o19s.es.ltr;

import org.opensearch.Version;

public class Constants {

    public static final Version VERSION_2_19_0 = Version.fromString("2.19.0");

    // Legacy versions for backward compatibility
    /** Release builds use build-number 99. */
    private static final int RELEASE_BUILD = 99;
    public static final Version LEGACY_V_7_0_0 = getLegacyVersion(7, 0, 0, RELEASE_BUILD);
    public static final Version LEGACY_V_7_7_0 = getLegacyVersion(7, 7, 0, RELEASE_BUILD);

    private static Version getLegacyVersion(int major, int minor, int revision, int build) {
        return Version.fromId(Version.computeLegacyID(major, minor, revision, build));
    }
}
