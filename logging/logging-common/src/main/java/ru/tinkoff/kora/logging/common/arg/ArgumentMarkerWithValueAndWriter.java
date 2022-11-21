package ru.tinkoff.kora.logging.common.arg;

import com.fasterxml.jackson.core.JsonGenerator;
import org.slf4j.Marker;
import ru.tinkoff.kora.json.common.JsonWriter;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

record ArgumentMarkerWithValueAndWriter<T>(String fieldName, T value, JsonWriter<T> writer) implements Marker, StructuredArgument {
    @Override
    public void writeTo(JsonGenerator generator) throws IOException {
        if (this.value == null) {
            generator.writeNull();
        } else {
            this.writer.write(generator, this.value);
        }
    }

    @Override
    public String getName() {
        return "ArgumentMarkerWithValueAndWriter";
    }

    @Override
    public void add(Marker marker) {

    }

    @Override
    public boolean remove(Marker marker) {
        return false;
    }

    @Override
    @Deprecated
    public boolean hasChildren() {
        return false;
    }

    @Override
    public boolean hasReferences() {
        return false;
    }

    @Override
    public Iterator<Marker> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public boolean contains(Marker marker) {
        return false;
    }

    @Override
    public boolean contains(String s) {
        return false;
    }
}
