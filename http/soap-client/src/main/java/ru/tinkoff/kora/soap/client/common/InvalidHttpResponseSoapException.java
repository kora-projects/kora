package ru.tinkoff.kora.soap.client.common;

public class InvalidHttpResponseSoapException extends SoapException {
    public InvalidHttpResponseSoapException(String message) {
        super(message);
    }

    public InvalidHttpResponseSoapException(int code, byte[] responseBody) {
        this("Invalid http response code for SOAP request: %d\n%s".formatted(code, new String(responseBody, 0, Math.min(responseBody.length, 500))));
    }
}
