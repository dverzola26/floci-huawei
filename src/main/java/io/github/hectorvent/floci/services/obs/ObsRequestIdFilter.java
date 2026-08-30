package io.github.hectorvent.floci.services.obs;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Adds the OBS request ID without exposing inherited AWS identifiers. */
@Provider
public class ObsRequestIdFilter implements ContainerResponseFilter {

    private final ObsRequestContext requestContext;

    @Inject
    public ObsRequestIdFilter(ObsRequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (!ObsRequestClassifier.isRouted(request) || requestContext.getRequestId() == null) {
            return;
        }
        if (!response.getHeaders().containsKey(ObsExceptionMapper.REQUEST_ID_HEADER)) {
            response.getHeaders().putSingle(ObsExceptionMapper.REQUEST_ID_HEADER, requestContext.getRequestId());
        }
    }
}
