package ru.tinkoff.kora.logging.common.arg;

import tools.jackson.core.JsonGenerator;

record ArgumentWithWriter(String fieldName, StructuredArgumentWriter writer) implements StructuredArgument {
    @Override
    public void writeTo(JsonGenerator generator) {
        this.writer.writeTo(generator);
    }
}
