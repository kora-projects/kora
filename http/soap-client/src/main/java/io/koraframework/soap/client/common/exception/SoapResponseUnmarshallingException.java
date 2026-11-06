package io.koraframework.soap.client.common.exception;

public class SoapResponseUnmarshallingException extends SoapException {

    public SoapResponseUnmarshallingException(String message) {
        super(message);
    }

    public SoapResponseUnmarshallingException(Throwable cause) {
        super(cause);
    }
}
