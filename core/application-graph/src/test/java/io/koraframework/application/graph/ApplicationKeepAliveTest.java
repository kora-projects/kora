package io.koraframework.application.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Kora runs on virtual threads and those are always daemon, so an initialized application has nothing
 * holding the JVM and exits with code 0 right after start. This used to be every server module's own
 * problem: a gRPC-only application, whose transport is entirely virtual, exited immediately and every
 * test against it failed with UNAVAILABLE.
 *
 * <p>The mechanism is tested here rather than through {@link KoraApplication#run} on purpose --
 * {@code run} only releases the thread from its shutdown hook, so calling it in-process would leave a
 * non-daemon thread behind and the test JVM would never exit.
 */
class ApplicationKeepAliveTest {

    @Test
    void holdsTheJvmUntilStopped() throws InterruptedException {
        var keepAlive = ApplicationKeepAlive.start();

        var thread = keepAlive.thread();
        assertThat(thread.isDaemon())
            .describedAs("a daemon thread does not hold the JVM")
            .isFalse();
        assertThat(thread.isAlive())
            .describedAs("a running application must keep the JVM alive by itself")
            .isTrue();

        keepAlive.stop();

        thread.join(5_000);
        assertThat(thread.isAlive())
            .describedAs("a stopped application must not keep the JVM alive")
            .isFalse();
    }
}
