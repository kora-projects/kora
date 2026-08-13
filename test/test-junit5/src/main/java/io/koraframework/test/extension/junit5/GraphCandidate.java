package io.koraframework.test.extension.junit5;

import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;

record GraphCandidate(Type type, @Nullable Class<?> tag) {

    GraphCandidate(Type type) {
        this(type, null);
    }

    @Override
    public String toString() {
        var typeName = type.getTypeName();
        return tag == null
            ? typeName
            : "[type=" + typeName + ", tag=" + tag.getName() + ']';
    }
}
