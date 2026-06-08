package ru.tinkoff.kora.config.hocon;

import ru.tinkoff.kora.config.common.origin.ConfigOrigin;
import ru.tinkoff.kora.config.common.origin.ContainerConfigOrigin;
import ru.tinkoff.kora.config.common.origin.FileConfigOrigin;
import ru.tinkoff.kora.config.common.origin.ResourceConfigOrigin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class HoconConfigOrigin implements ContainerConfigOrigin {

    private final ConfigOrigin primaryOrigin;
    private final List<ConfigOrigin> origins;
    private final String description;

    HoconConfigOrigin(Path path, List<FileConfigOrigin> includedFiles) {
        this.primaryOrigin = new FileConfigOrigin(path);
        this.origins = new ArrayList<>();
        this.origins.add(this.primaryOrigin);
        this.origins.addAll(includedFiles);
        this.description = buildDescription();
    }

    HoconConfigOrigin(URL url) {
        this.primaryOrigin = new ResourceConfigOrigin(url);
        this.origins = List.of(this.primaryOrigin);
        this.description = buildDescription();
    }

    InputStream openInputStream() throws IOException {
        if (primaryOrigin instanceof FileConfigOrigin file) {
            return Files.newInputStream(file.path());
        } else if (primaryOrigin instanceof ResourceConfigOrigin resource) {
            var connection = resource.url().openConnection();
            connection.connect();
            return connection.getInputStream();
        }
        throw new IllegalStateException("Unknown primary origin type: " + primaryOrigin.getClass());
    }

    @Override
    public List<ConfigOrigin> origins() {
        return this.origins;
    }

    @Override
    public String description() {
        return this.description;
    }

    @Override
    public String toString() {
        return this.description;
    }

    private String buildDescription() {
        if (origins.size() == 1) {
            return origins.get(0).description();
        }
        return origins.stream()
            .map(ConfigOrigin::description)
            .collect(Collectors.joining(",\n  ", "Hocon config of:\n  ", ""));
    }
}
