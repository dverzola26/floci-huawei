package io.github.hectorvent.floci.services.obs;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObsRequestClassifierTest {

    private final ObsRequestClassifier classifier = new ObsRequestClassifier();

    @Test
    void claimsObsHeadersIncludingMalformedCredentials() {
        assertTrue(classifier.isObsRequest(request("OBS AccessKey:signature", new MultivaluedHashMap<>())));
        assertTrue(classifier.isObsRequest(request("OBS", new MultivaluedHashMap<>())));
        assertFalse(classifier.isObsRequest(request("AWS4-HMAC-SHA256 value", new MultivaluedHashMap<>())));
    }

    @Test
    void claimsCompleteQueryTupleButNotPartialOrAwsAuthorizedRequests() {
        MultivaluedHashMap<String, String> query = new MultivaluedHashMap<>();
        query.add("AccessKeyId", "AccessKey");
        query.add("Expires", "1");
        query.add("Signature", "value");
        query.add("Signature", "duplicate");

        assertTrue(classifier.isObsRequest(request(null, query)));
        assertFalse(classifier.isObsRequest(request("AWS4-HMAC-SHA256 value", query)));
        query.remove("Expires");
        assertFalse(classifier.isObsRequest(request(null, query)));
    }

    private static ContainerRequestContext request(String authorization,
                                                   MultivaluedHashMap<String, String> query) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(request.getHeaderString("Authorization")).thenReturn(authorization);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters(true)).thenReturn(query);
        return request;
    }
}
