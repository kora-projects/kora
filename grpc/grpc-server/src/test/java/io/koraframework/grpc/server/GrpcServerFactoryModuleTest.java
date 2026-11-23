package io.koraframework.grpc.server;

import io.grpc.InsecureChannelCredentials;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.koraframework.common.util.Size;
import io.koraframework.grpc.server.app.EventService;
import io.koraframework.grpc.server.events.EventsGrpc;
import io.koraframework.grpc.server.events.SendEventRequest;
import io.koraframework.grpc.server.handler.DynamicBindableService;
import io.koraframework.grpc.server.telemetry.GrpcServerTelemetryConfig;
import io.koraframework.grpc.server.telemetry.impl.NoopGrpcServerTelemetry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcServerFactoryModuleTest {

    @Test
    void maxMessageSizeOf2GiBDoesNotOverflow() throws Exception {
        assertServerAcceptsMessage(Size.of(2, Size.Type.GiB));
    }

    @Test
    void maxMessageSizeOf4GiBDoesNotOverflow() throws Exception {
        assertServerAcceptsMessage(Size.of(4, Size.Type.GiB));
    }

    private void assertServerAcceptsMessage(Size maxMessageSize) throws Exception {
        var config = config(maxMessageSize);
        var module = new GrpcServerFactoryModule("test", "grpcServer");
        var services = List.of(new DynamicBindableService(() -> new EventService("ok")));
        var builder = module.grpcServerBuilder(() -> config, services, List.of(), null, null, (name, port, telemetryConfig) -> NoopGrpcServerTelemetry.INSTANCE);

        var server = builder.build().start();
        var channel = OkHttpChannelBuilder.forAddress("localhost", server.getPort(), InsecureChannelCredentials.create()).build();
        try {
            var request = SendEventRequest.newBuilder().setEvent("event").build();
            var response = EventsGrpc.newBlockingStub(channel).sendEvent(request);
            assertThat(response.getRes()).isEqualTo("ok");
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    private static GrpcServerConfig config(Size maxMessageSize) {
        return new GrpcServerConfig() {
            @Override
            public int port() {
                return 0;
            }

            @Override
            public Size maxMessageSize() {
                return maxMessageSize;
            }

            @Override
            public GrpcServerTelemetryConfig telemetry() {
                return null;
            }

            @Override
            public @Nullable Duration maxConnectionAge() {
                return null;
            }

            @Override
            public @Nullable Duration maxConnectionAgeGrace() {
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
        };
    }
}
