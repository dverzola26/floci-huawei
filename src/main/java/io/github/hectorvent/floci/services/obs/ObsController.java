package io.github.hectorvent.floci.services.obs;

import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

/** Internal-only OBS dispatcher. Bucket state is introduced in the next focused PR. */
@Path(ObsRoutingFilter.INTERNAL_PATH)
public class ObsController {

    private final ObsRequestContext requestContext;

    @Inject
    public ObsController(ObsRequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @GET
    public Response get() {
        return dispatch();
    }

    @HEAD
    public Response head() {
        return dispatch();
    }

    @PUT
    public Response put() {
        return dispatch();
    }

    @POST
    public Response post() {
        return dispatch();
    }

    @DELETE
    public Response delete() {
        return dispatch();
    }

    @OPTIONS
    public Response options() {
        return dispatch();
    }

    private Response dispatch() {
        if (!requestContext.isObsRequest()) {
            throw new NotFoundException();
        }
        String path = requestContext.getRawPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            throw new ObsException("NotImplemented", "This OBS operation is not implemented.", 501);
        }
        throw new ObsException("NoSuchBucket", "The specified bucket does not exist.", 404);
    }
}
