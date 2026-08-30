package io.github.hectorvent.floci.services.obs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedMap;

/** Positively identifies legacy OBS header and signed-query requests. */
@ApplicationScoped
public class ObsRequestClassifier {

    public static final String REQUEST_PROPERTY = "floci.huawei.obs.request";

    public boolean isObsRequest(ContainerRequestContext context) {
        if (context == null) {
            return false;
        }
        String authorization = context.getHeaderString("Authorization");
        if (hasObsScheme(authorization)) {
            return true;
        }
        if (authorization != null && !authorization.isBlank()) {
            return false;
        }
        // Classification needs only parameter presence. RESTEasy Reactive does not support
        // non-decoded query maps in native mode; the untouched raw query is preserved separately
        // by ObsRoutingFilter for signature canonicalization.
        MultivaluedMap<String, String> query = context.getUriInfo().getQueryParameters(true);
        // Presence claims the request as OBS. Cardinality and value validation belong to the
        // parser so malformed/duplicate tuples receive OBS XML instead of falling through to S3.
        return hasValue(query, "AccessKeyId")
                && hasValue(query, "Expires")
                && hasValue(query, "Signature");
    }

    static boolean hasObsScheme(String authorization) {
        return authorization != null
                && (authorization.equals("OBS") || authorization.startsWith("OBS "));
    }

    private static boolean hasValue(MultivaluedMap<String, String> query, String name) {
        return query.containsKey(name) && query.get(name) != null && !query.get(name).isEmpty();
    }

    public static boolean isRouted(ContainerRequestContext context) {
        return context != null && Boolean.TRUE.equals(context.getProperty(REQUEST_PROPERTY));
    }
}
