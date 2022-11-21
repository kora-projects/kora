package ru.tinkoff.kora.config.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class ConfigTestUtils {
    public static Path createConfigDir() throws IOException {
        var configDir = Files.createTempDirectory(UUID.randomUUID().toString());
        configDir.toFile().deleteOnExit();
        return configDir;
    }

    public static Path createCurrentDataDir(Path configDir, String configContent) throws IOException {
        var currentConfigDir = Files.createTempDirectory(configDir, "data-" + DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now()));
        currentConfigDir.toFile().deleteOnExit();

        var config = Files.createFile(currentConfigDir.resolve("config.conf"));
        config.toFile().deleteOnExit();
        Files.writeString(config, configContent, StandardCharsets.UTF_8);

        return currentConfigDir;
    }

    public static Path createOrUpdateDataDir(Path configDir, Path currentConfigDir) throws IOException {
        var dataDirPath = configDir.resolve("data");
        Files.deleteIfExists(dataDirPath);

        var dataDir = Files.createSymbolicLink(dataDirPath, currentConfigDir);
        dataDir.toFile().deleteOnExit();
        return dataDir;
    }

    public static Path createConfigFile(Path configDir, Path dataDir) throws IOException {
        Files.deleteIfExists(configDir.resolve("config.conf"));
        var config = Files.createSymbolicLink(configDir.resolve("config.conf"), dataDir.resolve("config.conf"));
        config.toFile().deleteOnExit();
        return config;
    }
}
