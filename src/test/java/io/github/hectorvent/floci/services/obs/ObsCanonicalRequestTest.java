package io.github.hectorvent.floci.services.obs;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObsCanonicalRequestTest {

    @Test
    void buildsLegacyCanonicalStringFromUntouchedResource() {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add("X-Obs-Meta-Name", "  two   spaces ");
        headers.add("x-obs-date", "Tue, 27 May 2025 12:00:00 GMT");
        when(request.getHeaders()).thenReturn(headers);
        when(request.getHeaderString("x-obs-date")).thenReturn("Tue, 27 May 2025 12:00:00 GMT");

        ObsRequestContext context = new ObsRequestContext();
        context.setMethod("GET");
        context.setRawPath("/bucket/a%2Fb+%252F");
        context.setRawQuery("uploads=&ignored=1&partNumber=2&uploadId=z%2F1");

        assertEquals("GET\n\n\n\n"
                        + "x-obs-date:Tue, 27 May 2025 12:00:00 GMT\n"
                        + "x-obs-meta-name:two spaces\n"
                        + "/bucket/a%2Fb+%252F?partNumber=2&uploadId=z%2F1&uploads",
                new ObsCanonicalRequest().build(request, context,
                        new ObsAuthorization("AccessKey", "signature", null)));
    }

    @Test
    void reproducesPinnedPythonSdkFixture() {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        when(request.getHeaders()).thenReturn(headers);
        when(request.getHeaderString("Date")).thenReturn("Tue May 27 2025 12:00:00 GMT");
        ObsRequestContext context = new ObsRequestContext();
        context.setMethod("HEAD");
        context.setRawPath("/python-sdk-missing-bucket");

        String canonical = new ObsCanonicalRequest().build(request, context,
                new ObsAuthorization("AccessKey", "signature", null));
        assertEquals("HEAD\n\n\nTue May 27 2025 12:00:00 GMT\n/python-sdk-missing-bucket", canonical);
        assertEquals("fclH5I6gB8+oBaFhdjifSOLg+cs=", ObsSignatureVerifier.sign(canonical, "SecretKey"));
    }
}
