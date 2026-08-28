package io.github.hectorvent.floci.core.huawei;

import io.github.hectorvent.floci.config.EmulatorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Optionally validates standard and derived Huawei Cloud HMAC request signatures. */
@ApplicationScoped
public class HuaweiSignatureVerifier {

    static final String INVALID_SIGNATURE_CODE = "FLOCI.HUAWEI.AUTH.0001";
    static final String UNKNOWN_ACCESS_KEY_CODE = "FLOCI.HUAWEI.AUTH.0004";
    static final String INVALID_DATE_CODE = "FLOCI.HUAWEI.AUTH.0005";

    private static final HexFormat HEX = HexFormat.of();
    private static final DateTimeFormatter SDK_DATE = new DateTimeFormatterBuilder()
            .appendPattern("uuuuMMdd'T'HHmmss'Z'")
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final EmulatorConfig.HuaweiAuthConfig config;
    private final HuaweiCanonicalRequest canonicalRequest;
    private final Clock clock;

    @Inject
    public HuaweiSignatureVerifier(EmulatorConfig config, HuaweiCanonicalRequest canonicalRequest) {
        this(config.huawei().auth(), canonicalRequest, Clock.systemUTC());
    }

    HuaweiSignatureVerifier(EmulatorConfig.HuaweiAuthConfig config,
                            HuaweiCanonicalRequest canonicalRequest,
                            Clock clock) {
        this.config = config;
        this.canonicalRequest = canonicalRequest;
        this.clock = clock;
    }

    public void verifyIfEnabled(ContainerRequestContext context, HuaweiAuthorization authorization) {
        if (!config.validateSignatures()) {
            return;
        }
        if (!constantTimeEquals(authorization.accessKey(), config.accessKey())) {
            throw new HuaweiException(
                    UNKNOWN_ACCESS_KEY_CODE, "The configured access key does not match the request.", 401);
        }

        String date = context.getHeaderString("X-Sdk-Date");
        validateDate(date);
        byte[] body = readAndRestoreBody(context);
        URI uri = context.getUriInfo().getRequestUri();
        String expected = switch (authorization.algorithm()) {
            case SDK_HMAC_SHA256 -> calculateSignature(
                    context.getMethod(), uri, context.getHeaders(), body,
                    authorization, date, config.secretKey());
            case V11_HMAC_SHA256 -> calculateDerivedSignature(
                    context.getMethod(), uri, context.getHeaders(), body,
                    authorization, date, config.secretKey());
            default -> throw new HuaweiException(
                    "FLOCI.HUAWEI.AUTH.0002",
                    "Signature validation for " + authorization.algorithm().authorizationPrefix()
                            + " is not implemented.",
                    501);
        };

        if (!constantTimeHexEquals(expected, authorization.signature())) {
            throw new HuaweiException(INVALID_SIGNATURE_CODE, "The request signature is invalid.", 401);
        }
    }

    String calculateSignature(String method,
                              URI uri,
                              Map<String, ? extends List<String>> headers,
                              byte[] body,
                              HuaweiAuthorization authorization,
                              String sdkDate,
                              String secretKey) {
        HuaweiCanonicalRequest.Canonicalized canonical = canonicalRequest.canonicalize(
                method, uri, headers, authorization.signedHeaders(), body);
        String stringToSign = String.join("\n",
                HuaweiAuthAlgorithm.SDK_HMAC_SHA256.authorizationPrefix(),
                sdkDate,
                HuaweiCanonicalRequest.sha256Hex(canonical.value().getBytes(StandardCharsets.UTF_8)));
        return HEX.formatHex(hmacSha256(secretKey.getBytes(StandardCharsets.UTF_8),
                stringToSign.getBytes(StandardCharsets.UTF_8)));
    }

    String calculateDerivedSignature(String method,
                                     URI uri,
                                     Map<String, ? extends List<String>> headers,
                                     byte[] body,
                                     HuaweiAuthorization authorization,
                                     String sdkDate,
                                     String secretKey) {
        String scope = derivedScope(authorization, sdkDate);
        HuaweiCanonicalRequest.Canonicalized canonical = canonicalRequest.canonicalize(
                method, uri, headers, authorization.signedHeaders(), body);
        String stringToSign = String.join("\n",
                HuaweiAuthAlgorithm.V11_HMAC_SHA256.authorizationPrefix(),
                sdkDate,
                scope,
                HuaweiCanonicalRequest.sha256Hex(canonical.value().getBytes(StandardCharsets.UTF_8)));
        String derivedHexKey = HuaweiHkdf.deriveHexKey(authorization.accessKey(), secretKey, scope);
        return HEX.formatHex(hmacSha256(derivedHexKey.getBytes(StandardCharsets.UTF_8),
                stringToSign.getBytes(StandardCharsets.UTF_8)));
    }

    private static String derivedScope(HuaweiAuthorization authorization, String sdkDate) {
        String date = authorization.date().orElseThrow(
                () -> HuaweiAuthorizationParser.malformed("The derived credential date is missing."));
        String region = authorization.region().orElseThrow(
                () -> HuaweiAuthorizationParser.malformed("The derived credential region is missing."));
        String service = authorization.service().orElseThrow(
                () -> HuaweiAuthorizationParser.malformed("The derived credential service is missing."));
        if (sdkDate == null || sdkDate.length() < 8 || !date.equals(sdkDate.substring(0, 8))) {
            throw new HuaweiException(
                    INVALID_DATE_CODE,
                    "The derived credential date does not match X-Sdk-Date.",
                    401);
        }
        return date + "/" + region + "/" + service;
    }

    private void validateDate(String value) {
        if (value == null || value.isBlank()) {
            throw new HuaweiException(INVALID_DATE_CODE, "The X-Sdk-Date header is missing.", 401);
        }
        final Instant requestTime;
        try {
            requestTime = LocalDateTime.parse(value, SDK_DATE).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new HuaweiException(INVALID_DATE_CODE, "The X-Sdk-Date header is malformed.", 401);
        }
        long skew = Duration.between(requestTime, clock.instant()).abs().getSeconds();
        if (skew > config.maxClockSkewSeconds()) {
            throw new HuaweiException(
                    INVALID_DATE_CODE, "The request date is outside the allowed clock skew.", 401);
        }
    }

    private static byte[] readAndRestoreBody(ContainerRequestContext context) {
        if (context.getEntityStream() == null) {
            return new byte[0];
        }
        try {
            byte[] body = context.getEntityStream().readAllBytes();
            context.setEntityStream(new ByteArrayInputStream(body));
            return body;
        } catch (IOException e) {
            throw new HuaweiException(
                    "FLOCI.HUAWEI.AUTH.0006", "The request body could not be read for validation.", 400);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean constantTimeHexEquals(String expected, String actual) {
        try {
            return MessageDigest.isEqual(HEX.parseHex(expected), HEX.parseHex(actual));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
