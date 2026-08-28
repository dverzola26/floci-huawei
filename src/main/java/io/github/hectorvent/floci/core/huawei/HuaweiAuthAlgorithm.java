package io.github.hectorvent.floci.core.huawei;

import java.util.Arrays;
import java.util.Optional;

/** Huawei Cloud authorization algorithms recognized by the emulator. */
public enum HuaweiAuthAlgorithm {
    SDK_HMAC_SHA256("SDK-HMAC-SHA256", true),
    V11_HMAC_SHA256("V11-HMAC-SHA256", true),
    SDK_HMAC_SM3("SDK-HMAC-SM3", false),
    SDK_ECDSA_P256_SHA256("SDK-ECDSA-P256-SHA256", false),
    SDK_SM2_SM3("SDK-SM2-SM3", false);

    private final String authorizationPrefix;
    private final boolean supported;

    HuaweiAuthAlgorithm(String authorizationPrefix, boolean supported) {
        this.authorizationPrefix = authorizationPrefix;
        this.supported = supported;
    }

    public String authorizationPrefix() {
        return authorizationPrefix;
    }

    public boolean supported() {
        return supported;
    }

    public static Optional<HuaweiAuthAlgorithm> fromAuthorization(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(algorithm -> authorization.startsWith(algorithm.authorizationPrefix + " "))
                .findFirst();
    }
}
