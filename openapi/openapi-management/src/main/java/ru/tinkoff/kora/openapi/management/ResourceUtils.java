package ru.tinkoff.kora.openapi.management;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class ResourceUtils {

    private ResourceUtils() {
    }

    @Nullable
    public static InputStream getFileAsStream(@Nonnull String path) {
        return Optional.ofNullable(ResourceUtils.class.getResourceAsStream(path))
                .or(() -> Optional.ofNullable(ResourceUtils.class.getResourceAsStream("/" + path)))
                .map(BufferedInputStream::new)
                .orElse(null);
    }

    public static Optional<String> getFileAsString(@Nonnull String path) {
        final InputStream is = ResourceUtils.getFileAsStream(path);
        if (is == null) {
            return Optional.empty();
        }

        try (var stream = is) {
            return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("Can't read file: " + path, e);
        }
    }
}
