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

import java.util.Map;
import java.util.function.Supplier;


public class ExtraLoggingSupplier implements Supplier<Map<String,Object>> {
    protected Supplier<Map<String,Object>> supplier;

    public void setSupplier(Supplier<Map<String,Object>> supplier) {
        this.supplier = supplier;
    }

    /**
     * Return a Map to add additional information to be returned when logging feature values.
     *
     * This Map will only be non-null during the LoggingFetchSubPhase.
     */
    @Override
    public Map<String, Object> get() {
        if (supplier != null) {
            return supplier.get();
        }
        return null;
    }
}