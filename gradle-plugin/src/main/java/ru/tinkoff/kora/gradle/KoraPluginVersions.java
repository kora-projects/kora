package ru.tinkoff.kora.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The Kora version baked into the plugin at build time (see gradle-plugin/build.gradle
 * processResources). It equals the plugin version and pins the {@code kora-parent} BOM. Read from a
 * classpath resource so a single source of truth is preserved.
 */
public final class KoraPluginVersions {
    private static final String RESOURCE = "/ru/tinkoff/kora/gradle/version.properties";

    private final String koraVersion;

    private KoraPluginVersions(String koraVersion) {
        this.koraVersion = koraVersion;
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
        String koraVersion = props.getProperty("koraVersion");
        if (koraVersion == null || koraVersion.isBlank()) {
            throw new IllegalStateException("Missing 'koraVersion' in " + RESOURCE);
        }
        return new KoraPluginVersions(koraVersion);
    }

    public String koraVersion() {
        return koraVersion;
    }
}
