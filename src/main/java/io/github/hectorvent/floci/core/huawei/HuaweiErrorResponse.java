package io.github.hectorvent.floci.core.huawei;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

/** Common Huawei Cloud error envelope understood by the official SDKs. */
@RegisterForReflection
public record HuaweiErrorResponse(
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error_msg") String errorMessage) {
}
