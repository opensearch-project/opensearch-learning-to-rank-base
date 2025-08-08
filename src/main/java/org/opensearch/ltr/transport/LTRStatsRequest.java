/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.opensearch.ltr.transport;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class LTRStatsRequest extends BaseNodesRequest<LTRStatsRequest> {

    /**
     * Key indicating all stats should be retrieved
     */
    public static final String ALL_STATS_KEY = "_all";

    private Set<String> statsToBeRetrieved;

    public LTRStatsRequest(final StreamInput in) throws IOException {
        super(in);
        statsToBeRetrieved = in.readSet(StreamInput::readString);
    }

    /**
     * Constructor
     *
     * @param nodeIds nodeIds of nodes' stats to be retrieved
     */
    public LTRStatsRequest(final String... nodeIds) {
        super(nodeIds);
        statsToBeRetrieved = new HashSet<>();
    }

    /**
     * No-args constructor for version compatibility
     */
    public LTRStatsRequest() {
        super((String[]) null);
    }

    /**
     * Adds a stat to the set of stats to be retrieved
     *
     * @param stat name of the stat
     */
    public void addStat(final String stat) {
        statsToBeRetrieved.add(stat);
    }

    /**
     * Add all stats to be retrieved
     *
     * @param statsToBeAdded set of stats to be retrieved
     */
    public void addAll(final Set<String> statsToBeAdded) {
        statsToBeRetrieved.addAll(statsToBeAdded);
    }

    /**
     * Remove all stats from retrieval set
     */
    public void clear() {
        statsToBeRetrieved.clear();
    }

    /**
     * Get the set that tracks which stats should be retrieved
     *
     * @return the set that contains the stat names marked for retrieval
     */
    public Set<String> getStatsToBeRetrieved() {
        return statsToBeRetrieved;
    }

    public void readFrom(StreamInput in) throws IOException {
        statsToBeRetrieved = in.readSet(StreamInput::readString);
    }

    @Override
    public void writeTo(final StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeStringCollection(statsToBeRetrieved);
    }
}
