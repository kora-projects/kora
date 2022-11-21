package ru.tinkoff.kora.logging.common.arg;

import com.fasterxml.jackson.core.JsonGenerator;
import org.slf4j.Marker;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

record ArgumentMarkerWithWriter(String fieldName, StructuredArgumentWriter writer) implements Marker, StructuredArgument {
    @Override
    public void writeTo(JsonGenerator generator) throws IOException {
        this.writer.writeTo(generator);
    }

    @Override
    public String getName() {
        return "ArgumentMarkerWithWriter";
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
