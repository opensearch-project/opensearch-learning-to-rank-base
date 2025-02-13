/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.rest;

import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;

import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;

public abstract class FeatureStoreBaseRestHandler extends BaseRestHandler {

    protected String indexName(RestRequest request) {
        if (request.hasParam("store")) {
            return IndexFeatureStore.STORE_PREFIX + request.param("store");
        } else {
            return IndexFeatureStore.DEFAULT_STORE;
        }
    }
}
