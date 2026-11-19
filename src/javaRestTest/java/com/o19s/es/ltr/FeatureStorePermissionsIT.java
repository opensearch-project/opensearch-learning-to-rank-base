/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.common.settings.Settings;
import org.opensearch.test.rest.OpenSearchRestTestCase;

/**
 * Checks, with the Security plugin installed, which permissions the feature store APIs need from the caller: the LTR
 * actions the caller invokes, and read access to the index a {@code validation} block names. The store index itself
 * is read and written by the plugin.
 */
public class FeatureStorePermissionsIT extends OpenSearchRestTestCase {

    private static final String PASSWORD = "Ltr-Permissions-Test-9!";
    private static final String VALIDATION_INDEX = "ltr_validation";

    /** LTR actions and read access to the validation index. */
    private static final String WRITER = "ltr_writer";
    /** LTR actions, but no access to the validation index. */
    private static final String WRITER_WITHOUT_READ = "ltr_writer_without_read";
    /** Only the store list action. */
    private static final String LISTER = "ltr_lister";
    /** Read access to the validation index, and no LTR actions. */
    private static final String NO_LTR = "ltr_none";

    private static final String VALIDATION = "\"validation\":{\"index\":\"" + VALIDATION_INDEX + "\",\"params\":{\"q\":\"text\"}}";
    private static final String FEATURE = "{\"name\":\"f1\",\"params\":[\"q\"],\"template\":{\"match\":{\"field\":\"{{q}}\"}}}";

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

    @Before
    public void createUsersAndStore() throws IOException {
        String readValidationIndex = "{\"index_patterns\":[\""
            + VALIDATION_INDEX
            + "\"],\"allowed_actions\":[\"indices:data/read/search*\"]}";
        createUser(WRITER, "{\"cluster_permissions\":[\"cluster:admin/ltr/*\"],\"index_permissions\":[" + readValidationIndex + "]}");
        createUser(WRITER_WITHOUT_READ, "{\"cluster_permissions\":[\"cluster:admin/ltr/*\"]}");
        createUser(LISTER, "{\"cluster_permissions\":[\"cluster:admin/ltr/featurestore/list\"]}");
        createUser(NO_LTR, "{\"index_permissions\":[" + readValidationIndex + "]}");
        for (String user : new String[] { WRITER, WRITER_WITHOUT_READ, LISTER, NO_LTR }) {
            // Each request below is sent with this user's credentials, never with the admin's.
            assertEquals(user, entityAsMap(as(user, "GET", "/_plugins/_security/authinfo", null)).get("user_name"));
        }

        Request index = new Request("PUT", "/" + VALIDATION_INDEX + "/_doc/1");
        index.addParameter("refresh", "true");
        index.setJsonEntity("{\"field\":\"some text\"}");
        adminClient().performRequest(index);
        adminClient().performRequest(new Request("PUT", "/_ltr"));
    }

    @After
    public void deleteStore() throws IOException {
        Request deleteStore = new Request("DELETE", "/_ltr");
        deleteStore.addParameter("ignore", "404");
        adminClient().performRequest(deleteStore);
        Request deleteIndex = new Request("DELETE", "/" + VALIDATION_INDEX);
        deleteIndex.addParameter("ignore_unavailable", "true");
        adminClient().performRequest(deleteIndex);
    }

    public void testValidatedWritesSucceedWithReadOnTheValidationIndex() throws IOException {
        assertStatus(
            201,
            as(WRITER, "PUT", "/_ltr/_featureset/validated", "{\"featureset\":{\"features\":[" + FEATURE + "]}," + VALIDATION + "}")
        );
        assertStatus(201, as(WRITER, "PUT", "/_ltr/_feature/f1", "{\"feature\":" + FEATURE + "}"));
        assertStatus(201, as(WRITER, "POST", "/_ltr/_featureset/added/_addfeatures/f1", "{" + VALIDATION + "}"));
        String model = "{\"model\":{\"name\":\"model\",\"model\":{\"type\":\"model/linear\",\"definition\":{\"f1\":1.0}}},"
            + VALIDATION
            + "}";
        assertStatus(201, as(WRITER, "POST", "/_ltr/_featureset/validated/_createmodel", model));
    }

