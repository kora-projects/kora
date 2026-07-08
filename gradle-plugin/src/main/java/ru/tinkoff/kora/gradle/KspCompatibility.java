package ru.tinkoff.kora.gradle;

import org.gradle.api.GradleException;

/**
 * Validates that the Kotlin version applied by the project matches the Kotlin version the bundled
 * KSP was built for. KSP is tightly coupled to the Kotlin compiler version, so a mismatch produces
 * confusing failures downstream — this turns it into an actionable error at configuration time.
 */
final class KspCompatibility {
    private KspCompatibility() {
    }

    static void validate(String appliedKotlinVersion, String expectedKotlinVersion) {
        if (!expectedKotlinVersion.equals(appliedKotlinVersion)) {
            throw new GradleException(
                "Kora Gradle plugin bundles KSP for Kotlin " + expectedKotlinVersion
                    + ", but the project applies Kotlin " + appliedKotlinVersion + ". "
                    + "Align kotlin(\"jvm\") to " + expectedKotlinVersion
                    + ", or use a Kora plugin version built for Kotlin " + appliedKotlinVersion + ".");
        }
    }
}
