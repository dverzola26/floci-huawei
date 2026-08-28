package io.github.hectorvent.floci.core.common;

import io.github.hectorvent.floci.core.huawei.HuaweiAuthAlgorithm;
import io.github.hectorvent.floci.core.huawei.HuaweiRequestClassifier;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AwsRequestIdFilterTest {

    @Test
    void doesNotAddAwsHeadersToHuaweiResponse() {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        when(request.getProperty(HuaweiRequestClassifier.REQUEST_PROPERTY))
                .thenReturn(HuaweiAuthAlgorithm.SDK_HMAC_SHA256);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getHeaders()).thenReturn(headers);

        new AwsRequestIdFilter().filter(request, response);

        assertTrue(headers.isEmpty());
    }
}
