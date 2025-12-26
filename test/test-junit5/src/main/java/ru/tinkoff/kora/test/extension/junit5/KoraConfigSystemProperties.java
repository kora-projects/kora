package ru.tinkoff.kora.test.extension.junit5;

import java.util.HashMap;
import java.util.Map;

/**
 * Configs inside {@link KoraConfigSystemProperties} are merged in user specified method order
 * <p>
 * All configs specified for test are merged into single config, each next config replaces values from previous configs
 */
final class KoraConfigSystemProperties implements KoraConfigModification {

    private final Map<String, String> systemProperties = new HashMap<>();

    @Override
    public Map<String, String> systemProperties() {
        return Map.copyOf(systemProperties);
    }

    public KoraConfigSystemProperties withSystemProperty(String key, String value) {
        this.systemProperties.put(key, value);
        return this;
    }

    @Override
    public String toString() {
        return systemProperties.toString();
    }
}
