package ru.tinkoff.kora.config.common.origin;

import java.nio.file.Path;

public record FileConfigOrigin(Path path) implements ConfigOrigin {
    public FileConfigOrigin(Path path) {
        this.path = path.toAbsolutePath();
    }

    @Override
    public String description() {
        return "File " + this.path;
    }
}
