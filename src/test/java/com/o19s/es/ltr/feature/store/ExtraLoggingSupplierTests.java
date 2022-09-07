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

package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.ranker.LogLtrRanker;
import org.apache.lucene.tests.util.LuceneTestCase;

import java.util.HashMap;
import java.util.Map;

public class ExtraLoggingSupplierTests extends LuceneTestCase {
    public void testGetWithConsumerNotSet() {
        ExtraLoggingSupplier supplier = new ExtraLoggingSupplier();
        assertNull(supplier.get());
    }

    public void testGetWillNullConsumerSet() {
        ExtraLoggingSupplier supplier = new ExtraLoggingSupplier();
        supplier.setSupplier(null);
        assertNull(supplier.get());
    }

    public void testGetWithSuppliedNull() {
        ExtraLoggingSupplier supplier = new ExtraLoggingSupplier();
        supplier.setSupplier(() -> null);
        assertNull(supplier.get());
    }

    public void testGetWithSuppliedMap() {
        Map<String,Object> extraLoggingMap = new HashMap<>();

        LogLtrRanker.LogConsumer consumer = new LogLtrRanker.LogConsumer() {
            @Override
            public void accept(int featureOrdinal, float score) {}

            @Override
            public Map<String,Object> getExtraLoggingMap() {
                return extraLoggingMap;
            }
        };

        ExtraLoggingSupplier supplier = new ExtraLoggingSupplier();
        supplier.setSupplier(consumer::getExtraLoggingMap);
        assertTrue(supplier.get() == extraLoggingMap);
    }
}
