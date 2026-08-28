package io.github.hectorvent.floci.core.huawei;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class HuaweiExceptionMapper implements ExceptionMapper<HuaweiException> {

    private static final Logger LOG = Logger.getLogger(HuaweiExceptionMapper.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final HuaweiRequestContext requestContext;

    @Inject
    public HuaweiExceptionMapper(HuaweiRequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @Override
    public Response toResponse(HuaweiException exception) {
        LOG.debugv("Mapping Huawei exception: {0} - {1}", exception.getErrorCode(), exception.getMessage());
        Response.ResponseBuilder response = Response.status(exception.getHttpStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(new HuaweiErrorResponse(exception.getErrorCode(), exception.getMessage()));
        if (requestContext.getRequestId() != null) {
            response.header(REQUEST_ID_HEADER, requestContext.getRequestId());
        }
        return response.build();
    }
}
