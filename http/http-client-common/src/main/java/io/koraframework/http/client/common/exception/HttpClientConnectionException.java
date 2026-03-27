package io.koraframework.http.client.common.exception;

public class HttpClientConnectionException extends HttpClientException {
    public HttpClientConnectionException(Exception cause) {
        super(cause);
    }
}
