package io.koraframework.soap.client.common.exception;

public class SoapInvalidHttpResponseException extends SoapException {
    public SoapInvalidHttpResponseException(String message) {
        super(message);
    }

    public SoapInvalidHttpResponseException(int code, byte[] responseBody) {
        this("Invalid http response code for SOAP request: %d\n%s".formatted(code, new String(responseBody, 0, Math.min(responseBody.length, 500))));
    }
}
