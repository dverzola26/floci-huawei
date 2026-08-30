package io.github.hectorvent.floci.services.obs;

/** OBS REST/XML protocol error. */
public class ObsException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public ObsException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
