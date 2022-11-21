package ru.tinkoff.kora.logging.common.arg;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

record ArgumentWithWriter(String fieldName, StructuredArgumentWriter writer) implements StructuredArgument {
    @Override
    public void writeTo(JsonGenerator generator) throws IOException {
        this.writer.writeTo(generator);
    }
}
