package ru.tinkoff.kora.test.extension.junit5;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * Configs inside {@link KoraConfigFile} are merged in user specified method order
 * <p>
 * All configs specified for test are merged into single config, each next config replaces values from previous configs
 */
public final class KoraConfigFile implements KoraConfigModification {

    private final Map<String, String> systemProperties = new HashMap<>();
    private final String configFile;

    KoraConfigFile(String configFile) {
        this.configFile = configFile;
    }

    @Nonnull
    String configFile() {
        return configFile;
    }

    @Nonnull
    @Override
    public Map<String, String> systemProperties() {
        return Map.copyOf(systemProperties);
    }

    @Nonnull
    @Override
    public KoraConfigFile withSystemProperty(@Nonnull String key, @Nonnull String value) {
        this.systemProperties.put(key, value);
        return this;
    }

    @Override
    public String toString() {
        return configFile();
    }
}
