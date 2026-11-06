package io.koraframework.config.hocon;

import io.koraframework.config.common.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;

class HoconConfigModuleTest {

    @Test
    void relativeIncludeInConfigFileIsResolvedNextToIt(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("secrets.conf"), "db.password = s3cret\n");
        var main = dir.resolve("application.conf");
        Files.writeString(main, """
            db.user = app
            include "secrets.conf"
            """);

        var config = withProperty("config.file", main.toString(), HoconConfigModuleTest::loadApplicationConfig);

        assertThat(config.get("db.user").asString()).isEqualTo("app");
        assertThat(config.get("db.password").asString()).isEqualTo("s3cret");
    }

    @Test
    void relativeIncludeInConfigResourceFromDirectoryIsResolvedNextToIt(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("app"));
        Files.writeString(dir.resolve("app/secrets.conf"), "db.password = s3cret\n");
        Files.writeString(dir.resolve("app/application.conf"), """
            db.user = app
            include "secrets.conf"
            """);

        var config = withClassLoader(dir, () -> withProperty("config.resource", "app/application.conf", HoconConfigModuleTest::loadApplicationConfig));

        assertThat(config.get("db.user").asString()).isEqualTo("app");
        assertThat(config.get("db.password").asString()).isEqualTo("s3cret");
    }

    @Test
    void includeInConfigResourceFromJarIsResolvedOnClasspath(@TempDir Path dir) throws Exception {
        var jar = dir.resolve("config.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            putEntry(out, "application.conf", """
                db.user = app
                include "secrets.conf"
                """);
            putEntry(out, "secrets.conf", "db.password = s3cret\n");
        }

        var config = withClassLoader(jar, () -> withProperty("config.resource", "application.conf", HoconConfigModuleTest::loadApplicationConfig));

        assertThat(config.get("db.user").asString()).isEqualTo("app");
        assertThat(config.get("db.password").asString()).isEqualTo("s3cret");
    }

    private static Config loadApplicationConfig() throws Exception {
        var module = new HoconConfigModule() {};
        var origin = module.applicationConfigOrigin();
        var hocon = module.applicationConfigHocon(module.applicationConfigHoconUnresolved(origin));
        return module.applicationConfig(origin, hocon);
    }

    private static void putEntry(JarOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private interface ConfigLoader {
        Config load() throws Exception;
    }

    private static Config withClassLoader(Path classpath, ConfigLoader loader) throws Exception {
        var thread = Thread.currentThread();
        var prev = thread.getContextClassLoader();
        try (var classLoader = new URLClassLoader(new URL[]{classpath.toUri().toURL()}, prev)) {
            thread.setContextClassLoader(classLoader);
            return loader.load();
        } finally {
            thread.setContextClassLoader(prev);
        }
    }

    private static Config withProperty(String key, String value, ConfigLoader loader) throws Exception {
        var prev = System.getProperty(key);
        System.setProperty(key, value);
        try {
            return loader.load();
        } finally {
            if (prev == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, prev);
            }
        }
    }
}
