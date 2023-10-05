package ru.tinkoff.kora.json.module.http;

import com.fasterxml.jackson.core.JsonEncoding;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.flow.LazySingleSubscription;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.module.JsonModule;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public final class JsonHttpBodyOutput<T> implements HttpBodyOutput {
    private final JsonWriter<T> writer;
    private final Context context;
    private final T value;

    public JsonHttpBodyOutput(JsonWriter<T> writer, Context context, T value) {
        this.writer = writer;
        this.value = value;
        this.context = context;
    }

    @Override
    public int contentLength() {
        return -1;
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new LazySingleSubscription<>(subscriber, context, () -> {
            var resultBytes = this.writer.toByteArray(value);
            return ByteBuffer.wrap(resultBytes);
        }));
    }

    @Override
    public void write(OutputStream os) throws IOException {
        try (var gen = JsonModule.JSON_FACTORY.createGenerator(os, JsonEncoding.UTF8)) {
            this.writer.write(gen, this.value);
        }
    }

    @Override
    public void close() throws IOException {

    }
}
