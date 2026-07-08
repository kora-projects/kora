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
 * Applies the plugin the real way (plugins block + mavenLocal), so it exercises marker resolution
 * and the same classloader scope a real consumer gets. Kora artifacts resolve from Sonatype
 * snapshots. Requires network; run via the {@code functionalTest} task (publishes the plugin first).
 */
class JavaEndToEndFunctionalTest {
    private static final String PLUGIN_VERSION = System.getProperty("koraPluginVersion");

    @TempDir
    Path dir;

    @Test
    void compilesKoraJavaAppWithOnlyThePlugin() throws IOException {
        Files.writeString(dir.resolve("settings.gradle"), """
            pluginManagement { repositories { mavenLocal(); gradlePluginPortal(); mavenCentral() } }
            rootProject.name = 'e2e'
            """);
        Files.writeString(dir.resolve("build.gradle"), """
            plugins { id 'ru.tinkoff.kora' version '%s'; id 'java' }
            repositories {
                mavenCentral()
                maven { url 'https://central.sonatype.com/repository/maven-snapshots/' }
            }
            dependencies {
                implementation 'ru.tinkoff.kora:config-hocon'
                implementation 'ru.tinkoff.kora:json-module'
            }
            """.formatted(PLUGIN_VERSION));
        Path src = dir.resolve("src/main/java/example");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"), """
            package example;
            import ru.tinkoff.kora.common.KoraApp;
            import ru.tinkoff.kora.config.hocon.HoconConfigModule;
            import ru.tinkoff.kora.json.module.JsonModule;
            @KoraApp
            public interface App extends HoconConfigModule, JsonModule {}
            """);

        BuildResult result = GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withArguments("compileJava", "--stacktrace")
            .build();

        assertThat(result.task(":compileJava").getOutcome()).isEqualTo(SUCCESS);
        assertThat(Files.exists(dir.resolve(
            "build/generated/sources/annotationProcessor/java/main/example/AppGraph.java"))).isTrue();
    }
}
