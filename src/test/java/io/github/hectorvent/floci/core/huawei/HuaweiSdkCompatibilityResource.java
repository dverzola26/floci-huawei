package io.github.hectorvent.floci.core.huawei;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** Test-only endpoint used to prove that official SDK requests cross the full HTTP filter chain. */
@Path("/resources")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class HuaweiSdkCompatibilityResource {

    private final HuaweiRequestContext requestContext;

    @Inject
    public HuaweiSdkCompatibilityResource(HuaweiRequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @GET
    public ProbeResponse get(@QueryParam("size") String size) {
        return response("GET", size, null);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public ProbeResponse post(@QueryParam("size") String size, String body) {
        return response("POST", size, body);
    }

    private ProbeResponse response(String method, String size, String body) {
        return new ProbeResponse(
                method,
                size,
                body,
                requestContext.getAccessKey(),
                requestContext.getRegionId(),
                requestContext.getProjectId(),
                requestContext.getDomainId(),
                requestContext.getServiceName(),
                requestContext.getAuthenticationAlgorithm().authorizationPrefix(),
                requestContext.getRequestId());
    }

    public record ProbeResponse(
            String method,
            String size,
            String body,
            String accessKey,
            String regionId,
            String projectId,
            String domainId,
            String serviceName,
            String authenticationAlgorithm,
            String requestId) {
    }
}
