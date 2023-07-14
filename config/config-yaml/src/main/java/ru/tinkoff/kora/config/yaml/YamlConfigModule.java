package ru.tinkoff.kora.config.yaml;

import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.CommonConfigModule;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.annotation.ApplicationConfig;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;
import ru.tinkoff.kora.config.common.factory.MergeConfigFactory;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;
import ru.tinkoff.kora.config.common.origin.FileConfigOrigin;
import ru.tinkoff.kora.config.common.origin.ResourceConfigOrigin;
import ru.tinkoff.kora.config.common.origin.SimpleConfigOrigin;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public interface YamlConfigModule extends CommonConfigModule {
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
            resource = "application.yaml";
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

    @Tag(YamlConfigModule.class)
    default Config referenceConfig() throws Exception {
        var references = Thread.currentThread().getContextClassLoader().getResources("reference.yaml");
        var config = MapConfigFactory.fromMap("empty", Map.of());
        while (references.hasMoreElements()) {
            var referenceUrl = references.nextElement();
            var origin = new ResourceConfigOrigin(referenceUrl);
            var connection = referenceUrl.openConnection();
            try (var is = connection.getInputStream()) {
                var yamlConfig = YamlConfigFactory.fromYaml(origin, is);
                try {
                    yamlConfig.resolve();
                } catch (Exception e) {
                    throw new RuntimeException("Reference config must be resolvable without external configs", e);
                }
                config = MergeConfigFactory.merge(yamlConfig, config);
            } finally {
                if (connection instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            }
        }
        return config;
    }

    @ApplicationConfig
    default Config yamlConfig(@ApplicationConfig ConfigOrigin configOrigin, @Tag(YamlConfigModule.class) Config reference) throws Exception {
        if (configOrigin instanceof FileConfigOrigin fileConfigOrigin) {
            try (var is = Files.newInputStream(fileConfigOrigin.path(), StandardOpenOption.READ)) {
                var config = YamlConfigFactory.fromYaml(configOrigin, is);
                return MergeConfigFactory.merge(config, reference);
            }
        } else if (configOrigin instanceof ResourceConfigOrigin resourceConfigOrigin) {
            var connection = resourceConfigOrigin.url().openConnection();
            connection.connect();
            try (var is = connection.getInputStream()) {
                var config = YamlConfigFactory.fromYaml(configOrigin, is);
                return MergeConfigFactory.merge(config, reference);
            } finally {
                if (connection instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            }
        } else {
            return reference;
        }
    }
}
