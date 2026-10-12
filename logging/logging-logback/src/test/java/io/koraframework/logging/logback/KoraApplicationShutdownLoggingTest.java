package io.koraframework.logging.logback;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.KoraApplication;
import io.koraframework.application.graph.Lifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class KoraApplicationShutdownLoggingTest {

    private static final int RELEASE_LINES = 400;

    @TempDir
    Path tmp;

    @Test
    void releaseLogsAreFlushedOnShutdown() throws Exception {
        // Kora's own configurator must be in charge, so the test logback.xml is left out of the child classpath
        var classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .filter(p -> !p.contains("test-logging"))
            .collect(Collectors.joining(File.pathSeparator));
        var out = this.tmp.resolve("app.log");
        var java = ProcessHandle.current().info().command().orElse("java");
        var process = new ProcessBuilder(java, "-cp", classpath, TestApp.class.getName())
            .redirectErrorStream(true)
            .redirectOutput(out.toFile())
            .start();

        var deadline = System.currentTimeMillis() + 20_000;
        while (!Files.readString(out).contains("Application initialized") && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        process.destroy(); // SIGTERM
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor();
        }

        var lines = Files.readAllLines(out);
        assertThat(lines).filteredOn(l -> l.contains("release line")).hasSize(RELEASE_LINES);
        assertThat(lines).anyMatch(l -> l.contains("component released"));
        assertThat(lines).anyMatch(l -> l.contains("Application released"));
    }

    public static class TestApp {
        public static void main(String[] args) {
            var log = LoggerFactory.getLogger("test.component");
            KoraApplication.run(() -> {
                var draw = new ApplicationGraphDraw(TestApp.class);
                draw.addNode(Lifecycle.class, null, null, List.of(), List.of(), List.of(), g -> new Lifecycle() {
                    @Override
                    public void init() {}

                    @Override
                    public void release() {
                        for (int i = 0; i < RELEASE_LINES; i++) {
                            log.warn("release line {}", i);
                        }
                        log.warn("component released");
                    }
                });
                return draw;
            });
        }
    }
}
