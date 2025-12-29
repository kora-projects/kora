package ru.tinkoff.kora.json.module.http;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.module.JsonModule;
import tools.jackson.core.JsonEncoding;

import java.io.IOException;
import java.io.OutputStream;

public final class JsonHttpBodyOutput<T> implements HttpBodyOutput {
    private final JsonWriter<T> writer;
    @Nullable
    private final T value;

    public JsonHttpBodyOutput(JsonWriter<T> writer, @Nullable T value) {
        this.writer = writer;
        this.value = value;
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
    public void write(OutputStream os) throws IOException {
        try (var gen = JsonModule.JSON_FACTORY.createGenerator(os, JsonEncoding.UTF8)) {
            this.writer.write(gen, this.value);
        }
    }

    @Override
    public void close() throws IOException {

    }
}