    public void testValidatedWritesAreDeniedWithoutReadOnTheValidationIndex() throws IOException {
        assertDeniedForCallerSearch(
            as(
                WRITER_WITHOUT_READ,
                "PUT",
                "/_ltr/_featureset/validated",
                "{\"featureset\":{\"features\":[" + FEATURE + "]}," + VALIDATION + "}"
            )
        );

        assertStatus(201, as(WRITER_WITHOUT_READ, "PUT", "/_ltr/_feature/f1", "{\"feature\":" + FEATURE + "}"));
        assertDeniedForCallerSearch(as(WRITER_WITHOUT_READ, "POST", "/_ltr/_featureset/added/_addfeatures/f1", "{" + VALIDATION + "}"));

        assertStatus(201, as(WRITER, "PUT", "/_ltr/_featureset/unvalidated", "{\"featureset\":{\"features\":[" + FEATURE + "]}}"));
        String model = "{\"model\":{\"name\":\"model\",\"model\":{\"type\":\"model/linear\",\"definition\":{\"f1\":1.0}}},"
            + VALIDATION
            + "}";
        assertDeniedForCallerSearch(as(WRITER_WITHOUT_READ, "POST", "/_ltr/_featureset/unvalidated/_createmodel", model));

        assertStatus(404, as(WRITER, "GET", "/_ltr/_featureset/validated", null));
        assertStatus(404, as(WRITER, "GET", "/_ltr/_featureset/added", null));
        assertStatus(404, as(WRITER, "GET", "/_ltr/_model/model", null));
    }

    public void testDeletingTheStoreNeedsAnLtrPermission() throws IOException {
        Response denied = as(NO_LTR, "DELETE", "/_ltr", null);
        assertStatus(403, denied);
        assertTrue(body(denied), body(denied).contains("cluster:admin/ltr/featurestore/delete"));
        assertTrue("the store survives the denied delete", listStores().containsKey("_default_"));

        assertStatus(200, as(WRITER, "DELETE", "/_ltr", null));
        assertFalse("the store is gone after the allowed delete", listStores().containsKey("_default_"));
    }

    public void testDeletingTheStoreDeletesOneStore() throws IOException {
        adminClient().performRequest(new Request("PUT", "/_ltr/named"));
        try {
            assertStatus(400, as(WRITER, "DELETE", "/_ltr/*", null));
            assertStatus(400, as(WRITER, "DELETE", "/_ltr/named,other", null));
            assertTrue("a wildcard deletes nothing", listStores().containsKey("named"));
            assertStatus(200, as(WRITER, "DELETE", "/_ltr/named", null));
            assertFalse(listStores().containsKey("named"));
        } finally {
            Request delete = new Request("DELETE", "/_ltr/named");
            delete.addParameter("ignore", "404");
            adminClient().performRequest(delete);
        }
    }

    public void testListingStoresCountsElementsWithOnlyTheListPermission() throws IOException {
        assertStatus(201, as(WRITER, "PUT", "/_ltr/_feature/f1", "{\"feature\":" + FEATURE + "}"));

        Response response = as(LISTER, "GET", "/_ltr", null);
        assertStatus(200, response);
        @SuppressWarnings("unchecked")
        Map<String, Object> store = (Map<String, Object>) entityAsMap(response).get("stores");
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) ((Map<String, Object>) store.get("_default_")).get("counts");
        assertEquals(Map.of("feature", 1), counts);

