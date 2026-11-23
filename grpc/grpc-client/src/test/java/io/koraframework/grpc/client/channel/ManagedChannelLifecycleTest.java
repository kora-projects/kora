package io.koraframework.grpc.client.channel;

import io.grpc.*;
import io.grpc.okhttp.OkHttpServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.koraframework.application.graph.All;
import io.koraframework.grpc.client.GrpcClientConfig;
import io.koraframework.grpc.client.config.DefaultServiceConfig;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryConfig;
import io.koraframework.grpc.client.telemetry.impl.NoopGrpcClientTelemetry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedChannelLifecycleTest {

    static final MethodDescriptor.Marshaller<String> MARSHALLER = new MethodDescriptor.Marshaller<>() {
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
    static final MethodDescriptor<String, String> UNARY = MethodDescriptor.<String, String>newBuilder()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("test.Service/unary")
        .setRequestMarshaller(MARSHALLER)
        .setResponseMarshaller(MARSHALLER)
        .build();
    static final ServiceDescriptor SERVICE = ServiceDescriptor.newBuilder("test.Service").addMethod(UNARY).build();

    private Server server;
    private ManagedChannelLifecycle lifecycle;

    @AfterEach
    void tearDown() {
        if (lifecycle != null) lifecycle.release();
        if (server != null) server.shutdownNow();
    }

    @Test
    void httpUrlWithInsecureCredentialsUsesPlaintext() throws Exception {
        var port = startServer();
        var channel = channel("http://localhost:" + port, InsecureChannelCredentials.create());

        assertThat(ClientCalls.blockingUnaryCall(channel, UNARY, CallOptions.DEFAULT, "x")).isEqualTo("ok:x");
    }

    @Test
    void httpUrlWithTlsCredentialsIgnoresThemAndUsesPlaintext() throws Exception {
        var port = startServer();
        var channel = channel("http://localhost:" + port, TlsChannelCredentials.create());

        assertThat(ClientCalls.blockingUnaryCall(channel, UNARY, CallOptions.DEFAULT, "x")).isEqualTo("ok:x");
    }

    private int startServer() throws IOException {
        server = OkHttpServerBuilder.forPort(0, InsecureServerCredentials.create())
            .addService(ServerServiceDefinition.builder(SERVICE)
                .addMethod(UNARY, ServerCalls.asyncUnaryCall((req, obs) -> {
                    obs.onNext("ok:" + req);
                    obs.onCompleted();
                }))
                .build())
            .build()
            .start();
        return server.getPort();
    }

    private ManagedChannel channel(String url, @Nullable ChannelCredentials credentials) {
        lifecycle = new ManagedChannelLifecycle(config(url), credentials, All.of(), null,
            (c, s, u) -> NoopGrpcClientTelemetry.INSTANCE, new GrpcOkHttpClientChannelFactory(null), SERVICE);
        lifecycle.init();
        return lifecycle.value();
    }

    private static GrpcClientConfig config(String url) {
        return new GrpcClientConfig() {
            @Override
            public String url() {return url;}

            @Override
            public @Nullable Duration timeout() {return Duration.ofSeconds(5);}

            @Override
            public GrpcClientTelemetryConfig telemetry() {
                return new GrpcClientTelemetryConfig() {
                    @Override
                    public GrpcClientLoggingConfig logging() {return new GrpcClientLoggingConfig() {};}

                    @Override
                    public GrpcClientMetricsConfig metrics() {return new GrpcClientMetricsConfig() {};}

                    @Override
                    public GrpcClientTracingConfig tracing() {return new GrpcClientTracingConfig() {};}
                };
            }

            @Override
            public @Nullable DefaultServiceConfig defaultServiceConfig() {return null;}

            @Override
            public @Nullable Duration keepAliveTime() {return null;}

            @Override
            public @Nullable Duration keepAliveTimeout() {return null;}

            @Override
            public @Nullable String loadBalancingPolicy() {return null;}
        };
    }
}
