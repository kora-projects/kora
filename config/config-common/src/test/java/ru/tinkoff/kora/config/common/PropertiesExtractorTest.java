package ru.tinkoff.kora.config.common;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.config.common.extractor.PropertiesConfigValueExtractor;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static ru.tinkoff.kora.config.common.ConfigTestUtils.*;

public class PropertiesExtractorTest {

    private final Path configDir = createConfigDir();
    private Path currentConfigDir = createCurrentDataDir(this.configDir, """
        properties {
            "bootstrap.servers" = "localhost:9092"
            "password" = "test_password"
        }
        """);
    private Path dataDir = createOrUpdateDataDir(this.configDir, this.currentConfigDir);
    private Path configFile = createConfigFile(this.configDir, this.dataDir);
    private final ValueOf<Config> config = getConfig();

    @Test
    void testPropertiesExtractor(){
        var propertiesExtractor = new PropertiesConfigValueExtractor();
        var properties = propertiesExtractor.extract(config.get().getValue("properties"));
        assertThat(properties.get("password")).isEqualTo("test_password");
        assertThat(properties.getProperty("bootstrap.servers")).isEqualTo("localhost:9092");
    }

    PropertiesExtractorTest() throws IOException {
    }


    private ValueOf<Config> getConfig() {
        return new ValueOf<>() {
            private volatile Config config = this.load();

            @Override
            public Config get() {
                return config;
            }

            @Override
            public Mono<Void> refresh() {
                return Mono.fromRunnable(() -> this.config = this.load());
            }

            private Config load() {
                return ConfigFactory.parseFile(PropertiesExtractorTest.this.configFile.toFile());
            }
        };
    }
}
