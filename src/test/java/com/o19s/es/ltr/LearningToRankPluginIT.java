/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr;

import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.opensearch.test.rest.OpenSearchRestTestCase;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import java.io.IOException;

public class LearningToRankPluginIT extends OpenSearchRestTestCase {
    public void testPluginInstalled() throws IOException, ParseException {
        Response response = client().performRequest(new Request("GET", "/_cat/plugins"));
        String body = EntityUtils.toString(response.getEntity());

        logger.info("response body: {}", body);
        assertNotNull(body);
        assertTrue(body.contains("search-processor"));
    }
}
