package io.github.hectorvent.floci.core.huawei;

import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the canonical request used by Huawei Cloud SDK-HMAC-SHA256 signing. */
@ApplicationScoped
public class HuaweiCanonicalRequest {

    static final String EMPTY_BODY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final HexFormat HEX = HexFormat.of();

    public Canonicalized canonicalize(String method,
                                      URI uri,
                                      Map<String, ? extends List<String>> headers,
                                      List<String> signedHeaders,
                                      byte[] body) {
        String canonicalHeaders = canonicalHeaders(headers, signedHeaders);
        String signedHeaderNames = String.join(";", signedHeaders);
        String payloadHash = payloadHash(headers, body);
        String value = String.join("\n",
                method.toUpperCase(Locale.ROOT),
                canonicalUri(uri.getRawPath()),
                canonicalQuery(uri.getRawQuery()),
                canonicalHeaders,
                signedHeaderNames,
                payloadHash);
        return new Canonicalized(value, payloadHash);
    }

    static String canonicalUri(String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || rawPath.equals("/")) {
            return "/";
        }

        StringBuilder result = new StringBuilder();
        for (String segment : rawPath.split("/", -1)) {
            result.append(percentEncode(percentDecode(segment))).append('/');
        }
        if (result.length() > 1 && result.charAt(result.length() - 2) == '/') {
            result.setLength(result.length() - 1);
        }
        return result.toString();
    }

    static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }

        List<QueryValue> values = new ArrayList<>();
        for (String component : rawQuery.split("&", -1)) {
            int equals = component.indexOf('=');
            String rawName = equals < 0 ? component : component.substring(0, equals);
            String rawValue = equals < 0 ? "" : component.substring(equals + 1);
            values.add(new QueryValue(
                    percentEncode(percentDecode(rawName)),
                    percentEncode(percentDecode(rawValue))));
        }
        values.sort(Comparator.comparing(QueryValue::name).thenComparing(QueryValue::value));

        List<String> encoded = new ArrayList<>(values.size());
        for (QueryValue value : values) {
            encoded.add(value.name() + "=" + value.value());
        }
        return String.join("&", encoded);
    }

    private static String canonicalHeaders(Map<String, ? extends List<String>> headers,
                                           List<String> signedHeaders) {
        StringBuilder result = new StringBuilder();
        for (String name : signedHeaders) {
            List<String> values = headerValues(headers, name);
            if (values == null || values.isEmpty()) {
                throw HuaweiAuthorizationParser.malformed("The signed header " + name + " is missing.");
            }
            List<String> trimmed = values.stream().map(String::trim).toList();
            result.append(name).append(':').append(String.join(",", trimmed)).append('\n');
        }
        return result.toString();
    }

    private static String payloadHash(Map<String, ? extends List<String>> headers, byte[] body) {
        List<String> declaredHash = headerValues(headers, "x-sdk-content-sha256");
        if (declaredHash != null && !declaredHash.isEmpty() && !declaredHash.getFirst().isBlank()) {
            return declaredHash.getFirst().trim();
        }
        if (body == null || body.length == 0) {
            return EMPTY_BODY_SHA256;
        }
        return sha256Hex(body);
    }

    private static List<String> headerValues(Map<String, ? extends List<String>> headers, String name) {
        for (Map.Entry<String, ? extends List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return List.copyOf(entry.getValue());
            }
        }
        return null;
    }

    static String sha256Hex(byte[] value) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String percentDecode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw HuaweiAuthorizationParser.malformed("The request URI contains invalid percent encoding.");
        }
    }

    private static String percentEncode(String value) {
        StringBuilder result = new StringBuilder();
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = current & 0xff;
            if ((unsigned >= 'a' && unsigned <= 'z')
                    || (unsigned >= 'A' && unsigned <= 'Z')
                    || (unsigned >= '0' && unsigned <= '9')
                    || unsigned == '-' || unsigned == '_' || unsigned == '.' || unsigned == '~') {
                result.append((char) unsigned);
            } else {
                result.append('%');
                result.append(Character.toUpperCase(Character.forDigit(unsigned >>> 4, 16)));
                result.append(Character.toUpperCase(Character.forDigit(unsigned & 0x0f, 16)));
            }
        }
        return result.toString();
    }

    public record Canonicalized(String value, String payloadHash) {
    }

    private record QueryValue(String name, String value) {
    }
}
