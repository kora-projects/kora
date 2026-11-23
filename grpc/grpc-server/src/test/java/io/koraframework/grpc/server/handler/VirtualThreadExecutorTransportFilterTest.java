package io.koraframework.grpc.server.handler;

import io.koraframework.grpc.server.GrpcServer;
import io.koraframework.grpc.server.GrpcServerConfig;
import io.koraframework.grpc.server.GrpcServerFactoryModule;
import io.koraframework.grpc.server.app.EventService;
import io.koraframework.grpc.server.telemetry.GrpcServerTelemetryConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class VirtualThreadExecutorTransportFilterTest {

    @Test
    void transportTerminatedBeforeReadyDoesNotThrow() {
        // grpc passes null attributes when a transport terminates before transportReady was called
        assertThatCode(() -> VirtualThreadExecutorTransportFilter.INSTANCE.transportTerminated(null)).doesNotThrowAnyException();
    }

    @Test
    void connectionClosedBeforeHandshakeDoesNotDelayGracefulShutdown() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var config = config(port);
        var builder = new GrpcServerFactoryModule("test", "grpcServer")
            .grpcServerBuilder(() -> config, List.of(new DynamicBindableService(() -> new EventService("test"))), List.of(), null, null,
                (name, p, telemetryConfig) -> (call, headers) -> {throw new IllegalStateException("no calls expected");});
        var server = new GrpcServer(() -> builder, () -> config);
        server.init();
        try {
            try (var probe = new Socket("localhost", port)) {
                // connect and close without the HTTP/2 preface, like a k8s tcpSocket probe
            }
            Thread.sleep(500);
        } finally {
            long start = System.nanoTime();
            server.release();
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            assertThat(tookMs).as("graceful shutdown took %d ms", tookMs).isLessThan(2000);
        }
    }

    private static GrpcServerConfig config(int port) {
        return new GrpcServerConfig() {
            @Override
            public int port() {return port;}

            @Override
            public Duration shutdownWait() {return Duration.ofSeconds(3);}

            @Override
            public GrpcServerTelemetryConfig telemetry() {
                return new GrpcServerTelemetryConfig() {
                    @Override
                    public GrpcServerLoggingConfig logging() {return new GrpcServerLoggingConfig() {};}

                    @Override
                    public GrpcServerMetricsConfig metrics() {return new GrpcServerMetricsConfig() {};}

                    @Override
                    public GrpcServerTracingConfig tracing() {return new GrpcServerTracingConfig() {};}
                };
            }

            @Override
            public @Nullable Duration maxConnectionAge() {return null;}

            @Override
            public @Nullable Duration maxConnectionAgeGrace() {return null;}

            @Override
            public @Nullable Duration keepAliveTime() {return null;}

            @Override
            public @Nullable Duration keepAliveTimeout() {return null;}
        };
    }
}
