package ru.tinkoff.kora.http.client.async;

import io.netty.buffer.ByteBuf;
import org.asynchttpclient.request.body.Body;
import ru.tinkoff.kora.http.common.body.HttpOutBody;

import java.io.IOException;

public class AsyncHttpClientRequestBody implements Body {
    private final long contentLength;

    public AsyncHttpClientRequestBody(HttpOutBody body) {
        this.contentLength = body.contentLength();

    }

    @Override
    public long getContentLength() {
        return 0;
    }

    @Override
    public BodyState transferTo(ByteBuf target) throws IOException {
        return null;
    }

    @Override
    public void close() throws IOException {

    }
}
