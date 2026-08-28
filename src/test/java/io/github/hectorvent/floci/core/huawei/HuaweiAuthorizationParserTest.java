package io.github.hectorvent.floci.core.huawei;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HuaweiAuthorizationParserTest {

    private final HuaweiAuthorizationParser parser = new HuaweiAuthorizationParser();

    @Test
    void parsesStandardAuthorization() {
        HuaweiAuthorization authorization = parser.parse(
                "SDK-HMAC-SHA256 Access=AccessKey, SignedHeaders=x-sdk-date;host, Signature="
                        + "fd95e7da6f695cfb4cabbb9d6b0968aec155bc576b064835282473539ae9ea1d");

        assertEquals(HuaweiAuthAlgorithm.SDK_HMAC_SHA256, authorization.algorithm());
        assertEquals("AccessKey", authorization.accessKey());
        assertEquals(java.util.List.of("host", "x-sdk-date"), authorization.signedHeaders());
        assertTrue(authorization.region().isEmpty());
    }

    @Test
    void parsesDerivedCredentialScopeForRequestContext() {
        HuaweiAuthorization authorization = parser.parse(
                "V11-HMAC-SHA256 Credential=AccessKey/20191115/region-id-1/service, "
                        + "SignedHeaders=host;x-sdk-date, Signature="
                        + "c63981314b17efeca4c2577e3e22ecfd3831e0c87eb87277271e32587207dd2c");

        assertEquals(HuaweiAuthAlgorithm.V11_HMAC_SHA256, authorization.algorithm());
        assertEquals("20191115", authorization.date().orElseThrow());
        assertEquals("region-id-1", authorization.region().orElseThrow());
        assertEquals("service", authorization.service().orElseThrow());
    }

    @Test
    void rejectsMissingDuplicateAndMalformedFields() {
        assertMalformed("SDK-HMAC-SHA256 Access=test, SignedHeaders=host;x-sdk-date");
        assertMalformed("SDK-HMAC-SHA256 Access=test, Access=other, "
                + "SignedHeaders=host;x-sdk-date, Signature=" + "a".repeat(64));
        assertMalformed("SDK-HMAC-SHA256 Access=test, SignedHeaders=host;x_sdk_date, Signature="
                + "a".repeat(64));
        assertMalformed("SDK-HMAC-SHA256 Access=test, SignedHeaders=host;x-sdk-date, Signature=abc");
        assertMalformed("V11-HMAC-SHA256 Credential=test/not-a-date/region/service, "
                + "SignedHeaders=host;x-sdk-date, Signature=" + "a".repeat(64));
    }

    private void assertMalformed(String value) {
        HuaweiException exception = assertThrows(HuaweiException.class, () -> parser.parse(value));
        assertEquals(401, exception.getHttpStatus());
        assertEquals(HuaweiAuthorizationParser.MALFORMED_AUTH_CODE, exception.getErrorCode());
    }
}
