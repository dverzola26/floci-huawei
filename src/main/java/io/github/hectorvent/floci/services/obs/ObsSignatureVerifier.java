package io.github.hectorvent.floci.services.obs;

import io.github.hectorvent.floci.config.EmulatorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/** Verifies legacy OBS v2 HMAC-SHA1 header and signed-query authentication. */
@ApplicationScoped
public class ObsSignatureVerifier {

    private final EmulatorConfig.HuaweiAuthConfig config;
    private final ObsCanonicalRequest canonicalRequest;
    private final Clock clock;

    @Inject
    public ObsSignatureVerifier(EmulatorConfig emulatorConfig, ObsCanonicalRequest canonicalRequest) {
        this(emulatorConfig.huawei().auth(), canonicalRequest, Clock.systemUTC());
    }

    ObsSignatureVerifier(EmulatorConfig.HuaweiAuthConfig config,
                         ObsCanonicalRequest canonicalRequest,
                         Clock clock) {
        this.config = config;
        this.canonicalRequest = canonicalRequest;
        this.clock = clock;
    }

    public void verify(ContainerRequestContext request,
                       ObsRequestContext context,
                       ObsAuthorization authorization) {
        if (!config.validateSignatures()) {
            return;
        }
        validateTime(request, authorization);
        if (!MessageDigest.isEqual(config.accessKey().getBytes(StandardCharsets.UTF_8),
                authorization.accessKey().getBytes(StandardCharsets.UTF_8))) {
            throw new ObsException("InvalidAccessKeyId",
                    "The Access Key Id you provided does not exist in our records.", 403);
        }
        String expected = sign(canonicalRequest.build(request, context, authorization), config.secretKey());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                authorization.signature().getBytes(StandardCharsets.UTF_8))) {
            throw new ObsException("SignatureDoesNotMatch",
                    "The request signature we calculated does not match the signature you provided.", 403);
        }
    }

    private void validateTime(ContainerRequestContext request, ObsAuthorization authorization) {
        Instant now = clock.instant();
        if (authorization.querySigned()) {
            if (authorization.expires() < now.getEpochSecond()) {
                throw new ObsException("AccessDenied", "Request has expired.", 403);
            }
            return;
        }
        String value = request.getHeaderString("x-obs-date");
        if (value == null || value.isBlank()) {
            value = request.getHeaderString("Date");
        }
        if (value == null || value.isBlank()) {
            throw new ObsException("AccessDenied", "A valid Date or x-obs-date header is required.", 403);
        }
        try {
            Instant requestTime = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            long skew = Math.abs(Duration.between(requestTime, now).getSeconds());
            if (skew > config.maxClockSkewSeconds()) {
                throw new ObsException("RequestTimeTooSkewed",
                        "The difference between the request time and the server time is too large.", 403);
            }
        } catch (DateTimeParseException exception) {
            throw new ObsException("AccessDenied", "The request date is invalid.", 403);
        }
    }

    static String sign(String canonical, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA1 is unavailable", exception);
        }
    }
}
