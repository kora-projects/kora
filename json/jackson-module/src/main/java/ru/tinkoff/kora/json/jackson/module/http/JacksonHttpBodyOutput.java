package ru.tinkoff.kora.json.jackson.module.http;

import com.fasterxml.jackson.databind.ObjectWriter;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.flow.LazySingleSubscription;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public final class JacksonHttpBodyOutput<T> implements HttpBodyOutput {
    private final ObjectWriter objectMapper;
    private final Context context;
    private final T value;

    public JacksonHttpBodyOutput(ObjectWriter objectMapper, Context context, T value) {
        this.objectMapper = objectMapper;
        this.value = value;
        this.context = context;
    }

    @Override
    public long contentLength() {
        return -1;
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new LazySingleSubscription<>(subscriber, context, () -> {
            var resultBytes = this.objectMapper.writeValueAsBytes(value);
            return ByteBuffer.wrap(resultBytes);
        }));
    }

    @Override
    public void write(OutputStream os) throws IOException {
        this.objectMapper.writeValue(os, this.value);
    }

    @Override
    public void close() throws IOException {

    }
}
