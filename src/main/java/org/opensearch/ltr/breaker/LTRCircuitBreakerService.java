/*
 *   Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package org.opensearch.ltr.breaker;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ltr.settings.LTRSettings;
import org.opensearch.monitor.jvm.JvmService;

/**
 * Class {@code LTRCircuitBreakerService} provide storing, retrieving circuit breakers functions.
 *
 * This service registers internal system breakers and provide API for users to register their own breakers.
 */
public class LTRCircuitBreakerService {

    private final ConcurrentMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final JvmService jvmService;

    private static final Logger logger = LogManager.getLogger(LTRCircuitBreakerService.class);

    /**
     * Constructor.
     *
     * @param jvmService jvm info
     */
    public LTRCircuitBreakerService(JvmService jvmService) {
        this.jvmService = jvmService;
    }

    public void registerBreaker(String name, CircuitBreaker breaker) {
        breakers.putIfAbsent(name, breaker);
    }

    public void unregisterBreaker(String name) {
        if (name == null) {
            return;
        }

        breakers.remove(name);
    }

    public void clearBreakers() {
        breakers.clear();
    }

    public CircuitBreaker getBreaker(String name) {
        return breakers.get(name);
    }

    /**
     * Initialize circuit breaker service.
     *
     * Register memory breaker by default.
     *
     * @return LTRCircuitBreakerService
     */
    public LTRCircuitBreakerService init() {
        // Register memory circuit breaker
        registerBreaker(BreakerName.MEM.getName(), new MemoryCircuitBreaker(this.jvmService));
        logger.info("Registered memory breaker.");

        return this;
    }

    public Boolean isOpen() {
        if (!LTRSettings.isLTRBreakerEnabled()) {
            return false;
        }

        for (CircuitBreaker breaker : breakers.values()) {
            if (breaker.isOpen()) {
                return true;
            }
        }

        return false;
    }
}
