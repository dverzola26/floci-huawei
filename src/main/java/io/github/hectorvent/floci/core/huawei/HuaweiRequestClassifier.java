package io.github.hectorvent.floci.core.huawei;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;

import java.util.Optional;

/** Classifies Huawei Cloud requests without relying on paths shared with inherited AWS APIs. */
@ApplicationScoped
public class HuaweiRequestClassifier {

    public static final String REQUEST_PROPERTY = "floci.huawei.auth-algorithm";

    public Optional<HuaweiAuthAlgorithm> classify(ContainerRequestContext context) {
        return classify(context == null ? null : context.getHeaderString("Authorization"));
    }

    public Optional<HuaweiAuthAlgorithm> classify(String authorization) {
        return HuaweiAuthAlgorithm.fromAuthorization(authorization);
    }

    public static boolean isHuaweiRequest(ContainerRequestContext context) {
        return context != null && context.getProperty(REQUEST_PROPERTY) instanceof HuaweiAuthAlgorithm;
    }
}
