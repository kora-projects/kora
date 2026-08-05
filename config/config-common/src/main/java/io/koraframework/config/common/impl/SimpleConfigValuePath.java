package io.koraframework.config.common.impl;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValuePath;
import io.koraframework.config.common.PathElement;

public record SimpleConfigValuePath(@Nullable PathElement last, @Nullable ConfigValuePath prev) implements ConfigValuePath {
    public SimpleConfigValuePath {
        if (last == null && prev != null) {
            throw new IllegalArgumentException("Invalid config path: root path cannot have a previous path segment");
        }
        if (last != null && prev == null) {
            throw new IllegalArgumentException("Invalid config path: non-root path segment '%s' must have a previous path".formatted(last));
        }
    }

    @Override
    public String toString() {
        if (last == null || prev == null) {
            return "ROOT";
        }
        return prev + "." + last;
    }
}
