package io.github.hectorvent.floci.services.obs;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.huawei.HuaweiRequestClassifier;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

import java.net.URI;
import java.util.UUID;

/** Claims OBS-authenticated public paths before inherited AWS path routers run. */
@Provider
@PreMatching
@ApplicationScoped
@Priority(1)
public class ObsRoutingFilter implements ContainerRequestFilter {

    public static final String INTERNAL_PATH = "/_floci/internal/huawei/obs";

    private final EmulatorConfig config;
    private final ObsRequestClassifier classifier;
    private final ObsAuthorizationParser authorizationParser;
    private final ObsSignatureVerifier signatureVerifier;
    private final ObsRequestContext requestContext;

    @Inject
    public ObsRoutingFilter(EmulatorConfig config,
                            ObsRequestClassifier classifier,
                            ObsAuthorizationParser authorizationParser,
                            ObsSignatureVerifier signatureVerifier,
                            ObsRequestContext requestContext) {
        this.config = config;
        this.classifier = classifier;
        this.authorizationParser = authorizationParser;
        this.signatureVerifier = signatureVerifier;
        this.requestContext = requestContext;
    }

    @Override
    public void filter(ContainerRequestContext context) {
        URI uri = context.getUriInfo().getRequestUri();
        if (!config.huawei().enabled() || INTERNAL_PATH.equals(uri.getRawPath())
                || !classifier.isObsRequest(context)) {
            return;
        }

        HuaweiRequestClassifier.markHuaweiRequest(context);
        context.setProperty(ObsRequestClassifier.REQUEST_PROPERTY, Boolean.TRUE);
        requestContext.setObsRequest(true);
        requestContext.setRequestId(UUID.randomUUID().toString());
        requestContext.setMethod(context.getMethod());
        requestContext.setRawPath(uri.getRawPath());
        requestContext.setRawQuery(uri.getRawQuery());

        ObsAuthorization authorization = authorizationParser.parse(context);
        requestContext.setAccessKey(authorization.accessKey());
        signatureVerifier.verify(context, requestContext, authorization);
        context.setRequestUri(rewrite(uri));
    }

    static URI rewrite(URI uri) {
        StringBuilder value = new StringBuilder();
        if (uri.getScheme() != null) {
            value.append(uri.getScheme()).append("://").append(uri.getRawAuthority());
        }
        value.append(INTERNAL_PATH);
        if (uri.getRawQuery() != null) {
            value.append('?').append(uri.getRawQuery());
        }
        return URI.create(value.toString());
    }
}
