package io.github.hectorvent.floci.core.huawei;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Adds the Huawei Cloud request identifier to Huawei responses only. */
@Provider
public class HuaweiRequestIdFilter implements ContainerResponseFilter {

    static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final HuaweiRequestContext requestContext;

    @Inject
    public HuaweiRequestIdFilter(HuaweiRequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (!HuaweiRequestClassifier.isCoreHuaweiRequest(request)) {
            return;
        }
        if (!response.getHeaders().containsKey(REQUEST_ID_HEADER) && requestContext.getRequestId() != null) {
            response.getHeaders().putSingle(REQUEST_ID_HEADER, requestContext.getRequestId());
        }
    }
}
