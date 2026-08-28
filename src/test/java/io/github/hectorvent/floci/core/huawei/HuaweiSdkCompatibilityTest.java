package io.github.hectorvent.floci.core.huawei;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.sdk.core.auth.AKSKSigner;
import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.auth.DerivedAKSKSigner;
import com.huaweicloud.sdk.core.auth.IAKSKSigner;
import com.huaweicloud.sdk.core.http.HttpConfig;
import com.huaweicloud.sdk.core.http.HttpMethod;
import com.huaweicloud.sdk.core.http.HttpRequest;
import com.huaweicloud.sdk.core.http.HttpResponse;
import com.huaweicloud.sdk.core.impl.DefaultHttpClient;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(HuaweiSdkCompatibilityTest.SignatureValidationProfile.class)
class HuaweiSdkCompatibilityTest {

    private static final String ACCESS_KEY = "AccessKey";
    private static final String SECRET_KEY = "SecretKey";
    private static final String PROJECT_ID = "sdk-project-id";
    private static final String DOMAIN_ID = "sdk-domain-id";

    @TestHTTPResource("/resources")
    URI endpoint;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void officialJavaSdkCoreStandardRequestCrossesTheHttpBoundary() throws IOException {
        HttpRequest request = request(HttpMethod.GET, null)
                .addHeader("X-Project-Id", PROJECT_ID)
                .addHeader("X-Domain-Id", DOMAIN_ID)
                .build();

        JsonNode response = invoke(request, AKSKSigner.getInstance(), credentials());

        assertContext(response, "SDK-HMAC-SHA256", "region-1", null);
        assertEquals("GET", response.path("method").asText());
        assertEquals("1", response.path("size").asText());
    }

    @Test
    void officialJavaSdkCoreDerivedRequestPopulatesCredentialScope() throws IOException {
        BasicCredentials credentials = credentials();
        credentials.processDerivedAuthParams("demo", "test-region-1");
        HttpRequest request = request(HttpMethod.POST, "{\"source\":\"java-sdk\"}")
                .addHeader("X-Project-Id", PROJECT_ID)
                .addHeader("X-Domain-Id", DOMAIN_ID)
                .build();

        JsonNode response = invoke(request, DerivedAKSKSigner.getInstance(), credentials);

        assertContext(response, "V11-HMAC-SHA256", "test-region-1", "demo");
        assertEquals("POST", response.path("method").asText());
        assertEquals("{\"source\":\"java-sdk\"}", response.path("body").asText());
    }

    @Test
    void pythonSdkGeneratedFixturesPassStandardAndDerivedVerification() throws IOException {
        JsonNode fixtures = loadPythonFixtures();

        for (JsonNode fixture : fixtures.path("requests")) {
            String body = fixture.path("body").asText();
            HttpRequest.HttpRequestBuilder builder = request(
                    HttpMethod.valueOf(fixture.path("method").asText()),
                    body.isEmpty() ? null : body)
                    .addHeader("Host", fixture.path("host").asText())
                    .addHeader("X-Sdk-Date", fixture.path("sdkDate").asText())
                    .addHeader("Authorization", fixture.path("authorization").asText())
                    .addHeader("X-Project-Id", PROJECT_ID)
                    .addHeader("X-Domain-Id", DOMAIN_ID);

            JsonNode response = invokeSigned(builder.build());

            assertContext(
                    response,
                    fixture.path("algorithm").asText(),
                    fixture.path("regionId").asText("region-1"),
                    fixture.path("serviceName").asText(null));
            assertEquals(fixture.path("method").asText(), response.path("method").asText());
        }
    }

    private HttpRequest.HttpRequestBuilder request(HttpMethod method, String body) {
        HttpRequest.HttpRequestBuilder builder = HttpRequest.newBuilder()
                .withEndpoint(endpoint.toString())
                .withMethod(method)
                .withContentType("application/json")
                .withPath("")
                .addQueryParam("size", List.of("1"));
        return body == null ? builder : builder.withBodyAsString(body);
    }

    private JsonNode invoke(HttpRequest request,
                            IAKSKSigner signer,
                            BasicCredentials credentials) throws IOException {
        Map<String, String> authenticationHeaders = signer.sign(request, credentials);
        return invokeSigned(request.builder().addHeaders(authenticationHeaders).build());
    }

    private JsonNode invokeSigned(HttpRequest request) throws IOException {
        HttpResponse response = new DefaultHttpClient(new HttpConfig()).syncInvokeHttp(request);
        String responseBody = response.getBodyAsString();
        assertEquals(200, response.getStatusCode(), responseBody);
        assertNotNull(response.getHeader("X-Request-Id"));
        return objectMapper.readTree(responseBody);
    }

    private void assertContext(JsonNode response,
                               String algorithm,
                               String regionId,
                               String serviceName) {
        assertEquals(ACCESS_KEY, response.path("accessKey").asText());
        assertEquals(PROJECT_ID, response.path("projectId").asText());
        assertEquals(DOMAIN_ID, response.path("domainId").asText());
        assertEquals(regionId, response.path("regionId").asText());
        assertEquals(algorithm, response.path("authenticationAlgorithm").asText());
        if (serviceName == null) {
            assertFalse(response.hasNonNull("serviceName"));
        } else {
            assertEquals(serviceName, response.path("serviceName").asText());
        }
        assertFalse(response.path("requestId").asText().isBlank());
    }

    private JsonNode loadPythonFixtures() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/huawei/python-sdk-signing-fixtures.json")) {
            assertNotNull(stream);
            return objectMapper.readTree(stream);
        }
    }

    private static BasicCredentials credentials() {
        return new BasicCredentials()
                .withAk(ACCESS_KEY)
                .withSk(SECRET_KEY)
                .withProjectId(PROJECT_ID);
    }

    public static class SignatureValidationProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "floci.huawei.auth.validate-signatures", "true",
                    "floci.huawei.auth.access-key", ACCESS_KEY,
                    "floci.huawei.auth.secret-key", SECRET_KEY,
                    "floci.huawei.auth.max-clock-skew-seconds", "400000000");
        }
    }
}
