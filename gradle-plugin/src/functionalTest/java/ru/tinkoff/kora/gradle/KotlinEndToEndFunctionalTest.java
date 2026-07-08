package ru.tinkoff.kora.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;

/**
 * Applies the plugin the real way (plugins block + mavenLocal). Both the Kora plugin and the Kotlin
 * plugin are resolved through the plugins block, so they share a classloader scope — this is what a
 * real consumer gets, and what lets the plugin read the applied Kotlin version. Requires network.
 */
class KotlinEndToEndFunctionalTest {
    private static final String PLUGIN_VERSION = System.getProperty("koraPluginVersion");

    @TempDir
    Path dir;

    @Test
    void compilesKoraKotlinAppWithOnlyThePlugin() throws IOException {
        Files.writeString(dir.resolve("settings.gradle"), """
            pluginManagement { repositories { mavenLocal(); gradlePluginPortal(); mavenCentral() } }
            rootProject.name = 'e2ek'
            """);
        Files.writeString(dir.resolve("build.gradle"), """
            plugins { id 'ru.tinkoff.kora' version '%s'; id 'org.jetbrains.kotlin.jvm' version '1.9.25' }
            repositories {
                mavenCentral()
                maven { url 'https://central.sonatype.com/repository/maven-snapshots/' }
            }
            dependencies { implementation 'ru.tinkoff.kora:config-hocon' }
            """.formatted(PLUGIN_VERSION));
        Path src = dir.resolve("src/main/kotlin/example");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.kt"), """
            package example
            import ru.tinkoff.kora.common.KoraApp
            import ru.tinkoff.kora.config.hocon.HoconConfigModule
            @KoraApp
            interface App : HoconConfigModule
            """);

        BuildResult result = GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withArguments("compileKotlin", "--stacktrace")
            .build();

        assertThat(result.task(":kspKotlin").getOutcome()).isEqualTo(SUCCESS);
        assertThat(result.task(":compileKotlin").getOutcome()).isEqualTo(SUCCESS);
    }
}
