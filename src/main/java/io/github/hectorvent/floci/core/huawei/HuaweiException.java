package io.github.hectorvent.floci.core.huawei;

/** Base exception for Huawei Cloud-compatible API errors. */
public class HuaweiException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public HuaweiException(String errorCode, String message, int httpStatus) {
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
