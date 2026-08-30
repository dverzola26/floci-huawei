package io.github.hectorvent.floci.services.obs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedMap;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Builds the legacy OBS v2 HMAC-SHA1 canonical string. */
@ApplicationScoped
public class ObsCanonicalRequest {

    private static final Set<String> SIGNED_SUBRESOURCES = Set.of("uploads", "uploadId", "partNumber");

    public String build(ContainerRequestContext request,
                        ObsRequestContext context,
                        ObsAuthorization authorization) {
        String date = authorization.querySigned()
                ? Long.toString(authorization.expires())
                : canonicalDate(request);
        return context.getMethod() + '\n'
                + header(request, "Content-MD5") + '\n'
                + header(request, "Content-Type") + '\n'
                + date + '\n'
                + canonicalObsHeaders(request.getHeaders())
                + canonicalResource(context.getRawPath(), context.getRawQuery());
    }

    static String canonicalDate(ContainerRequestContext request) {
        return blankToEmpty(request.getHeaderString("x-obs-date")).isEmpty()
                ? header(request, "Date")
                : "";
    }

    static String canonicalObsHeaders(MultivaluedMap<String, String> headers) {
        Map<String, List<String>> canonical = new TreeMap<>();
        headers.forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("x-obs-")) {
                canonical.computeIfAbsent(lower, ignored -> new ArrayList<>())
                        .addAll(values.stream().map(ObsCanonicalRequest::fold).toList());
            }
        });
        return canonical.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + String.join(",", entry.getValue()) + "\n")
                .collect(Collectors.joining());
    }

    static String canonicalResource(String rawPath, String rawQuery) {
        String path = rawPath == null || rawPath.isBlank() ? "/" : rawPath;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (rawQuery == null || rawQuery.isBlank()) {
            return path;
        }
        List<RawParameter> subresources = new ArrayList<>();
        for (String pair : rawQuery.split("&", -1)) {
            int separator = pair.indexOf('=');
            String rawName = separator < 0 ? pair : pair.substring(0, separator);
            String name = percentDecode(rawName);
            if (SIGNED_SUBRESOURCES.contains(name)) {
                String value = separator < 0 ? null : pair.substring(separator + 1);
                subresources.add(new RawParameter(name, value == null || value.isEmpty() ? null : value));
            }
        }
        if (subresources.isEmpty()) {
            return path;
        }
        subresources.sort(Comparator.comparing(RawParameter::name)
                .thenComparing(parameter -> parameter.rawValue() == null ? "" : parameter.rawValue()));
        return path + "?" + subresources.stream()
                .map(parameter -> parameter.rawValue() == null
                        ? parameter.name()
                        : parameter.name() + "=" + parameter.rawValue())
                .collect(Collectors.joining("&"));
    }

    static String percentDecode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new ObsException("InvalidURI", "The request URI contains invalid percent encoding.", 400);
        }
    }

    private static String header(ContainerRequestContext request, String name) {
        return fold(blankToEmpty(request.getHeaderString(name)));
    }

    private static String fold(String value) {
        return blankToEmpty(value).trim().replaceAll("[\\t\\n\\r ]+", " ");
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record RawParameter(String name, String rawValue) {
    }
}
