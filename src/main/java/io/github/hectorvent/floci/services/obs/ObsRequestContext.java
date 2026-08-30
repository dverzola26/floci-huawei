package io.github.hectorvent.floci.services.obs;

import jakarta.enterprise.context.RequestScoped;

/** Request-scoped OBS routing and authentication state. */
@RequestScoped
public class ObsRequestContext {

    private boolean obsRequest;
    private String requestId;
    private String method;
    private String rawPath;
    private String rawQuery;
    private String accessKey;

    public boolean isObsRequest() {
        return obsRequest;
    }

    public void setObsRequest(boolean obsRequest) {
        this.obsRequest = obsRequest;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getRawPath() {
        return rawPath;
    }

    public void setRawPath(String rawPath) {
        this.rawPath = rawPath;
    }

    public String getRawQuery() {
        return rawQuery;
    }

    public void setRawQuery(String rawQuery) {
        this.rawQuery = rawQuery;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }
}
