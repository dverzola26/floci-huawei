package io.github.hectorvent.floci.services.obs;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObsAuthorizationParserTest {

    private final ObsAuthorizationParser parser = new ObsAuthorizationParser();

    @Test
    void parsesHeaderAndDecodedSignedQuery() {
        ObsAuthorization header = parser.parseHeader("OBS AccessKey:a+b/c=");
        assertEquals("AccessKey", header.accessKey());
        assertEquals("a+b/c=", header.signature());

        MultivaluedHashMap<String, String> query = new MultivaluedHashMap<>();
        query.add("AccessKeyId", "AccessKey");
        query.add("Expires", "1750000000");
        query.add("Signature", "a+b/c=");
        ObsAuthorization parsed = parser.parse(request(query));
        assertEquals("a+b/c=", parsed.signature());
        assertEquals(1750000000L, parsed.expires());
    }

    @Test
    void rejectsMalformedAndDuplicateCredentials() {
        assertEquals("InvalidArgument", assertThrows(ObsException.class,
                () -> parser.parseHeader("OBS AccessKey:one:two")).getErrorCode());

        MultivaluedHashMap<String, String> query = new MultivaluedHashMap<>();
        query.add("AccessKeyId", "AccessKey");
        query.add("Expires", "not-a-number");
        query.add("Signature", "value");
        assertEquals("InvalidArgument", assertThrows(ObsException.class,
                () -> parser.parseQuery(query)).getErrorCode());
        query.putSingle("Expires", "1750000000");
        query.add("Signature", "duplicate");
        assertEquals("InvalidArgument", assertThrows(ObsException.class,
                () -> parser.parseQuery(query)).getErrorCode());
    }

    private static ContainerRequestContext request(MultivaluedHashMap<String, String> query) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters(true)).thenReturn(query);
        return request;
    }
}
