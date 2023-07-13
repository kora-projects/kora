package ru.tinkoff.kora.config.common.impl;

import ru.tinkoff.kora.config.common.ConfigValuePath;
import ru.tinkoff.kora.config.common.PathElement;

import javax.annotation.Nullable;

public record SimpleConfigValuePath(@Nullable PathElement last, @Nullable ConfigValuePath prev) implements ConfigValuePath {
    public SimpleConfigValuePath {
        if (last == null && prev != null) {
            throw new IllegalArgumentException();
        }
        if (last != null && prev == null) {
            throw new IllegalArgumentException();
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
