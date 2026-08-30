package io.github.hectorvent.floci.services.obs;

import com.huaweicloud.sdk.core.http.HttpConfig;
import com.huaweicloud.sdk.core.http.HttpMethod;
import com.huaweicloud.sdk.core.http.HttpRequest;
import com.huaweicloud.sdk.core.http.HttpResponse;
import com.huaweicloud.sdk.core.impl.DefaultHttpClient;
import com.huaweicloud.sdk.obs.v1.ObsCredentials;
import com.huaweicloud.sdk.obs.v1.ObsSigner;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(ObsRoutingAuthIntegrationTest.ObsValidationProfile.class)
class ObsRoutingAuthIntegrationTest {

    private static final String ACCESS_KEY = "AccessKey";
    private static final String SECRET_KEY = "SecretKey";
    private static final String FIXED_DATE = "Tue, 27 May 2025 12:00:00 GMT";

    @TestHTTPResource("/")
    URI endpoint;

    @Test
    void signedGetReturnsObsXmlAndNoAwsHeaders() {
        given()
                .header("Date", FIXED_DATE)
                .header("Authorization", "OBS AccessKey:o/ql5Bpmzhkd2gKxqt1z2FhtvNQ=")
        .when()
                .get("/python-sdk-missing-bucket")
        .then()
                .statusCode(404)
                .contentType("application/xml")
                .header("x-obs-request-id", not(blankOrNullString()))
                .header("x-amz-request-id", blankOrNullString())
                .header("x-amzn-requestid", blankOrNullString())
                .body(containsString("<Code>NoSuchBucket</Code>"))
                .body(containsString("<Resource>/python-sdk-missing-bucket</Resource>"));
    }

    @Test
    void officialJavaObsSignerCrossesHttpBoundaryForHead() throws IOException {
        ObsCredentials credentials = new ObsCredentials().withAk(ACCESS_KEY).withSk(SECRET_KEY);
        HttpRequest source = HttpRequest.newBuilder()
                .withEndpoint("http://java-sdk-missing-bucket.obs.localhost:" + endpoint.getPort())
                .withMethod(HttpMethod.HEAD)
                .withPath("/")
                .addHeader("date", FIXED_DATE)
                .build();
        HttpRequest signed = new ObsSigner(credentials).sign(source);
        HttpRequest.HttpRequestBuilder transportBuilder = HttpRequest.newBuilder()
                .withEndpoint(endpoint.toString())
                .withMethod(HttpMethod.HEAD)
                .withPath("/java-sdk-missing-bucket/");
        signed.getHeaders().forEach((name, values) ->
                values.forEach(value -> transportBuilder.addHeader(name, value)));
        HttpRequest transport = transportBuilder.build();

        HttpResponse response = new DefaultHttpClient(new HttpConfig()).syncInvokeHttp(transport);

        assertEquals(404, response.getStatusCode());
        assertNotNull(response.getHeader("x-obs-request-id"));
        assertFalse(response.getHeader("x-obs-request-id").isBlank());
        assertNull(response.getHeader("x-amz-request-id"));
        assertTrue(response.getBodyAsString() == null || response.getBodyAsString().isEmpty());
    }

    @Test
    void headSuppressesXmlAndMalformedObsStillUsesObsErrors() {
        Response head = given()
                .header("Date", FIXED_DATE)
                .header("Authorization", "OBS AccessKey:dHGdnXgECUfv/eVo/XdxMhnEXAY=")
                .head("/python-sdk-missing-bucket");
        assertEquals(404, head.statusCode());
        assertEquals("", head.body().asString());
        assertFalse(head.header("x-obs-request-id").isBlank());

        given().header("Authorization", "OBS")
                .when().get("/missing-bucket")
                .then().statusCode(400)
                .header("x-obs-request-id", not(blankOrNullString()))
                .body(containsString("<Code>InvalidArgument</Code>"));
    }

    @Test
    void directInternalRouteAndAwsTrafficAreNotClaimedAsObs() {
        given().when().get(ObsRoutingFilter.INTERNAL_PATH)
                .then().statusCode(404)
                .header("x-obs-request-id", blankOrNullString());

        given().header("Authorization", "AWS4-HMAC-SHA256 invalid")
                .queryParam("AccessKeyId", ACCESS_KEY)
                .queryParam("Expires", "9999999999")
                .queryParam("Signature", "value")
                .when().get("/s3-path")
                .then().header("x-obs-request-id", blankOrNullString());
    }

    public static class ObsValidationProfile implements QuarkusTestProfile {
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
