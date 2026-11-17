/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.o19s.es.ltr;

import java.io.IOException;

import org.apache.hc.core5.http.HttpHost;
import org.junit.BeforeClass;
import org.opensearch.client.Request;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.common.settings.Settings;
import org.opensearch.test.rest.OpenSearchRestTestCase;

/** Verifies that the REST suite runs against a cluster that actually enforces authentication. */
public class SecurityEnabledIT extends OpenSearchRestTestCase {
    @BeforeClass
    public static void requireSecurity() {
        assumeTrue("Security-enabled test cluster required", Boolean.getBoolean("security.enabled"));
    }

    @Override
    protected String getProtocol() {
        return "https";
    }

    @Override
    protected boolean preserveIndicesUponCompletion() {
        return true;
    }

    @Override
    protected RestClient buildClient(Settings settings, HttpHost[] hosts) throws IOException {
        RestClientBuilder builder = RestClient.builder(hosts);
        LtrQueryClientYamlTestSuiteIT.configureHttpsClient(builder, settings);
        return builder.build();
    }

    public void testSecurityIsEnabled() throws IOException {
        var response = client().performRequest(new Request("GET", "/_plugins/_security/authinfo"));
        assertEquals("admin", entityAsMap(response).get("user_name"));
        // An explicit invalid Authorization header prevents the client's default credentials from being used.
        Request rejected = new Request("GET", "/_ltr");
        rejected.setOptions(rejected.getOptions().toBuilder().addHeader("Authorization", "Basic aW52YWxpZDppbnZhbGlk"));
        ResponseException failure = expectThrows(ResponseException.class, () -> client().performRequest(rejected));
        assertEquals(401, failure.getResponse().getStatusLine().getStatusCode());
    }
}
