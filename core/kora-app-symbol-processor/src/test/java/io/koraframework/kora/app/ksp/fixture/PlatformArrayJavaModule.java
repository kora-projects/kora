package io.koraframework.kora.app.ksp.fixture;

import org.jspecify.annotations.NullUnmarked;

import java.util.List;
import java.util.function.Supplier;

/**
 * Not null-marked on purpose, like most third-party Java modules: Kotlin sees {@code String[]} as
 * {@code Array<(out) String!>} and {@code List<String>} as {@code (Mutable)List<String!>}.
 */
@NullUnmarked
public interface PlatformArrayJavaModule {

    default Supplier<String[]> stringArray() {
        return () -> new String[]{"a"};
    }

    default Supplier<Long[]> longArray() {
        return () -> new Long[]{1L};
    }

    default Supplier<List<String>> stringList() {
        return () -> List.of("b");
    }
}
