package io.koraframework.soap.client.common.exception;

public class SoapException extends RuntimeException {

    public SoapException(String message) {
        super(message);
    }

    public SoapException(String message, Throwable cause) {
        super(message, cause);
    }

    public SoapException(Throwable cause) {
        super(cause);
    }

    protected SoapException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
