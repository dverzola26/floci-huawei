package io.github.hectorvent.floci.core.huawei;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;

import java.util.Optional;

/** Classifies Huawei Cloud requests without relying on paths shared with inherited AWS APIs. */
@ApplicationScoped
public class HuaweiRequestClassifier {

    public static final String REQUEST_PROPERTY = "floci.huawei.auth-algorithm";
    public static final String PROVIDER_PROPERTY = "floci.huawei.request";

    public Optional<HuaweiAuthAlgorithm> classify(ContainerRequestContext context) {
        return classify(context == null ? null : context.getHeaderString("Authorization"));
    }

    public Optional<HuaweiAuthAlgorithm> classify(String authorization) {
        return HuaweiAuthAlgorithm.fromAuthorization(authorization);
    }

    public static boolean isHuaweiRequest(ContainerRequestContext context) {
        return context != null && (Boolean.TRUE.equals(context.getProperty(PROVIDER_PROPERTY))
                || context.getProperty(REQUEST_PROPERTY) instanceof HuaweiAuthAlgorithm);
    }

    public static boolean isCoreHuaweiRequest(ContainerRequestContext context) {
        return context != null && context.getProperty(REQUEST_PROPERTY) instanceof HuaweiAuthAlgorithm;
    }

    public static void markHuaweiRequest(ContainerRequestContext context) {
        context.setProperty(PROVIDER_PROPERTY, Boolean.TRUE);
    }
}
