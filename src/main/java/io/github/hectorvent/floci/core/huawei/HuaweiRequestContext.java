package io.github.hectorvent.floci.core.huawei;

import jakarta.enterprise.context.RequestScoped;

/** Request-scoped Huawei Cloud identity and tenancy metadata. */
@RequestScoped
public class HuaweiRequestContext {

    private boolean huaweiRequest;
    private String requestId;
    private String accessKey;
    private String regionId;
    private String projectId;
    private String domainId;
    private String serviceName;
    private HuaweiAuthAlgorithm authenticationAlgorithm;

    public boolean isHuaweiRequest() {
        return huaweiRequest;
    }

    public void setHuaweiRequest(boolean huaweiRequest) {
        this.huaweiRequest = huaweiRequest;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getDomainId() {
        return domainId;
    }

    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public HuaweiAuthAlgorithm getAuthenticationAlgorithm() {
        return authenticationAlgorithm;
    }

    public void setAuthenticationAlgorithm(HuaweiAuthAlgorithm authenticationAlgorithm) {
        this.authenticationAlgorithm = authenticationAlgorithm;
    }
}
