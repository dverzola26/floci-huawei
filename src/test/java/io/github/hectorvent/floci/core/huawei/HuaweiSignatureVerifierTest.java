package io.github.hectorvent.floci.core.huawei;

import io.github.hectorvent.floci.config.EmulatorConfig;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HuaweiSignatureVerifierTest {

    private static final String SDK_DATE = "20191115T033655Z";
    private static final String GET_SIGNATURE =
            "fd95e7da6f695cfb4cabbb9d6b0968aec155bc576b064835282473539ae9ea1d";

    private EmulatorConfig.HuaweiAuthConfig config;
    private HuaweiSignatureVerifier verifier;
    private HuaweiAuthorizationParser parser;

    @BeforeEach
    void setUp() {
        config = mock(EmulatorConfig.HuaweiAuthConfig.class);
        when(config.validateSignatures()).thenReturn(true);
        when(config.accessKey()).thenReturn("AccessKey");
        when(config.secretKey()).thenReturn("SecretKey");
        when(config.maxClockSkewSeconds()).thenReturn(900L);
        verifier = new HuaweiSignatureVerifier(
                config,
                new HuaweiCanonicalRequest(),
                Clock.fixed(Instant.parse("2019-11-15T03:36:55Z"), ZoneOffset.UTC));
        parser = new HuaweiAuthorizationParser();
    }

    @Test
    void reproducesOfficialJavaSdkGetAndPostSignatures() {
        HuaweiAuthorization getAuthorization = authorization(GET_SIGNATURE);
        Map<String, List<String>> headers = Map.of(
                "Host", List.of("service.region.example.com"),
                "X-Sdk-Date", List.of(SDK_DATE));

        assertEquals(GET_SIGNATURE, verifier.calculateSignature(
                "GET",
                URI.create("https://service.region.example.com/"
                        + "v1/77b6a44cba5143ab91d13ab9a8ff44fd/vpcs"
                        + "?limit=2&marker=13551d6b-755d-4757-b956-536f674975c0"),
                headers,
                new byte[0],
                getAuthorization,
                SDK_DATE,
                "SecretKey"));

        assertEquals("b5649aa774f6cac7437631662f08815fc65c70e40e486df6db2436ce68902771",
                verifier.calculateSignature(
                        "POST",
                        URI.create("https://service.region.example.com/"
                                + "v1/77b6a44cba5143ab91d13ab9a8ff44fd/vpc/123"),
                        headers,
                        "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8),
                        getAuthorization,
                        SDK_DATE,
                        "SecretKey"));
    }

    @Test
    void reproducesOfficialPythonSdkGetAndPostSignatures() {
        HuaweiAuthorization authorization = authorization(GET_SIGNATURE);
        Map<String, List<String>> headers = Map.of(
                "Host", List.of("service.endpoint.myhuaweicloud.com"),
                "X-Sdk-Date", List.of("20200608T023900Z"));
        URI uri = URI.create("https://service.endpoint.myhuaweicloud.com/resources?size=1");

        assertEquals("cfb4171acec81de07d50e53d57eb77edd537414d66ddb1d7d780f128e12cd842",
                verifier.calculateSignature(
                        "GET", uri, headers, new byte[0], authorization,
                        "20200608T023900Z", "SecretKey"));
        assertEquals("436b1ac0a1ae03705934bb70ef2f2e09f7bfed2117d731a38235053199323a1f",
                verifier.calculateSignature(
                        "POST", uri, headers,
                        "{\"name\":\"test\",\"id\":1}".getBytes(StandardCharsets.UTF_8),
                        authorization, "20200608T023900Z", "SecretKey"));
    }

    @Test
    void acceptsOfficialVectorAndRestoresRequestBody() {
        ContainerRequestContext context = request(
                URI.create("https://service.region.example.com/"
                        + "v1/77b6a44cba5143ab91d13ab9a8ff44fd/vpcs"
                        + "?limit=2&marker=13551d6b-755d-4757-b956-536f674975c0"),
                new byte[0]);

        verifier.verifyIfEnabled(context, authorization(GET_SIGNATURE));

        verify(context).setEntityStream(any(ByteArrayInputStream.class));
    }

    @Test
    void rejectsBodyTamperingAndUnknownAccessKeys() {
        ContainerRequestContext tampered = request(
                URI.create("https://service.region.example.com/"
                        + "v1/77b6a44cba5143ab91d13ab9a8ff44fd/vpcs"
                        + "?limit=2&marker=13551d6b-755d-4757-b956-536f674975c0"),
                "tampered".getBytes(StandardCharsets.UTF_8));
        HuaweiException signatureError = assertThrows(
                HuaweiException.class, () -> verifier.verifyIfEnabled(tampered, authorization(GET_SIGNATURE)));
        assertEquals(HuaweiSignatureVerifier.INVALID_SIGNATURE_CODE, signatureError.getErrorCode());

        HuaweiAuthorization wrongAccessKey = parser.parse(
                "SDK-HMAC-SHA256 Access=OtherKey, SignedHeaders=host;x-sdk-date, Signature=" + GET_SIGNATURE);
        HuaweiException accessError = assertThrows(
                HuaweiException.class, () -> verifier.verifyIfEnabled(tampered, wrongAccessKey));
        assertEquals(HuaweiSignatureVerifier.UNKNOWN_ACCESS_KEY_CODE, accessError.getErrorCode());
    }

    @Test
    void rejectsRequestsOutsideAllowedClockSkew() {
        ContainerRequestContext context = request(URI.create("https://service.region.example.com/"), new byte[0]);
        when(context.getHeaderString("X-Sdk-Date")).thenReturn("20191115T030000Z");

        HuaweiException exception = assertThrows(
                HuaweiException.class, () -> verifier.verifyIfEnabled(context, authorization(GET_SIGNATURE)));

        assertEquals(HuaweiSignatureVerifier.INVALID_DATE_CODE, exception.getErrorCode());
    }

    @Test
    void validationDisabledLeavesRequestUntouched() {
        when(config.validateSignatures()).thenReturn(false);
        ContainerRequestContext context = mock(ContainerRequestContext.class);

        verifier.verifyIfEnabled(context, authorization(GET_SIGNATURE));

        verify(context, never()).getEntityStream();
    }

    private HuaweiAuthorization authorization(String signature) {
        return parser.parse(
                "SDK-HMAC-SHA256 Access=AccessKey, SignedHeaders=host;x-sdk-date, Signature=" + signature);
    }

    private static ContainerRequestContext request(URI uri, byte[] body) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add("Host", "service.region.example.com");
        headers.add("X-Sdk-Date", SDK_DATE);
        when(context.getMethod()).thenReturn("GET");
        when(context.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getRequestUri()).thenReturn(uri);
        when(context.getHeaders()).thenReturn(headers);
        when(context.getHeaderString("X-Sdk-Date")).thenReturn(SDK_DATE);
        when(context.getEntityStream()).thenReturn(new ByteArrayInputStream(body));
        return context;
    }
}
