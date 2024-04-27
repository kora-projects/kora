package ru.tinkoff.kora.bpmn.camunda8.worker.util;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

record JarResource(String name, String path, Supplier<InputStream> inputStream) implements Resource {

    @Override
    public InputStream asInputStream() {
        return inputStream.get();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Resource that)) return false;
        return Objects.equals(name, that.name()) && Objects.equals(path, that.path());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, path);
    }

    @Override
    public String toString() {
        return name;
    }
}
