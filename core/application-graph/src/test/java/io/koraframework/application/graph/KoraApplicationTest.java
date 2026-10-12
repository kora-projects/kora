package io.koraframework.application.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class KoraApplicationTest {

    @TempDir
    Path tmp;

    @Test
    void sigtermDuringInitReleasesAlreadyInitializedComponents() throws Exception {
        var app = this.start("slowInit");
        awaitOutput(app.out, "MARK init fast");
        Thread.sleep(500);
        app.process.destroy(); // SIGTERM
        var output = app.finish();

        assertThat(output)
            .contains("MARK release fast")
            .contains("Application shutdown...");
    }

    @Test
    void systemExitDuringInitDoesNotHang() throws Exception {
        var app = this.start("exitInInit");
        if (!app.process.waitFor(10, TimeUnit.SECONDS)) {
            app.process.destroyForcibly().waitFor();
            throw new AssertionError("Application did not exit: " + Files.readString(app.out));
        }

        assertThat(app.process.exitValue()).isEqualTo(0);
        assertThat(Files.readString(app.out)).contains("MARK init exiting");
    }

    @Test
    void sigtermDuringHungInitExitsAfterTimeout() throws Exception {
        var app = this.start("hungInit", "-Dkora.application.shutdownInitAwaitMillis=1000");
        awaitOutput(app.out, "MARK init fast");
        app.process.destroy(); // SIGTERM
        var output = app.finish();

        assertThat(app.process.exitValue()).isEqualTo(143);
        assertThat(output).contains("Application was not initialized in 1000ms after shutdown signal");
    }

    @Test
    void initFailureWithErrorIsLoggedAndExitsWithMinusOne() throws Exception {
        var app = this.start("initError");
        var output = app.finish();

        assertThat(app.process.exitValue()).isEqualTo(255);
        assertThat(output)
            .contains("Application initializing failed with error")
            .contains("MARK release fast");
    }

    @Test
    void graphSupplierFailureIsLoggedAndExitsWithMinusOne() throws Exception {
        var app = this.start("supplierFailure");
        var output = app.finish();

        assertThat(app.process.exitValue()).isEqualTo(255);
        assertThat(output).contains("Application initializing failed with error");
    }

    record App(Process process, Path out) {
        String finish() throws Exception {
            if (!this.process.waitFor(30, TimeUnit.SECONDS)) {
                this.process.destroyForcibly().waitFor();
                throw new AssertionError("Application did not exit: " + Files.readString(this.out));
            }
            return Files.readString(this.out);
        }
    }

    private App start(String scenario, String... jvmArgs) throws Exception {
        var out = this.tmp.resolve(scenario + ".log");
        var command = new ArrayList<String>();
        command.add(ProcessHandle.current().info().command().orElse("java"));
        command.addAll(List.of(jvmArgs));
        command.addAll(List.of("-cp", System.getProperty("java.class.path"), TestApp.class.getName(), scenario));
        var process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(out.toFile())
            .start();
        return new App(process, out);
    }

    private static void awaitOutput(Path out, String line) throws Exception {
        var deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.readString(out).contains(line)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("No '" + line + "' in output: " + Files.readString(out));
    }

    public static class TestApp {
        static void mark(String s) {
            System.out.println("MARK " + s);
            System.out.flush();
        }

        static Lifecycle lifecycle(String name, long initSleep, Runnable onInit) {
            return new Lifecycle() {
                @Override
                public void init() throws Exception {
                    Thread.sleep(initSleep);
                    mark("init " + name);
                    onInit.run();
                }

                @Override
                public void release() {
                    mark("release " + name);
                }
            };
        }

        public static void main(String[] args) {
            var scenario = args[0];
            KoraApplication.run(() -> {
                var draw = new ApplicationGraphDraw(TestApp.class);
                draw.addNode(Lifecycle.class, null, null, List.of(), List.of(), List.of(), g -> lifecycle("fast", 0, () -> {}));
                switch (scenario) {
                    case "slowInit" -> draw.addNode(Lifecycle.class, null, null, List.of(), List.of(), List.of(), g -> lifecycle("slow", 3000, () -> {}));
                    case "initError" -> draw.addNode(Lifecycle.class, null, null, List.of(), List.of(), List.of(), g -> lifecycle("bad", 200, () -> {
                        throw new ExceptionInInitializerError("static init failed");
                    }));
                    case "exitInInit" -> draw.addNode(Lifecycle.class, null, null, List.of(), List.of(), List.of(), g -> lifecycle("exiting", 200, () -> System.exit(0)));
                    case "hungInit" -> draw.addNode(Lifecycle.class, null, null, List.of(), List.of(), List.of(), g -> lifecycle("hung", Long.MAX_VALUE, () -> {}));
                    case "supplierFailure" -> throw new NoClassDefFoundError("some/Missing");
                    default -> throw new IllegalArgumentException(scenario);
                }
                return draw;
            });
        }
    }
}
