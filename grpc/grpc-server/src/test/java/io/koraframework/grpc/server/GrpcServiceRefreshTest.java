package io.koraframework.grpc.server;

import io.grpc.BindableService;
import io.grpc.ForwardingServerBuilder;
import io.grpc.InsecureChannelCredentials;
import io.grpc.Server;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.koraframework.application.graph.All;
import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.WrappedRefreshListener;
import io.koraframework.common.util.Size;
import io.koraframework.grpc.server.app.EventService;
import io.koraframework.grpc.server.events.EventsGrpc;
import io.koraframework.grpc.server.events.SendEventRequest;
import io.koraframework.grpc.server.handler.DynamicBindableService;
import io.koraframework.grpc.server.telemetry.GrpcServerTelemetry;
import io.koraframework.grpc.server.telemetry.GrpcServerTelemetryConfig;
import io.koraframework.grpc.server.telemetry.GrpcServerTelemetryFactory;
import io.koraframework.grpc.server.telemetry.impl.NoopGrpcServerTelemetry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wired like GrpcServerFactoryModule in the generated graph: the server reads the builder through ValueOf, services are
 * swapped in by the DynamicBindableService refresh listener.
 */
class GrpcServiceRefreshTest {

    @Test
    void serviceIsSwappedAfterBuilderWasRecreatedByEarlierRefresh() throws Exception {
        var module = new GrpcServerFactoryModule("test", "grpcServer");
        var serviceValue = new AtomicReference<>("v1");
        var config = config();

        var draw = new ApplicationGraphDraw(GrpcServiceRefreshTest.class);
        // e.g. built from the tracing / metrics config; a new instance per refresh, otherwise the graph keeps the old one and the builder is not refreshed
        var telemetryFactoryNode = draw.addNode(GrpcServerTelemetryFactory.class, null, null, List.of(), List.of(), List.of(),
            _ -> new GrpcServerTelemetryFactory() {
                @Override
                public GrpcServerTelemetry get(String name, int port, GrpcServerTelemetryConfig telemetryConfig) {
                    return NoopGrpcServerTelemetry.INSTANCE;
                }
            });
        var configNode = draw.addNode(GrpcServerConfig.class, null, null, List.of(), List.of(), List.of(), _ -> config);
        // a gRPC service that depends on its own config section
        var serviceConfigNode = draw.addNode(String.class, null, null, List.of(), List.of(), List.of(), _ -> serviceValue.get());
        var serviceNode = draw.addNode(BindableService.class, null, null, List.of(serviceConfigNode), List.of(serviceConfigNode), List.of(),
            g -> new EventService(g.get(serviceConfigNode)));
        var listenerNode = draw.addNode(WrappedRefreshListener.class, null, null, List.of(serviceNode), List.of(), List.of(),
            g -> module.dynamicBindableServicesListener(All.of(g.valueOf(serviceNode))));
        @SuppressWarnings("unchecked")
        var builderNode = draw.addNode(ForwardingServerBuilder.class, null, null, List.of(configNode, listenerNode, telemetryFactoryNode), List.of(listenerNode, telemetryFactoryNode), List.of(),
            g -> module.grpcServerBuilder(g.valueOf(configNode), (List<DynamicBindableService>) ((WrappedRefreshListener<?>) g.get(listenerNode)).value(), List.of(), null, null, g.get(telemetryFactoryNode)));
        var serverNode = draw.addNode(GrpcServer.class, null, null, List.of(builderNode, configNode), List.of(), List.of(),
            g -> module.grpcServer(g.valueOf(builderNode).map(b -> (ForwardingServerBuilder<?>) b), g.valueOf(configNode)));
        var graph = draw.init();
        var server = graph.get(serverNode);
        var portField = GrpcServer.class.getDeclaredField("server");
        portField.setAccessible(true);
        var port = ((Server) portField.get(server)).getPort();
        var channel = OkHttpChannelBuilder.forAddress("localhost", port, InsecureChannelCredentials.create()).build();
        try {
            var stub = EventsGrpc.newBlockingStub(channel);
            var request = SendEventRequest.newBuilder().setEvent("e").build();
            assertThat(stub.sendEvent(request).getRes()).isEqualTo("v1");

            // 1. the tracing/metrics config changes: telemetry factory and builder are recreated, the server keeps running
            graph.refresh(telemetryFactoryNode);
            assertThat(stub.sendEvent(request).getRes()).isEqualTo("v1");

            // 2. the service config changes: the service is recreated and must be swapped in
            serviceValue.set("v2");
            graph.refresh(serviceConfigNode);
            assertThat(stub.sendEvent(request).getRes()).as("response of the refreshed service").isEqualTo("v2");
        } finally {
            channel.shutdownNow();
            graph.release();
        }
    }

    private static GrpcServerConfig config() {
        return new GrpcServerConfig() {
            public int port() {return 0;}
            public Size maxMessageSize() {return Size.of(4, Size.Type.MiB);}
            public GrpcServerTelemetryConfig telemetry() {return null;}
            public @Nullable Duration maxConnectionAge() {return null;}
            public @Nullable Duration maxConnectionAgeGrace() {return null;}
            public @Nullable Duration keepAliveTime() {return null;}
            public @Nullable Duration keepAliveTimeout() {return null;}
            public Duration shutdownWait() {return Duration.ofMillis(100);}
        };
    }
}
