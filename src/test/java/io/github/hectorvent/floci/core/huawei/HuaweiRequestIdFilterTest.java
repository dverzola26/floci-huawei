package io.github.hectorvent.floci.core.huawei;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HuaweiRequestIdFilterTest {

    private HuaweiRequestContext requestContext;
    private HuaweiRequestIdFilter filter;

    @BeforeEach
    void setUp() {
        requestContext = new HuaweiRequestContext();
        requestContext.setRequestId("request-id-1");
        filter = new HuaweiRequestIdFilter(requestContext);
    }

    @Test
    void addsRequestIdToHuaweiResponse() {
        ContainerRequestContext request = huaweiRequest();
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ContainerResponseContext response = response(headers);

        filter.filter(request, response);

        assertEquals("request-id-1", headers.getFirst(HuaweiRequestIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    void preservesControllerRequestId() {
        ContainerRequestContext request = huaweiRequest();
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putSingle(HuaweiRequestIdFilter.REQUEST_ID_HEADER, "controller-id");

        filter.filter(request, response(headers));

        assertEquals("controller-id", headers.getFirst(HuaweiRequestIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    void doesNotAddHuaweiHeaderToAwsResponse() {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();

        filter.filter(request, response(headers));

        assertFalse(headers.containsKey(HuaweiRequestIdFilter.REQUEST_ID_HEADER));
    }

    private static ContainerRequestContext huaweiRequest() {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        when(request.getProperty(HuaweiRequestClassifier.REQUEST_PROPERTY))
                .thenReturn(HuaweiAuthAlgorithm.SDK_HMAC_SHA256);
        return request;
    }

    private static ContainerResponseContext response(MultivaluedMap<String, Object> headers) {
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getHeaders()).thenReturn(headers);
        return response;
    }
}
