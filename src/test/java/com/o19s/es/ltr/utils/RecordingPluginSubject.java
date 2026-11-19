/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.utils;

import java.security.Principal;

import org.opensearch.common.CheckedRunnable;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.identity.PluginSubject;

/**
 * Stands in for the plugin subject in tests. Stashes and restores around its body as NoopPluginSubject
 * and SecurePluginSubject do, and puts its marker in the stashed context so a test can tell which
 * subject, if any, an action ran as.
 */
public final class RecordingPluginSubject implements PluginSubject {

    /** Transient header that holds the marker while the body runs. */
    public static final String MARKER_TRANSIENT = "subject.marker";

    private final ThreadContext threadContext;
    private final String marker;

    /**
     * Creates a subject that marks the context it runs in.
     *
     * @param threadContext the context the plugin client uses
     * @param marker the value put under {@link #MARKER_TRANSIENT} while the body runs
     */
    public RecordingPluginSubject(ThreadContext threadContext, String marker) {
        this.threadContext = threadContext;
        this.marker = marker;
    }

    @Override
    public Principal getPrincipal() {
        return () -> "plugin:test";
    }

    @Override
    public <E extends Exception> void runAs(CheckedRunnable<E> runnable) throws E {
        try (ThreadContext.StoredContext ignored = threadContext.stashContext()) {
            threadContext.putTransient(MARKER_TRANSIENT, marker);
            runnable.run();
        }
    }
}
