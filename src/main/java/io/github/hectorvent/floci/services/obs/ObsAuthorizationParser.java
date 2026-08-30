package io.github.hectorvent.floci.services.obs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedMap;

/** Strict parser for the legacy OBS authorization formats. */
@ApplicationScoped
public class ObsAuthorizationParser {

    public ObsAuthorization parse(ContainerRequestContext context) {
        String authorization = context.getHeaderString("Authorization");
        if (ObsRequestClassifier.hasObsScheme(authorization)) {
            return parseHeader(authorization);
        }
        // Decode credential values exactly once. The raw query remains in ObsRequestContext for
        // canonical-resource construction, but Base64 signatures commonly contain %2B/%2F/%3D.
        return parseQuery(context.getUriInfo().getQueryParameters(true));
    }

    ObsAuthorization parseHeader(String value) {
        if (value == null || !value.startsWith("OBS ") || value.length() == 4) {
            throw malformed();
        }
        String credentials = value.substring(4);
        int separator = credentials.indexOf(':');
        if (separator <= 0 || separator != credentials.lastIndexOf(':')
                || separator == credentials.length() - 1) {
            throw malformed();
        }
        String accessKey = credentials.substring(0, separator);
        String signature = credentials.substring(separator + 1);
        if (invalidCredentialPart(accessKey) || invalidCredentialPart(signature)) {
            throw malformed();
        }
        return new ObsAuthorization(accessKey, signature, null);
    }

    ObsAuthorization parseQuery(MultivaluedMap<String, String> query) {
        String accessKey = one(query, "AccessKeyId");
        String expiresValue = one(query, "Expires");
        String signature = one(query, "Signature");
        if (invalidCredentialPart(accessKey) || invalidCredentialPart(signature)) {
            throw malformed();
        }
        try {
            return new ObsAuthorization(accessKey, signature, Long.parseLong(expiresValue));
        } catch (NumberFormatException exception) {
            throw new ObsException("InvalidArgument", "Expires must be an epoch-seconds value.", 400);
        }
    }

    private static String one(MultivaluedMap<String, String> query, String name) {
        if (query == null || query.get(name) == null || query.get(name).size() != 1) {
            throw malformed();
        }
        String value = query.getFirst(name);
        if (value == null || value.isBlank()) {
            throw malformed();
        }
        return value;
    }

    private static boolean invalidCredentialPart(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return value.chars().anyMatch(character -> Character.isWhitespace(character)
                || Character.isISOControl(character));
    }

    static ObsException malformed() {
        return new ObsException("InvalidArgument", "The OBS authorization is malformed.", 400);
    }
}
