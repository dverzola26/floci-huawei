package io.github.hectorvent.floci.core.huawei;

import io.github.hectorvent.floci.config.EmulatorConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.UUID;

/** Initializes Huawei request context before the inherited AWS context filter runs. */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION - 200)
public class HuaweiRequestContextFilter implements ContainerRequestFilter {

    private final EmulatorConfig.HuaweiConfig config;
    private final HuaweiRequestClassifier classifier;
    private final HuaweiAuthorizationParser authorizationParser;
    private final HuaweiSignatureVerifier signatureVerifier;
    private final HuaweiRequestContext requestContext;

    @Inject
    public HuaweiRequestContextFilter(EmulatorConfig config,
                                      HuaweiRequestClassifier classifier,
                                      HuaweiAuthorizationParser authorizationParser,
                                      HuaweiSignatureVerifier signatureVerifier,
                                      HuaweiRequestContext requestContext) {
        this(config.huawei(), classifier, authorizationParser, signatureVerifier, requestContext);
    }

    HuaweiRequestContextFilter(EmulatorConfig.HuaweiConfig config,
                               HuaweiRequestClassifier classifier,
                               HuaweiAuthorizationParser authorizationParser,
                               HuaweiSignatureVerifier signatureVerifier,
                               HuaweiRequestContext requestContext) {
        this.config = config;
        this.classifier = classifier;
        this.authorizationParser = authorizationParser;
        this.signatureVerifier = signatureVerifier;
        this.requestContext = requestContext;
    }

    @Override
    public void filter(ContainerRequestContext context) {
        if (!config.enabled()) {
            return;
        }

        classifier.classify(context).ifPresent(algorithm -> initialize(context, algorithm));
    }

    private void initialize(ContainerRequestContext context, HuaweiAuthAlgorithm algorithm) {
        context.setProperty(HuaweiRequestClassifier.REQUEST_PROPERTY, algorithm);
        requestContext.setHuaweiRequest(true);
        requestContext.setRequestId(UUID.randomUUID().toString());
        requestContext.setRegionId(config.defaultRegion());
        requestContext.setProjectId(headerOrDefault(context, "X-Project-Id", config.defaultProjectId()));
        requestContext.setDomainId(headerOrDefault(context, "X-Domain-Id", config.defaultDomainId()));
        requestContext.setAuthenticationAlgorithm(algorithm);

        if (!algorithm.supported()) {
            throw new HuaweiException(
                    "FLOCI.HUAWEI.AUTH.0002",
                    "The authorization algorithm " + algorithm.authorizationPrefix() + " is not supported.",
                    501);
        }

        HuaweiAuthorization authorization = authorizationParser.parse(context.getHeaderString("Authorization"));
        requestContext.setAccessKey(authorization.accessKey());
        authorization.region().ifPresent(requestContext::setRegionId);
        authorization.service().ifPresent(requestContext::setServiceName);
        signatureVerifier.verifyIfEnabled(context, authorization);
    }

    private static String headerOrDefault(ContainerRequestContext context, String name, String fallback) {
        String value = context.getHeaderString(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
