package ru.tinkoff.kora.openapi.management;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class ResourceUtils {

    private ResourceUtils() {}

    @Nullable
    public static InputStream getFileAsStream(@Nonnull String path) {
        var resourceAsStream = ResourceUtils.class.getResourceAsStream(path);
        if (resourceAsStream != null) {
            return new BufferedInputStream(resourceAsStream);
        }
        var resourceWithSlashStream = ResourceUtils.class.getResourceAsStream("/" + path);
        if (resourceWithSlashStream != null) {
            return new BufferedInputStream(resourceWithSlashStream);
        }
        return null;
    }

    @Nullable
    public static String getFileAsString(@Nonnull String path) {
        var is = ResourceUtils.getFileAsStream(path);
        if (is == null) {
            return null;
        }

        try (var stream = is) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Can't read file: " + path, e);
        }
    }

    public static String getFileName(String filePath) {
        int i = filePath.lastIndexOf('/');
        String fileName = (i == -1) ? filePath : filePath.substring(i + 1);
        if (fileName.endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        } else if (fileName.endsWith(".yml")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        } else if (fileName.endsWith(".yaml")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        }

        return fileName;
    }
}
