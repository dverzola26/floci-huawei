package io.github.hectorvent.floci.services.obs;

import io.github.hectorvent.floci.core.common.XmlBuilder;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/** Maps OBS errors to the REST/XML wire contract. */
@Provider
public class ObsExceptionMapper implements ExceptionMapper<ObsException> {

    private static final Logger LOG = Logger.getLogger(ObsExceptionMapper.class);
    public static final String REQUEST_ID_HEADER = "x-obs-request-id";

    private final ObsRequestContext requestContext;

    @Inject
    public ObsExceptionMapper(ObsRequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @Override
    public Response toResponse(ObsException exception) {
        LOG.debugv("Mapping OBS exception: {0} - {1}", exception.getErrorCode(), exception.getMessage());
        Response.ResponseBuilder response = Response.status(exception.getHttpStatus())
                .type(MediaType.APPLICATION_XML_TYPE)
                .header(REQUEST_ID_HEADER, requestContext.getRequestId());
        if (!"HEAD".equalsIgnoreCase(requestContext.getMethod())) {
            response.entity(new XmlBuilder()
                    .start("Error")
                    .elem("Code", exception.getErrorCode())
                    .elem("Message", exception.getMessage())
                    .elem("RequestId", requestContext.getRequestId())
                    .elem("HostId", requestContext.getRequestId())
                    .elem("Resource", requestContext.getRawPath())
                    .end("Error")
                    .build());
        }
        return response.build();
    }
}
