package ru.tinkoff.kora.test.extension.junit5;

import javax.annotation.Nonnull;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

record GraphCandidate(@Nonnull Type type, @Nonnull List<Class<?>> tags) {

    GraphCandidate(Type type) {
        this(type, Collections.emptyList());
    }

    GraphCandidate(Type type, Class<?>[] tags) {
        this(type, (tags == null) ? Collections.emptyList() : Arrays.asList(tags));
    }

    public Class<?>[] tagsAsArray() {
        return tags.toArray(Class[]::new);
    }

    @Override
    public String toString() {
        return "[type=" + type + ", tags=" + tags + ']';
    }
}
