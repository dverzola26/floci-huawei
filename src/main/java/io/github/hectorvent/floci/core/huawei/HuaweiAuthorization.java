package io.github.hectorvent.floci.core.huawei;

import java.util.List;
import java.util.Optional;

/** Immutable representation of a Huawei Cloud Authorization header. */
public record HuaweiAuthorization(
        HuaweiAuthAlgorithm algorithm,
        String accessKey,
        List<String> signedHeaders,
        String signature,
        Optional<String> date,
        Optional<String> region,
        Optional<String> service) {

    public HuaweiAuthorization {
        signedHeaders = List.copyOf(signedHeaders);
        date = date == null ? Optional.empty() : date;
        region = region == null ? Optional.empty() : region;
        service = service == null ? Optional.empty() : service;
    }
}
