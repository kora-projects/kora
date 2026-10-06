package io.koraframework.grpc.client.telemetry;

import io.grpc.*;
import io.grpc.okhttp.OkHttpServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import io.koraframework.application.graph.All;
import io.koraframework.grpc.client.GrpcClientConfig;
import io.koraframework.grpc.client.channel.GrpcOkHttpClientChannelFactory;
import io.koraframework.grpc.client.channel.ManagedChannelLifecycle;
import io.koraframework.grpc.client.config.DefaultServiceConfig;
import io.koraframework.grpc.client.telemetry.impl.DefaultGrpcClientTelemetryFactory;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcClientTelemetryTest {

    private static final MethodDescriptor.Marshaller<String> STRING_MARSHALLER = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    };

    private static final MethodDescriptor<String, String> UNARY = MethodDescriptor.<String, String>newBuilder()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName(MethodDescriptor.generateFullMethodName("test.TestService", "unary"))
        .setRequestMarshaller(STRING_MARSHALLER)
        .setResponseMarshaller(STRING_MARSHALLER)
        .build();

    private static final ServiceDescriptor SERVICE = ServiceDescriptor.newBuilder("test.TestService")
        .addMethod(UNARY)
        .build();

    private Server server;
    private ManagedChannelLifecycle channel;

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.release();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void asyncObserverOnNextThrowsRecordsCallDurationOnce() throws Exception {
        var port = startServer();
        var registry = new SimpleMeterRegistry();
        var ch = startChannel(port, new DefaultGrpcClientTelemetryFactory(null, registry, null, null));

        var executor = Executors.newSingleThreadExecutor();
        var done = new CountDownLatch(1);
        ClientCalls.asyncUnaryCall(ch.newCall(UNARY, CallOptions.DEFAULT.withExecutor(executor)), "request", new StreamObserver<>() {
            @Override
            public void onNext(String value) {
                throw new IllegalStateException("onNext failure");
            }

            @Override
            public void onError(Throwable t) {
                done.countDown();
            }

            @Override
            public void onCompleted() {
                done.countDown();
            }
        });
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        // listener callbacks run on this executor, so the call is fully closed once it terminates
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        var samples = registry.getMeters().stream()
            .filter(m -> m instanceof Timer)
            .mapToLong(m -> ((Timer) m).count())
            .sum();
        assertThat(samples).as("call duration samples for a single call").isEqualTo(1);
    }

    private int startServer() throws IOException {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var service = ServerServiceDefinition.builder(SERVICE)
            .addMethod(UNARY, ServerCalls.asyncUnaryCall((String request, StreamObserver<String> observer) -> {
                observer.onNext("response");
                observer.onCompleted();
            }))
            .build();
        server = OkHttpServerBuilder.forPort(port, InsecureServerCredentials.create())
            .addService(service)
            .build()
            .start();
        return port;
    }

    private ManagedChannel startChannel(int port, GrpcClientTelemetryFactory telemetryFactory) {
        var config = new GrpcClientConfig() {
            @Override
            public String url() {
                return "http://localhost:" + port;
            }

            @Override
            public @Nullable Duration timeout() {
                return null;
            }

            @Override
            public GrpcClientTelemetryConfig telemetry() {
                return new GrpcClientTelemetryConfig() {
                    @Override
                    public GrpcClientLoggingConfig logging() {
                        return new GrpcClientLoggingConfig() {};
                    }

                    @Override
                    public GrpcClientMetricsConfig metrics() {
                        return new GrpcClientMetricsConfig() {
                            @Override
                            public boolean enabled() {
                                return true;
                            }
                        };
                    }

                    @Override
                    public GrpcClientTracingConfig tracing() {
                        return new GrpcClientTracingConfig() {};
                    }
                };
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
        channel = new ManagedChannelLifecycle(config, null, new All.StaticAll<>(List.of()), null, telemetryFactory,
            new GrpcOkHttpClientChannelFactory(null), SERVICE);
        channel.init();
        return channel.value();
    }
}
