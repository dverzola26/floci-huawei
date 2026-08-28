package io.github.hectorvent.floci.core.huawei;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HuaweiExceptionMapperTest {

    @Test
    void mapsHuaweiErrorEnvelopeAndRequestId() {
        HuaweiRequestContext requestContext = new HuaweiRequestContext();
        requestContext.setRequestId("request-id-1");
        HuaweiExceptionMapper mapper = new HuaweiExceptionMapper(requestContext);

        Response response = mapper.toResponse(
                new HuaweiException("FLOCI.HUAWEI.TEST.0001", "Example failure", 400));

        assertEquals(400, response.getStatus());
        assertEquals("request-id-1", response.getHeaderString("X-Request-Id"));
        HuaweiErrorResponse entity = (HuaweiErrorResponse) response.getEntity();
        assertEquals("FLOCI.HUAWEI.TEST.0001", entity.errorCode());
        assertEquals("Example failure", entity.errorMessage());
    }
}
