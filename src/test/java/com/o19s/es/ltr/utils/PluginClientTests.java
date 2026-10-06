/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchAction;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.CheckedRunnable;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.identity.Subject;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class PluginClientTests extends OpenSearchTestCase {

    private static final String CALLER_HEADER = "caller";
    private static final String SUBJECT_HEADER = "ran_as";

    private ThreadPool threadPool;
    private Client delegate;
    private PluginClient pluginClient;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        threadPool = new TestThreadPool(getTestName());
        delegate = mock(Client.class);
        when(delegate.settings()).thenReturn(Settings.EMPTY);
        when(delegate.threadPool()).thenReturn(threadPool);
        pluginClient = new PluginClient(delegate);
    }

    @Override
    public void tearDown() throws Exception {
        ThreadPool.terminate(threadPool, 10, TimeUnit.SECONDS);
        super.tearDown();
    }

    public void testRunsAsTheSubjectAndGivesTheListenerBackTheCallersContext() {
        ThreadContext threadContext = threadPool.getThreadContext();
        AtomicReference<ActionListener<SearchResponse>> deferredListener = new AtomicReference<>();
        AtomicReference<String> headerSeenByDelegate = new AtomicReference<>();
        captureListener(deferredListener, () -> headerSeenByDelegate.set(threadContext.getHeader(SUBJECT_HEADER)));

        pluginClient.setSubject(new StashingSubject(threadContext));

        AtomicReference<String> headerSeenByListener = new AtomicReference<>();
        ThreadContext.StoredContext callerContext = threadContext.stashContext();
        threadContext.putHeader(CALLER_HEADER, "alice");
        pluginClient
            .execute(
                SearchAction.INSTANCE,
                new SearchRequest(),
                ActionListener.wrap(r -> headerSeenByListener.set(threadContext.getHeader(CALLER_HEADER)), e -> fail(e.getMessage()))
            );
        callerContext.close();

        assertEquals("plugin", headerSeenByDelegate.get());
        assertNull("the caller's context is gone by the time the response arrives", threadContext.getHeader(CALLER_HEADER));

        deferredListener.get().onResponse(mock(SearchResponse.class));
        assertEquals("alice", headerSeenByListener.get());
    }

    public void testSynchronousFailureIsReportedThroughTheListener() {
        pluginClient.setSubject(new FailingSubject());

        AtomicReference<Exception> failure = new AtomicReference<>();
        pluginClient
            .execute(SearchAction.INSTANCE, new SearchRequest(), ActionListener.wrap(r -> fail("expected a failure"), failure::set));

        assertNotNull("the failure has to reach the listener, not be thrown at the caller", failure.get());
        assertEquals("no", failure.get().getMessage());
    }

    public void testExecutingWithoutASubjectIsRejected() {
        IllegalStateException e = expectThrows(
            IllegalStateException.class,
            () -> pluginClient
                .execute(SearchAction.INSTANCE, new SearchRequest(), ActionListener.wrap(r -> fail("unexpected"), e2 -> fail("unexpected")))
        );
        assertEquals("PluginClient is not initialized with a subject.", e.getMessage());
    }

    @SuppressWarnings("unchecked")
    private void captureListener(AtomicReference<ActionListener<SearchResponse>> sink, Runnable onCall) {
        doAnswer(invocation -> {
            onCall.run();
            sink.set((ActionListener<SearchResponse>) invocation.getArgument(2));
            return null;
        }).when(delegate).execute(any(ActionType.class), any(ActionRequest.class), any(ActionListener.class));
    }

    /**
     * Mirrors the real subjects: both the security plugin's and core's no-op implementation stash the
     * thread context around the body, so they restore the caller's context when runAs returns.
     */
    private static class StashingSubject implements Subject {
        private final ThreadContext threadContext;

        StashingSubject(ThreadContext threadContext) {
            this.threadContext = threadContext;
        }

        @Override
        public Principal getPrincipal() {
            return () -> "plugin";
        }

        @Override
        public <E extends Exception> void runAs(CheckedRunnable<E> runnable) throws E {
            try (ThreadContext.StoredContext ignored = threadContext.stashContext()) {
                threadContext.putHeader(SUBJECT_HEADER, "plugin");
                runnable.run();
            }
        }
    }

    private static class FailingSubject implements Subject {
        @Override
        public Principal getPrincipal() {
            return () -> "plugin";
        }

        @Override
        public <E extends Exception> void runAs(CheckedRunnable<E> runnable) {
            throw new IllegalStateException("no");
        }
    }
}
