package io.koraframework.logging.logback;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Reads Logback bootstrap configuration, which happens before the application config is available.
 * <p>
 * Every property is looked up as a system property first and then as an environment variable, where the environment
 * variable name is the property in upper case with {@code .} and {@code -} replaced by {@code _}, so
 * {@code kora.logging.encoder} is also read from {@code KORA_LOGGING_ENCODER}.
 */
public final class KoraLogbackProperties {

    /** Set by Gradle on every test worker JVM, holding the worker id. */
    public static final String GRADLE_TEST_WORKER_PROPERTY = "org.gradle.test.worker";

    private KoraLogbackProperties() { }

    @Nullable
    public static String get(String property) {
        var value = System.getProperty(property);
        if (value == null) {
            value = System.getenv(asEnvironmentVariable(property));
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String get(String property, String defaultValue) {
        var value = get(property);
        return value == null ? defaultValue : value;
    }

    /**
     * @return whether the JVM was started by a Gradle test worker, which sets {@value #GRADLE_TEST_WORKER_PROPERTY}
     */
    public static boolean isRunningInTests() {
        return System.getProperty(GRADLE_TEST_WORKER_PROPERTY) != null;
    }

    public static String asEnvironmentVariable(String property) {
        return property.toUpperCase(Locale.ROOT)
            .replace('.', '_')
            .replace('-', '_');
    }
}
