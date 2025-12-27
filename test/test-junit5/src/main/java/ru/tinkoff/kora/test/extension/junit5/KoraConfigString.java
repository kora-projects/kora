package ru.tinkoff.kora.test.extension.junit5;

import java.util.HashMap;
import java.util.Map;

/**
 * Configs inside {@link KoraConfigString} are merged in user specified method order
 * <p>
 * All configs specified for test are merged into single config, each next config replaces values from previous configs
 */
final class KoraConfigString implements KoraConfigModification {

    private final Map<String, String> systemProperties = new HashMap<>();
    private final String config;

    KoraConfigString(String config) {
        this.config = config;
    }

    String config() {
        return config;
    }

    @Override
    public Map<String, String> systemProperties() {
        return Map.copyOf(systemProperties);
    }

    @Override
    public KoraConfigString withSystemProperty(String key, String value) {
        this.systemProperties.put(key, value);
        return this;
    }

    @Override
    public String toString() {
        return config;
    }
}
