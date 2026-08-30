package io.github.hectorvent.floci.services.obs;

/** Parsed legacy OBS header or signed-query authorization. */
public record ObsAuthorization(String accessKey, String signature, Long expires) {

    public boolean querySigned() {
        return expires != null;
    }
}
