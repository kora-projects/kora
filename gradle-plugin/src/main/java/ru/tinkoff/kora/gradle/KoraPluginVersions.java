package ru.tinkoff.kora.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Versions baked into the plugin at build time (see gradle-plugin/build.gradle processResources).
 * The Kora version equals the plugin version; the KSP and Kotlin versions come from the Kora
 * version catalog. Read from a classpath resource so a single source of truth is preserved.
 */
public final class KoraPluginVersions {
    private static final String RESOURCE = "/ru/tinkoff/kora/gradle/version.properties";

    private final String koraVersion;
    private final String kspVersion;
    private final String kotlinVersion;

    private KoraPluginVersions(String koraVersion, String kspVersion, String kotlinVersion) {
        this.koraVersion = koraVersion;
        this.kspVersion = kspVersion;
        this.kotlinVersion = kotlinVersion;
    }

    public static KoraPluginVersions load() {
        Properties props = new Properties();
        try (InputStream in = KoraPluginVersions.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Kora plugin version resource not found: " + RESOURCE);
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + RESOURCE, e);
        }
        return new KoraPluginVersions(
            require(props, "koraVersion"),
            require(props, "kspVersion"),
            require(props, "kotlinVersion"));
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing '" + key + "' in " + RESOURCE);
        }
        return value;
    }

    public String koraVersion() {
        return koraVersion;
    }

    public String kspVersion() {
        return kspVersion;
    }

    public String kotlinVersion() {
        return kotlinVersion;
    }
}
