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

package org.opensearch.ltr.stats.suppliers;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.ltr.breaker.LTRCircuitBreakerService;
import org.opensearch.ltr.stats.suppliers.utils.StoreUtils;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class PluginHealthStatusSupplierTests {
    private PluginHealthStatusSupplier pluginHealthStatusSupplier;

    @Mock
    private LTRCircuitBreakerService ltrCircuitBreakerService;

    @Mock
    StoreUtils storeUtils;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        pluginHealthStatusSupplier =
                new PluginHealthStatusSupplier(storeUtils, ltrCircuitBreakerService);
    }

    @Test
    public void testStatusGreen() {
        when(ltrCircuitBreakerService.isOpen()).thenReturn(false);
        when(storeUtils.getAllLtrStoreNames()).thenReturn(Arrays.asList("store1", "store2"));
        when(storeUtils.getLtrStoreHealthStatus(Mockito.anyString())).thenReturn("green");

        assertEquals("green", pluginHealthStatusSupplier.get());
    }

    @Test
    public void testStatusYellowStoreStatusYellow() {
        when(ltrCircuitBreakerService.isOpen()).thenReturn(false);
        when(storeUtils.getAllLtrStoreNames()).thenReturn(Arrays.asList("store1", "store2"));
        when(storeUtils.getLtrStoreHealthStatus("store1")).thenReturn("green");
        when(storeUtils.getLtrStoreHealthStatus("store2")).thenReturn("yellow");
        assertEquals("yellow", pluginHealthStatusSupplier.get());
    }

    @Test
    public void testStatusRedStoreStatusRed() {
        when(ltrCircuitBreakerService.isOpen()).thenReturn(false);
        when(storeUtils.getAllLtrStoreNames()).thenReturn(Arrays.asList("store1", "store2"));
        when(storeUtils.getLtrStoreHealthStatus("store1")).thenReturn("red");
        when(storeUtils.getLtrStoreHealthStatus("store2")).thenReturn("green");

        assertEquals("red", pluginHealthStatusSupplier.get());
    }

    @Test
    public void testStatusRedCircuitBreakerOpen() {
        when(ltrCircuitBreakerService.isOpen()).thenReturn(true);
        assertEquals("red", pluginHealthStatusSupplier.get());
    }
}
