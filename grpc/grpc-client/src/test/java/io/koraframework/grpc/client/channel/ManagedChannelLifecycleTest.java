package io.koraframework.grpc.client.channel;

import io.grpc.ServiceDescriptor;
import io.koraframework.application.graph.All;
import io.koraframework.grpc.client.GrpcClientConfig;
import io.koraframework.grpc.client.config.DefaultServiceConfig;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryConfig;
import io.koraframework.grpc.client.telemetry.impl.NoopGrpcClientTelemetry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedChannelLifecycleTest {

    @ParameterizedTest
    @CsvSource({
        "http://localhost, 80",
        "https://localhost, 443",
        "https://localhost:8443, 8443",
    })
    void telemetryReceivesDialedPort(String url, int expectedPort) {
        var telemetryUri = new AtomicReference<URI>();
        var lifecycle = new ManagedChannelLifecycle(
            config(url),
            null,
            All.of(),
            null,
            (config, service, uri) -> {
                telemetryUri.set(uri);
                return NoopGrpcClientTelemetry.INSTANCE;
            },
            new GrpcOkHttpClientChannelFactory(null),
            new ServiceDescriptor("test")
        );

        lifecycle.init();
        try {
            assertThat(telemetryUri.get().getHost()).isEqualTo("localhost");
            assertThat(telemetryUri.get().getPort()).isEqualTo(expectedPort);
        } finally {
            lifecycle.release();
        }
    }

    private static GrpcClientConfig config(String url) {
        return new GrpcClientConfig() {
            @Override
            public String url() {
                return url;
            }

            @Override
            public @Nullable Duration timeout() {
                return null;
            }

            @Override
            public GrpcClientTelemetryConfig telemetry() {
                return null;
            }

            @Override
            public @Nullable DefaultServiceConfig defaultServiceConfig() {
                return null;
            }

            @Override
            public @Nullable Duration keepAliveTime() {
                return null;
            }

            @Override
            public @Nullable Duration keepAliveTimeout() {
                return null;
            }

            @Override
            public @Nullable String loadBalancingPolicy() {
                return null;
            }
        };
    }
}
