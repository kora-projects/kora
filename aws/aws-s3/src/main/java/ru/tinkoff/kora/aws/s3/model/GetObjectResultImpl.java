package ru.tinkoff.kora.aws.s3.model;

import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.io.IOException;

public class GetObjectResultImpl implements GetObjectResult {
    private final HttpClientResponse rs;

    public GetObjectResultImpl(HttpClientResponse rs) {
        this.rs = rs;
    }

    @Override
    public HttpBodyInput body() {
        return this.rs.body();
    }

    @Override
    public void close() throws IOException {
        this.rs.close();
    }

    @Override
    public int code() {
        return this.rs.code();
    }

    @Override
    public HttpHeaders headers() {
        return this.rs.headers();
    }

    @Override
    public ContentRange contentRange() {
        var contentRange = this.rs.headers().getFirst("content-range");
        if (contentRange == null) {
            throw new IllegalArgumentException("Missing content range header");
        }
        if (!contentRange.startsWith("bytes ")) {
            throw new IllegalArgumentException("Invalid content range header: " + contentRange);
        }
        var i = contentRange.indexOf('/');
        if (i < 0) {
            throw new IllegalArgumentException("Invalid range header value: " + contentRange);
        }
        final long completeLength;
        try {
            completeLength = Long.parseLong(contentRange.substring(i + 1).trim());
        } catch (NumberFormatException ignore) {
            throw new IllegalArgumentException("Invalid range header value: " + contentRange);
        }
        var j = contentRange.indexOf('-');
        if (j < 0) {
            throw new IllegalArgumentException("Invalid range header value: " + contentRange);
        }
        final long firstPosition;
        try {
            firstPosition = Long.parseLong(contentRange.substring(6, j).trim());
        } catch (NumberFormatException ignore) {
            throw new IllegalArgumentException("Invalid range header value: " + contentRange);
        }
        final long lastPosition;
        try {
            lastPosition = Long.parseLong(contentRange.substring(j, i).trim());
        } catch (NumberFormatException ignore) {
            throw new IllegalArgumentException("Invalid range header value: " + contentRange);
        }

        return new ContentRange(firstPosition, lastPosition, completeLength);
    }
}
