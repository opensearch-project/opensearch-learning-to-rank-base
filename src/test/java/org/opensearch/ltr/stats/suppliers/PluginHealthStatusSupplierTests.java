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
