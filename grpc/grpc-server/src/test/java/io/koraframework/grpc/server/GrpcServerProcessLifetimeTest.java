package io.koraframework.grpc.server;

import io.grpc.ForwardingServerBuilder;
import io.grpc.InsecureServerCredentials;
import io.grpc.okhttp.OkHttpServerBuilder;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.grpc.server.telemetry.GrpcServerTelemetryConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gRPC transport runs on virtual threads, which are always daemon. Without a non-daemon thread of
 * its own an application whose only server is gRPC exits with code 0 right after start, and every test
 * against it fails with UNAVAILABLE. {@code XnioLifecycle} keeps such a thread for the same reason.
 */
class GrpcServerProcessLifetimeTest {

    private static final String AWAIT_THREAD = "kora-grpc-await";

    @Test
    void runningServerHoldsANonDaemonThread() throws Exception {
        var server = new GrpcServer(builder(), config());

        server.init();
        try {
            var awaitThread = awaitThread();
            assertThat(awaitThread)
                    .describedAs("a running gRPC server must keep the JVM alive by itself")
                    .isNotNull();
            assertThat(awaitThread.isDaemon())
                    .describedAs("a daemon thread does not hold the JVM")
                    .isFalse();
        } finally {
            server.release();
        }

        // the thread is interrupted rather than joined, so give it a moment to actually finish
        for (int i = 0; i < 100 && awaitThread() != null; i++) {
            Thread.sleep(10);
        }
        assertThat(awaitThread())
                .describedAs("a released server must not keep the JVM alive")
                .isNull();
    }

    private static Thread awaitThread() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(t -> AWAIT_THREAD.equals(t.getName()))
                .findFirst()
                .orElse(null);
    }

    private static ValueOf<ForwardingServerBuilder<?>> builder() {
        // port 0 lets the OS pick a free one, so the test never collides with anything
        return () -> OkHttpServerBuilder.forPort(0, InsecureServerCredentials.create()).directExecutor();
    }

    private static ValueOf<GrpcServerConfig> config() {
        GrpcServerConfig config = new GrpcServerConfig() {

            @Override
            public Duration shutdownWait() {
                return Duration.ofSeconds(5);
            }

            @Override
            public GrpcServerTelemetryConfig telemetry() {
                throw new UnsupportedOperationException("GrpcServer must not read telemetry config");
            }

            @Override
            public Duration maxConnectionAge() {
                return null;
            }

            @Override
            public Duration maxConnectionAgeGrace() {
                return null;
            }

            @Override
            public Duration keepAliveTime() {
                return null;
            }

            @Override
            public Duration keepAliveTimeout() {
                return null;
            }
        };
        return () -> config;
    }
}
