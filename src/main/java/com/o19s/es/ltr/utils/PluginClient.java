/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.utils;

import org.opensearch.transport.client.Client;

/** This plugin's client, which runs feature store actions as the plugin's system subject. */
public final class PluginClient extends org.opensearch.commons.client.PluginClient {

    /**
     * Wraps a client so its actions run as the plugin subject, which is assigned later.
     *
     * @param delegate the client whose actions this wraps
     */
    public PluginClient(Client delegate) {
        super(delegate);
    }
}
