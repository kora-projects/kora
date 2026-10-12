package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.KoraApplication;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.logging.logback.text.ConsoleTextEncoderFactory;
import io.koraframework.logging.logback.text.ConsoleTextRecordEncoder;
import io.koraframework.logging.logback.KoraAsyncAppender;
import io.koraframework.logging.logback.KoraLogbackConfigurator;
import io.koraframework.logging.logback.KoraLogbackProperties;
import io.koraframework.logging.logback.LogbackEncoderFactory;
import io.koraframework.logging.logback.text.ColorConsoleTextEncoderFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class KoraLogbackConfiguratorTest {

    private final KoraLogbackConfigurator configurator = new KoraLogbackConfigurator();

    @Test
    void shouldDiscoverAllEncoderFactoriesOnClasspath() {
        var factories = ServiceLoader.load(LogbackEncoderFactory.class).stream()
            .map(ServiceLoader.Provider::get)
            .map(LogbackEncoderFactory::name)
            .toList();

        assertThat(factories).contains(JsonEncoderFactory.NAME, ConsoleTextEncoderFactory.NAME,
            ColorConsoleTextEncoderFactory.NAME);
    }

    @Test
    void shouldDetectGradleTestWorker() {
        assertThat(KoraLogbackProperties.isRunningInTests()).isTrue();
    }

    @Test
    void shouldPreferPrettyEncoderWhenRunningInTests() {
        var selected = this.configurator.selectFactory(factories(), null);

        assertThat(selected).isInstanceOf(ColorConsoleTextEncoderFactory.class);
    }

    @Test
    void shouldPreferJsonOverTextWhenNothingIsSelected() {
        var selected = this.configurator.selectFactory(
            List.of(new ConsoleTextEncoderFactory(), new JsonEncoderFactory()), null);

        assertThat(selected).isInstanceOf(JsonEncoderFactory.class);
    }

    @Test
    void shouldSelectEncoderByName() {
        assertThat(this.configurator.selectFactory(factories(), "text")).isInstanceOf(ConsoleTextEncoderFactory.class);
        assertThat(this.configurator.selectFactory(factories(), "json")).isInstanceOf(JsonEncoderFactory.class);
        assertThat(this.configurator.selectFactory(factories(), "PRETTY")).isInstanceOf(ColorConsoleTextEncoderFactory.class);
    }

    @Test
    void shouldNotSelectUnknownEncoder() {
        assertThat(this.configurator.selectFactory(factories(), "unknown")).isNull();
    }

    @Test
    void shouldAttachSelectedEncoderToRootLogger() {
        var context = new LoggerContext();
        this.configurator.setContext(context);

        this.configurator.configureDefault(context, new JsonEncoderFactory());

        try {
            var root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            var async = root.getAppender(KoraLogbackConfigurator.ASYNC_APPENDER_NAME);
            assertThat(async).isInstanceOf(KoraAsyncAppender.class);

            var console = ((KoraAsyncAppender) async).getAppender(KoraLogbackConfigurator.CONSOLE_APPENDER_NAME);
            assertThat(console).isInstanceOf(ConsoleAppender.class);

            @SuppressWarnings("unchecked")
            var encoder = ((ConsoleAppender<ILoggingEvent>) console).getEncoder();
            assertThat(encoder).isInstanceOf(JsonRecordEncoder.class);
            assertThat(encoder.isStarted()).isTrue();
        } finally {
            context.stop();
        }
    }

    @Test
    void shouldCreateTextEncoder() {
        var encoder = new ConsoleTextEncoderFactory().create(new LoggerContext());

        assertThat(encoder).isInstanceOf(ConsoleTextRecordEncoder.class);
        assertThat(encode(encoder)).doesNotContain("");
    }

    @Test
    void shouldCreateColoredTextEncoder() {
        var encoder = new ColorConsoleTextEncoderFactory().create(new LoggerContext());

        assertThat(encoder).isInstanceOf(ConsoleTextRecordEncoder.class);
        assertThat(encode(encoder)).contains("");
    }

    @Test
    void shouldFlushAsyncQueueOnJvmExit() throws Exception {
        var out = runInChildJvm(ExitingApp.class, "plain");

        assertThat(count(out, "\"main-")).as(out).isEqualTo(100);
        assertThat(count(out, "\"hook-")).as(out).isEqualTo(50);
    }

    @Test
    void shouldFlushAsyncQueueOnKoraApplicationShutdown() throws Exception {
        var out = runInChildJvm(ExitingApp.class, "kora");

        assertThat(count(out, "\"main-")).as(out).isEqualTo(100);
        assertThat(count(out, "\"hook-")).as(out).isEqualTo(50);
        assertThat(out).contains("slow-release-done", "Application released in");
    }

    public static class ExitingApp {
        public static void main(String[] args) {
            System.setOut(new PrintStream(new HeldUntilStopped(System.out), true));
            var log = LoggerFactory.getLogger("exiting-app");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                for (int i = 0; i < 50; i++) log.info("hook-{}", i);
            }));
            if (args[0].equals("kora")) {
                KoraApplication.run(() -> {
                    var draw = new ApplicationGraphDraw(ExitingApp.class);
                    // the graph logs well after the other shutdown hooks are done
                    draw.addNode(Lifecycle.class, null, null, List.of(), List.of(), List.of(), g -> new Lifecycle() {
                        @Override
                        public void init() {}

                        @Override
                        public void release() throws InterruptedException {
                            Thread.sleep(400);
                            log.info("slow-release-done");
                        }
                    });
                    return draw;
                }, false);
            }
            for (int i = 0; i < 100; i++) log.info("main-{}", i);
        }
    }

    /**
     * Holds the async appender's worker on its first write until the worker is interrupted, which only
     * {@code AsyncAppenderBase.stop()} does. Nothing reaches the output unless the logging context is stopped before
     * the JVM halts, and only what was queued by then: a worker draining the queue on its own can not hide the bug.
     */
    static final class HeldUntilStopped extends FilterOutputStream {
        private boolean released;

        HeldUntilStopped(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            this.hold();
            this.out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            this.hold();
            this.out.write(b, off, len);
        }

        private synchronized void hold() {
            if (!Thread.currentThread().getName().startsWith("AsyncAppender-Worker")) {
                return;
            }
            while (!this.released) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    // the appender is stopping: let the worker flush the queue
                    this.released = true;
                }
            }
        }
    }

    private static String runInChildJvm(Class<?> main, String arg) throws Exception {
        // test-logging brings its own logback-test.xml, the default Kora pipeline is what is tested here
        var classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .filter(e -> !e.contains("test-logging"))
            .collect(Collectors.joining(File.pathSeparator));
        var process = new ProcessBuilder(ProcessHandle.current().info().command().orElseThrow(), "-cp", classpath,
            "-Dkora.logging.encoder=json", main.getName(), arg)
            .redirectErrorStream(true)
            .start();
        var out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return out;
    }

    private static long count(String out, String marker) {
        return out.lines().filter(l -> l.contains(marker)).count();
    }

    private static String encode(ch.qos.logback.core.encoder.Encoder<ILoggingEvent> encoder) {
        var event = new io.koraframework.logging.logback.KoraLoggingEvent(
            "thread", "logger", null, ch.qos.logback.classic.Level.INFO, "message", "message",
            null, null, null, java.util.Map.of(), 1000, 0, 1, null, java.util.Map.of(),
            io.opentelemetry.api.trace.SpanContext.getInvalid());
        return new String(encoder.encode(event), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static List<LogbackEncoderFactory> factories() {
        return List.of(new ConsoleTextEncoderFactory(), new ColorConsoleTextEncoderFactory(), new JsonEncoderFactory());
    }
}
