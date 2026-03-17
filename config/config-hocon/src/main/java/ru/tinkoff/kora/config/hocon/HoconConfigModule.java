package ru.tinkoff.kora.config.hocon;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.impl.ConfigImpl;
import ru.tinkoff.kora.config.common.CommonConfigModule;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.annotation.ApplicationConfig;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;
import ru.tinkoff.kora.config.common.origin.FileConfigOrigin;
import ru.tinkoff.kora.config.common.origin.ResourceConfigOrigin;
import ru.tinkoff.kora.config.common.origin.SimpleConfigOrigin;

import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public interface HoconConfigModule extends CommonConfigModule {
    @ApplicationConfig
    default ConfigOrigin applicationConfigOrigin() throws URISyntaxException {
        var resource = System.getProperty("config.resource");
        int specified = 0;
        if (resource != null)
            specified += 1;
        var file = System.getProperty("config.file");
        if (file != null)
            specified += 1;

        if (specified == 0) {
            resource = "application.conf";
        } else if (specified > 1) {
            throw new RuntimeException("You set more than one of config.file='%s', config.resource='%s'; don't know which one to use!".formatted(file, resource));
        }
        if (resource != null) {
            if (resource.startsWith("/")) {
                resource = resource.substring(1);
            }
            var loader = Thread.currentThread().getContextClassLoader();
            var resourceUrl = loader.getResource(resource);
            if (resourceUrl == null) {
                return new SimpleConfigOrigin("empty");
            }
            if (resourceUrl.getProtocol().equals("file")) {
                return new FileConfigOrigin(Path.of(resourceUrl.toURI()));
            }
            return new ResourceConfigOrigin(resourceUrl);
        } else {
            return new FileConfigOrigin(Path.of(file));
        }
    }

    @ApplicationConfig
    default com.typesafe.config.Config applicationUnresolved(@ApplicationConfig ConfigOrigin origin) throws Exception {
        if (origin instanceof FileConfigOrigin file) {
            try (var reader = Files.newBufferedReader(file.path(), StandardCharsets.UTF_8)) {
                return ConfigFactory.parseReader(reader);
            }
        } else if (origin instanceof ResourceConfigOrigin resource) {
            var connection = resource.url().openConnection();
            connection.connect();
            try (var reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                return ConfigFactory.parseReader(reader);
            } finally {
                if (connection instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            }
        } else {
            return ConfigFactory.empty();
        }
    }

    default com.typesafe.config.Config hoconConfig(@ApplicationConfig com.typesafe.config.Config config) {
        var loader = Thread.currentThread().getContextClassLoader();
        ConfigFactory.invalidateCaches();
        return ConfigFactory.defaultOverrides(loader)
            .withFallback(config)
            .withFallback(ConfigImpl.defaultReferenceUnresolved(loader))
            .resolve(ConfigResolveOptions.defaults());
    }

    @ApplicationConfig
    default Config config(@ApplicationConfig ConfigOrigin origin, com.typesafe.config.Config hoconConfig) {
        return HoconConfigFactory.fromHocon(origin, hoconConfig);
    }

    /**
     * Walks the parsed Typesafe Config tree and collects all unique file paths
     * from which config values originated (including include files).
     */
    static Set<Path> extractIncludedFiles(com.typesafe.config.Config config) {
        var files = new LinkedHashSet<Path>();
        collectFileOrigins(config.root(), files);
        return files;
    }

    private static void collectFileOrigins(com.typesafe.config.ConfigValue value, Set<Path> files) {
        var origin = value.origin();
        var filename = origin.filename();
        if (filename != null && !filename.startsWith("merge of ")) {
            files.add(Path.of(filename).toAbsolutePath());
        }
        if (value instanceof com.typesafe.config.ConfigObject obj) {
            for (var entry : obj.entrySet()) {
                collectFileOrigins(entry.getValue(), files);
            }
        } else if (value instanceof com.typesafe.config.ConfigList list) {
            for (var item : list) {
                collectFileOrigins(item, files);
            }
        }
    }
}
