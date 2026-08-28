package io.github.hectorvent.floci.core.huawei;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HuaweiCanonicalRequestTest {

    private final HuaweiCanonicalRequest canonicalRequest = new HuaweiCanonicalRequest();

    @Test
    void buildsCanonicalRequestFromOfficialJavaSdkVector() {
        HuaweiCanonicalRequest.Canonicalized canonical = canonicalRequest.canonicalize(
                "GET",
                URI.create("https://service.region.example.com/"
                        + "v1/77b6a44cba5143ab91d13ab9a8ff44fd/vpcs"
                        + "?marker=13551d6b-755d-4757-b956-536f674975c0&limit=2"),
                Map.of(
                        "Host", List.of("service.region.example.com"),
                        "X-Sdk-Date", List.of("20191115T033655Z")),
                List.of("host", "x-sdk-date"),
                new byte[0]);

        assertEquals("""
                GET
                /v1/77b6a44cba5143ab91d13ab9a8ff44fd/vpcs/
                limit=2&marker=13551d6b-755d-4757-b956-536f674975c0
                host:service.region.example.com
                x-sdk-date:20191115T033655Z

                host;x-sdk-date
                e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855""",
                canonical.value());
    }

    @Test
    void normalizesPathAndSortsRepeatedEncodedQueryValues() {
        HuaweiCanonicalRequest.Canonicalized canonical = canonicalRequest.canonicalize(
                "post",
                URI.create("https://example.com/a%20path/%E7%BD%91%E7%BB%9C?z=2&a=b%20c&a=a%2Bb"),
                Map.of(
                        "host", List.of(" example.com "),
                        "x-sdk-date", List.of(" 20191115T033655Z ")),
                List.of("host", "x-sdk-date"),
                "body".getBytes(StandardCharsets.UTF_8));

        String[] lines = canonical.value().split("\n", -1);
        assertEquals("POST", lines[0]);
        assertEquals("/a%20path/%E7%BD%91%E7%BB%9C/", lines[1]);
        assertEquals("a=a%2Bb&a=b%20c&z=2", lines[2]);
        assertEquals(HuaweiCanonicalRequest.sha256Hex("body".getBytes(StandardCharsets.UTF_8)),
                canonical.payloadHash());
    }

    @Test
    void honorsUnsignedPayloadDeclaration() {
        HuaweiCanonicalRequest.Canonicalized canonical = canonicalRequest.canonicalize(
                "PUT",
                URI.create("https://example.com/object"),
                Map.of(
                        "host", List.of("example.com"),
                        "x-sdk-date", List.of("20191115T033655Z"),
                        "x-sdk-content-sha256", List.of("UNSIGNED-PAYLOAD")),
                List.of("host", "x-sdk-date"),
                "ignored".getBytes(StandardCharsets.UTF_8));

        assertEquals("UNSIGNED-PAYLOAD", canonical.payloadHash());
    }
}
