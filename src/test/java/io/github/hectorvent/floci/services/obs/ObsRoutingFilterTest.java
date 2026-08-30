package io.github.hectorvent.floci.services.obs;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.huawei.HuaweiRequestClassifier;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObsRoutingFilterTest {

    @Test
    void preservesRawTargetMarksProviderAndRewritesToReservedPath() {
        EmulatorConfig config = mock(EmulatorConfig.class);
        EmulatorConfig.HuaweiConfig huawei = mock(EmulatorConfig.HuaweiConfig.class);
        when(config.huawei()).thenReturn(huawei);
        when(huawei.enabled()).thenReturn(true);
        ObsRequestClassifier classifier = mock(ObsRequestClassifier.class);
        when(classifier.isObsRequest(any())).thenReturn(true);
        ObsAuthorizationParser parser = mock(ObsAuthorizationParser.class);
        ObsAuthorization authorization = new ObsAuthorization("AccessKey", "signature", null);
        when(parser.parse(any())).thenReturn(authorization);
        ObsSignatureVerifier verifier = mock(ObsSignatureVerifier.class);
        ObsRequestContext obsContext = new ObsRequestContext();
        ObsRoutingFilter filter = new ObsRoutingFilter(config, classifier, parser, verifier, obsContext);

        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        URI original = URI.create("http://localhost/bucket/a%2Fb+%252F?uploads=&x=1");
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getRequestUri()).thenReturn(original);
        when(request.getMethod()).thenReturn("GET");

        filter.filter(request);

        assertTrue(obsContext.isObsRequest());
        assertEquals("/bucket/a%2Fb+%252F", obsContext.getRawPath());
        assertEquals("uploads=&x=1", obsContext.getRawQuery());
        verify(request).setProperty(HuaweiRequestClassifier.PROVIDER_PROPERTY, Boolean.TRUE);
        verify(request).setProperty(ObsRequestClassifier.REQUEST_PROPERTY, Boolean.TRUE);
        verify(verifier).verify(request, obsContext, authorization);
        ArgumentCaptor<URI> rewritten = ArgumentCaptor.forClass(URI.class);
        verify(request).setRequestUri(rewritten.capture());
        assertEquals(ObsRoutingFilter.INTERNAL_PATH, rewritten.getValue().getRawPath());
        assertEquals("uploads=&x=1", rewritten.getValue().getRawQuery());
    }
}
