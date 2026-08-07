package io.koraframework.kora.app.ksp.fixture;

import java.util.List;

/**
 * Compiled on the test classpath on purpose: a Kotlin flexible type only carries its mutability
 * flexibility when the declaration is read from a class file rather than from a source in the
 * same compilation.
 */
public interface PlatformTypeJavaModule {

    default List<String> names() {
        return List.of("first");
    }

    default String joined(List<String> names) {
        return String.join(",", names);
    }
}
