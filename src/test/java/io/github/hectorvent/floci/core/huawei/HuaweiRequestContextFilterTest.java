package io.github.hectorvent.floci.core.huawei;

import io.github.hectorvent.floci.config.EmulatorConfig;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HuaweiRequestContextFilterTest {

    private static final String DEFAULT_REGION = "region-1";
    private static final String DEFAULT_PROJECT = "00000000000000000000000000000000";
    private static final String DEFAULT_DOMAIN = "11111111111111111111111111111111";

    private EmulatorConfig.HuaweiConfig config;
    private HuaweiRequestContext requestContext;
    private HuaweiRequestContextFilter filter;

    @BeforeEach
    void setUp() {
        config = mock(EmulatorConfig.HuaweiConfig.class);
        when(config.enabled()).thenReturn(true);
        when(config.defaultRegion()).thenReturn(DEFAULT_REGION);
        when(config.defaultProjectId()).thenReturn(DEFAULT_PROJECT);
        when(config.defaultDomainId()).thenReturn(DEFAULT_DOMAIN);
        requestContext = new HuaweiRequestContext();
        filter = new HuaweiRequestContextFilter(config, new HuaweiRequestClassifier(), requestContext);
    }

    @Test
    void initializesHuaweiContextFromStandardAuthorization() {
        ContainerRequestContext context = context(
                "SDK-HMAC-SHA256 Access=test, SignedHeaders=host;x-sdk-date, Signature=abc");

        filter.filter(context);

        assertTrue(requestContext.isHuaweiRequest());
        assertNotNull(requestContext.getRequestId());
        assertEquals(DEFAULT_REGION, requestContext.getRegionId());
        assertEquals(DEFAULT_PROJECT, requestContext.getProjectId());
        assertEquals(DEFAULT_DOMAIN, requestContext.getDomainId());
        assertEquals(HuaweiAuthAlgorithm.SDK_HMAC_SHA256, requestContext.getAuthenticationAlgorithm());
        verify(context).setProperty(
                HuaweiRequestClassifier.REQUEST_PROPERTY, HuaweiAuthAlgorithm.SDK_HMAC_SHA256);
    }

    @Test
    void headerScopesOverrideConfiguredDefaults() {
        ContainerRequestContext context = context(
                "V11-HMAC-SHA256 Credential=test/20260828/region-2/ecs, SignedHeaders=host, Signature=abc");
        when(context.getHeaderString("X-Project-Id")).thenReturn("project-2");
        when(context.getHeaderString("X-Domain-Id")).thenReturn("domain-2");

        filter.filter(context);

        assertEquals("project-2", requestContext.getProjectId());
        assertEquals("domain-2", requestContext.getDomainId());
        assertEquals(HuaweiAuthAlgorithm.V11_HMAC_SHA256, requestContext.getAuthenticationAlgorithm());
    }

    @Test
    void ignoresAwsRequests() {
        ContainerRequestContext context = context(
                "AWS4-HMAC-SHA256 Credential=test/20260828/us-east-1/s3/aws4_request");

        filter.filter(context);

        assertFalse(requestContext.isHuaweiRequest());
        verify(context, never()).setProperty(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void disabledHuaweiSupportDoesNotClassifyRequests() {
        when(config.enabled()).thenReturn(false);
        ContainerRequestContext context = context("SDK-HMAC-SHA256 Access=test");

        filter.filter(context);

        assertFalse(requestContext.isHuaweiRequest());
        verify(context, never()).setProperty(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recognizedUnsupportedAlgorithmReturnsClearError() {
        ContainerRequestContext context = context("SDK-HMAC-SM3 Access=test");

        HuaweiException exception = assertThrows(HuaweiException.class, () -> filter.filter(context));

        assertEquals(501, exception.getHttpStatus());
        assertEquals("FLOCI.HUAWEI.AUTH.0002", exception.getErrorCode());
        assertNotNull(requestContext.getRequestId());
    }

    private static ContainerRequestContext context(String authorization) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getHeaderString("Authorization")).thenReturn(authorization);
        return context;
    }
}
