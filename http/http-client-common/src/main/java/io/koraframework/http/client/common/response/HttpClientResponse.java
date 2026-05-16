package io.koraframework.http.client.common.response;

import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.header.HttpHeaders;

import java.io.Closeable;
import java.io.IOException;

public interface HttpClientResponse extends Closeable {

    int code();

    HttpHeaders headers();

    HttpBodyInput body();

    @Override
    void close() throws IOException;
}
