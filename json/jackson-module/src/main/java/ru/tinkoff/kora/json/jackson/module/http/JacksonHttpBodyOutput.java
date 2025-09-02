package ru.tinkoff.kora.json.jackson.module.http;

import com.fasterxml.jackson.databind.ObjectWriter;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.io.IOException;
import java.io.OutputStream;

public final class JacksonHttpBodyOutput<T> implements HttpBodyOutput {
    private final ObjectWriter objectMapper;
    private final T value;

    public JacksonHttpBodyOutput(ObjectWriter objectMapper, T value) {
        this.objectMapper = objectMapper;
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
        this.objectMapper.writeValue(os, this.value);
    }

    @Override
    public void close() throws IOException {

    }
}
