package io.github.hectorvent.floci.core.huawei;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict parser for standard and derived Huawei Cloud AK/SK authorization headers. */
@ApplicationScoped
public class HuaweiAuthorizationParser {

    static final String MALFORMED_AUTH_CODE = "FLOCI.HUAWEI.AUTH.0003";

    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern SCOPE_DATE = Pattern.compile("[0-9]{8}");
    private static final Pattern HEADER_NAME = Pattern.compile("[a-z0-9-]+");

    public HuaweiAuthorization parse(String value) {
        if (value == null || value.isBlank()) {
            throw malformed("The Authorization header is missing.");
        }

        int separator = value.indexOf(' ');
        if (separator <= 0 || separator == value.length() - 1) {
            throw malformed("The Authorization header is malformed.");
        }

        HuaweiAuthAlgorithm algorithm = HuaweiAuthAlgorithm.fromAuthorization(value)
                .orElseThrow(() -> malformed("The authorization algorithm is not recognized."));
        Map<String, String> fields = parseFields(value.substring(separator + 1));

        return switch (algorithm) {
            case SDK_HMAC_SHA256 -> parseStandard(algorithm, fields);
            case V11_HMAC_SHA256 -> parseDerived(algorithm, fields);
            default -> parseUnsupported(algorithm, fields);
        };
    }

    private HuaweiAuthorization parseStandard(HuaweiAuthAlgorithm algorithm, Map<String, String> fields) {
        requireOnly(fields, Set.of("Access", "SignedHeaders", "Signature"));
        return authorization(
                algorithm,
                required(fields, "Access"),
                required(fields, "SignedHeaders"),
                required(fields, "Signature"),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    private HuaweiAuthorization parseDerived(HuaweiAuthAlgorithm algorithm, Map<String, String> fields) {
        requireOnly(fields, Set.of("Credential", "SignedHeaders", "Signature"));
        String[] credential = required(fields, "Credential").split("/", -1);
        if (credential.length != 4
                || credential[0].isBlank()
                || !SCOPE_DATE.matcher(credential[1]).matches()
                || credential[2].isBlank()
                || credential[3].isBlank()) {
            throw malformed("The derived Credential scope is malformed.");
        }
        return authorization(
                algorithm,
                credential[0],
                required(fields, "SignedHeaders"),
                required(fields, "Signature"),
                Optional.of(credential[1]),
                Optional.of(credential[2]),
                Optional.of(credential[3]));
    }

    private HuaweiAuthorization parseUnsupported(HuaweiAuthAlgorithm algorithm, Map<String, String> fields) {
        String accessKey = fields.getOrDefault("Access", fields.getOrDefault("Credential", ""));
        return new HuaweiAuthorization(
                algorithm, accessKey, List.of(), "", Optional.empty(), Optional.empty(), Optional.empty());
    }

    private HuaweiAuthorization authorization(HuaweiAuthAlgorithm algorithm,
                                              String accessKey,
                                              String signedHeaderValue,
                                              String signature,
                                              Optional<String> date,
                                              Optional<String> region,
                                              Optional<String> service) {
        if (accessKey.isBlank() || accessKey.chars().anyMatch(Character::isWhitespace)) {
            throw malformed("The access key is malformed.");
        }
        if (!SIGNATURE.matcher(signature).matches()) {
            throw malformed("The request signature must contain 64 hexadecimal characters.");
        }

        List<String> signedHeaders = new ArrayList<>();
        Set<String> uniqueHeaders = new HashSet<>();
        for (String name : signedHeaderValue.split(";", -1)) {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!name.equals(normalized)
                    || !HEADER_NAME.matcher(normalized).matches()
                    || !uniqueHeaders.add(normalized)) {
                throw malformed("SignedHeaders contains an invalid or duplicate header name.");
            }
            signedHeaders.add(normalized);
        }
        signedHeaders.sort(String::compareTo);
        if (!signedHeaders.contains("host") || !signedHeaders.contains("x-sdk-date")) {
            throw malformed("SignedHeaders must contain host and x-sdk-date.");
        }

        return new HuaweiAuthorization(
                algorithm, accessKey, signedHeaders, signature.toLowerCase(Locale.ROOT), date, region, service);
    }

    private static Map<String, String> parseFields(String value) {
        Map<String, String> fields = new HashMap<>();
        for (String component : value.split("\\s*,\\s*", -1)) {
            int equals = component.indexOf('=');
            if (equals <= 0 || equals == component.length() - 1) {
                throw malformed("The Authorization header contains a malformed signing field.");
            }
            String previous = fields.put(component.substring(0, equals), component.substring(equals + 1));
            if (previous != null) {
                throw malformed("The Authorization header contains a duplicate signing field.");
            }
        }
        return fields;
    }

    private static void requireOnly(Map<String, String> fields, Set<String> expected) {
        if (!fields.keySet().equals(expected)) {
            throw malformed("The Authorization header does not contain the required signing fields.");
        }
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw malformed("The Authorization field " + name + " is missing.");
        }
        return value;
    }

    static HuaweiException malformed(String message) {
        return new HuaweiException(MALFORMED_AUTH_CODE, message, 401);
    }
}
