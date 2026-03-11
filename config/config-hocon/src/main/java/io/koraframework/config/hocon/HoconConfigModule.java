package io.koraframework.config.hocon;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.impl.ConfigImpl;
import io.koraframework.config.common.CommonConfigModule;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.annotation.ApplicationConfig;
import io.koraframework.config.common.origin.ConfigOrigin;
import io.koraframework.config.common.origin.FileConfigOrigin;
import io.koraframework.config.common.origin.ResourceConfigOrigin;
import io.koraframework.config.common.origin.SimpleConfigOrigin;

import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