        assertStatus(403, as(NO_LTR, "GET", "/_ltr", null));
    }

    public void testReadingElementsNeedsAnLtrPermission() throws IOException {
        assertStatus(201, as(WRITER, "PUT", "/_ltr/_feature/f1", "{\"feature\":" + FEATURE + "}"));

        assertStatus(200, as(WRITER_WITHOUT_READ, "GET", "/_ltr/_feature/f1", null));
        assertStatus(200, as(WRITER_WITHOUT_READ, "HEAD", "/_ltr/_feature/f1", null));
        assertStatus(404, as(WRITER_WITHOUT_READ, "GET", "/_ltr/_feature/missing", null));
        Response search = as(WRITER_WITHOUT_READ, "GET", "/_ltr/_feature?prefix=f", null);
        assertStatus(200, search);
        assertTrue(body(search), body(search).contains("\"_id\":\"feature-f1\""));
        assertStatus(404, as(WRITER_WITHOUT_READ, "GET", "/_ltr/missing", null));

        assertDenied(as(NO_LTR, "GET", "/_ltr/_feature/f1", null), "cluster:admin/ltr/featurestore/get");
        assertDenied(as(NO_LTR, "GET", "/_ltr/_feature", null), "cluster:admin/ltr/featurestore/search");
        assertDenied(as(LISTER, "GET", "/_ltr/_feature", null), "cluster:admin/ltr/featurestore/search");
        assertDenied(as(NO_LTR, "GET", "/_ltr/missing", null), "cluster:admin/ltr/featurestore/exists");
    }

    public void testDeletingAnElementNeedsAnLtrPermissionAndIsVisibleAtOnce() throws IOException {
        assertStatus(201, as(WRITER, "PUT", "/_ltr/_feature/f1", "{\"feature\":" + FEATURE + "}"));

        assertDenied(as(NO_LTR, "DELETE", "/_ltr/_feature/f1", null), "cluster:admin/ltr/featurestore/delete_element");
        assertStatus(200, as(WRITER, "GET", "/_ltr/_feature/f1", null));

        assertStatus(200, as(WRITER_WITHOUT_READ, "DELETE", "/_ltr/_feature/f1", null));
        Response search = as(WRITER, "GET", "/_ltr/_feature", null);
        assertStatus(200, search);
        assertFalse("the element search no longer returns the deleted element: " + body(search), body(search).contains("feature-f1"));
        assertStatus(404, as(WRITER, "GET", "/_ltr/_feature/f1", null));
    }

    public void testReadingElementsAcceptsOnlyStoreIndices() throws IOException {
        assertStatus(400, as(WRITER, "GET", "/_ltr/x,logs/_feature", null));
        assertStatus(400, as(WRITER, "GET", "/_ltr/x,logs/_feature/f1", null));
        assertStatus(400, as(WRITER, "GET", "/_ltr/x,logs", null));
        assertStatus(400, as(WRITER, "DELETE", "/_ltr/*/_feature/f1", null));
        assertStatus(400, as(WRITER, "DELETE", "/_ltr/x,y/_feature/f1", null));
        assertStatus(404, as(WRITER, "HEAD", "/_ltr/_feature/missing", null));
        assertStatus(404, as(WRITER, "DELETE", "/_ltr/_feature/missing", null));
        assertStatus(404, as(WRITER, "GET", "/_ltr/missing/_feature/f1", null));
    }

    public void testAnAliasWithAStoreNameIsRejected() throws IOException {
        Request alias = new Request("POST", "/_aliases");
        alias.setJsonEntity("{\"actions\":[{\"add\":{\"index\":\"" + VALIDATION_INDEX + "\",\"alias\":\".ltrstore_alias\"}}]}");
        adminClient().performRequest(alias);
        Request typed = new Request("PUT", "/" + VALIDATION_INDEX + "/_doc/typed");
        typed.addParameter("refresh", "true");
        typed.setJsonEntity("{\"type\":\"feature\",\"name\":\"f1\"}");
        adminClient().performRequest(typed);
        try {
            assertStatus(400, as(WRITER, "GET", "/_ltr/alias/_feature", null));
            assertStatus(400, as(WRITER, "GET", "/_ltr/alias/_feature/f1", null));
            assertStatus(400, as(WRITER, "DELETE", "/_ltr/alias/_feature/f1", null));
            assertStatus(400, as(WRITER, "GET", "/_ltr/alias", null));
            Response all = as(WRITER, "GET", "/_ltr/*/_feature", null);
            assertStatus(200, all);
            assertFalse("a wildcard does not expand to the alias: " + body(all), body(all).contains(VALIDATION_INDEX));
        } finally {
            Request remove = new Request("POST", "/_aliases");
            remove.setJsonEntity("{\"actions\":[{\"remove\":{\"index\":\"" + VALIDATION_INDEX + "\",\"alias\":\".ltrstore_alias\"}}]}");
            adminClient().performRequest(remove);
        }
    }

    private void createUser(String name, String role) throws IOException {
        Request putRole = new Request("PUT", "/_plugins/_security/api/roles/" + name);
        putRole.setJsonEntity(role);
        adminClient().performRequest(putRole);
        Request putUser = new Request("PUT", "/_plugins/_security/api/internalusers/" + name);
        putUser.setJsonEntity("{\"password\":\"" + PASSWORD + "\"}");
        adminClient().performRequest(putUser);
        Request mapRole = new Request("PUT", "/_plugins/_security/api/rolesmapping/" + name);
        mapRole.setJsonEntity("{\"users\":[\"" + name + "\"]}");
        adminClient().performRequest(mapRole);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> listStores() throws IOException {
        return (Map<String, Object>) entityAsMap(adminClient().performRequest(new Request("GET", "/_ltr"))).get("stores");
    }

    private Response as(String user, String method, String path, String body) throws IOException {
        Request request = new Request(method, path);
        if (body != null) {
            request.setJsonEntity(body);
        }
        String credentials = Base64.getEncoder().encodeToString((user + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
        request.setOptions(request.getOptions().toBuilder().addHeader("Authorization", "Basic " + credentials));
        try {
            return client().performRequest(request);
        } catch (ResponseException e) {
            return e.getResponse();
        }
    }

    private static void assertDeniedForCallerSearch(Response response) throws IOException {
        assertStatus(403, response);
        String body = body(response);
        assertTrue(body, body.contains("indices:data/read/search"));
        assertTrue("the caller, not the plugin, is denied: " + body, body.contains("User [name=" + WRITER_WITHOUT_READ));
    }

    private static void assertDenied(Response response, String action) throws IOException {
        assertStatus(403, response);
        assertTrue(body(response), body(response).contains(action));
    }

    private static void assertStatus(int expected, Response response) throws IOException {
        assertEquals(body(response), expected, response.getStatusLine().getStatusCode());
    }

    private static String body(Response response) throws IOException {
        try {
            return response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
        } catch (ParseException e) {
            throw new IOException(e);
        }
    }
}
