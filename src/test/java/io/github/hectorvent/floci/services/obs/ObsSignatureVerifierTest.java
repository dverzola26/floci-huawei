package io.github.hectorvent.floci.services.obs;

import io.github.hectorvent.floci.config.EmulatorConfig;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObsSignatureVerifierTest {

    private EmulatorConfig.HuaweiAuthConfig config;
    private ObsSignatureVerifier verifier;
    private ContainerRequestContext request;
    private ObsRequestContext context;

    @BeforeEach
    void setUp() {
        config = mock(EmulatorConfig.HuaweiAuthConfig.class);
        when(config.validateSignatures()).thenReturn(true);
        when(config.accessKey()).thenReturn("AccessKey");
        when(config.secretKey()).thenReturn("SecretKey");
        when(config.maxClockSkewSeconds()).thenReturn(900L);
        verifier = new ObsSignatureVerifier(config, new ObsCanonicalRequest(),
                Clock.fixed(Instant.parse("2025-05-27T12:00:00Z"), ZoneOffset.UTC));
        request = mock(ContainerRequestContext.class);
        when(request.getHeaders()).thenReturn(new MultivaluedHashMap<>());
        when(request.getHeaderString("Date")).thenReturn("Tue May 27 2025 12:00:00 GMT");
        context = new ObsRequestContext();
        context.setMethod("HEAD");
        context.setRawPath("/python-sdk-missing-bucket");
    }

    @Test
    void acceptsOfficialFixtureAndRejectsCredentialTampering() {
        ObsAuthorization valid = new ObsAuthorization(
                "AccessKey", "fclH5I6gB8+oBaFhdjifSOLg+cs=", null);
        assertDoesNotThrow(() -> verifier.verify(request, context, valid));

        assertEquals("InvalidAccessKeyId", assertThrows(ObsException.class,
                () -> verifier.verify(request, context,
                        new ObsAuthorization("OtherKey", valid.signature(), null))).getErrorCode());
        assertEquals("SignatureDoesNotMatch", assertThrows(ObsException.class,
                () -> verifier.verify(request, context,
                        new ObsAuthorization("AccessKey", "tampered", null))).getErrorCode());
    }

    @Test
    void validatesExpiryOnlyWhenSignatureValidationIsEnabled() {
        ObsAuthorization expired = new ObsAuthorization("AccessKey", "value", 1L);
        assertEquals("AccessDenied", assertThrows(ObsException.class,
                () -> verifier.verify(request, context, expired)).getErrorCode());
        when(config.validateSignatures()).thenReturn(false);
        assertDoesNotThrow(() -> verifier.verify(request, context, expired));
    }
}
