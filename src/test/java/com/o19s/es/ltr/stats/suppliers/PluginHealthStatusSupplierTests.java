/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.o19s.es.ltr.stats.suppliers;

import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.junit.Before;

public class PluginHealthStatusSupplierTests extends OpenSearchIntegTestCase {
    private PluginHealthStatusSupplier pluginHealthStatusSupplier;

    @Before
    public void setup() {
        pluginHealthStatusSupplier =
                new PluginHealthStatusSupplier(clusterService(), new IndexNameExpressionResolver(new ThreadContext(Settings.EMPTY)));
    }

    public void testPluginHealthStatusNoLtrStore() {
        assertEquals("green", pluginHealthStatusSupplier.get());
    }

    public void testPluginHealthStatus() {
        createIndex(IndexFeatureStore.DEFAULT_STORE,
                IndexFeatureStore.DEFAULT_STORE + "_custom1",
                IndexFeatureStore.DEFAULT_STORE + "_custom2");
        flush();
        String status = pluginHealthStatusSupplier.get();
        assertTrue(status.equals("green") || status.equals("yellow"));
    }
}
