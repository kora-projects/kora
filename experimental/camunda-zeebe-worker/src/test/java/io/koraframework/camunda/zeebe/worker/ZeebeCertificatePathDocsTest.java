package io.koraframework.camunda.zeebe.worker;

import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsServerCredentials;
import io.grpc.okhttp.OkHttpServerBuilder;
import io.koraframework.application.graph.All;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.util.Size;
import io.koraframework.grpc.client.channel.GrpcOkHttpClientChannelFactory;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryFactory;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.*;

/**
 * documentation/camunda8-worker.md: "The connection scheme is taken from grpc.url: http opens a plaintext channel, https opens
 * a TLS channel. For a self-signed certificate, point certificatePath at the certificate file; otherwise the system trust
 * store is used." and zeebe.client.certificatePath "File path to the certificate for the connection".
 */
class ZeebeCertificatePathDocsTest {

    @Test
    void grpcChannelTrustsConfiguredCertificatePath() throws Exception {
        var cert = new File(getClass().getResource("/docs-tls/cert.pem").toURI());
        var key = new File(getClass().getResource("/docs-tls/key8.pem").toURI());
        // TLS gRPC server with a self-signed certificate and no services: a successful TLS handshake ends in UNIMPLEMENTED
        var server = OkHttpServerBuilder.forPort(0, TlsServerCredentials.create(cert, key)).build().start();
        try {
            // control: the same server is reachable over TLS when the certificate is trusted
            var trusted = io.grpc.okhttp.OkHttpChannelBuilder.forAddress("localhost", server.getPort(),
                io.grpc.TlsChannelCredentials.newBuilder().trustManager(cert).build()).build();
            try {
                var ctl = catchThrowableOfType(StatusRuntimeException.class, () -> GatewayGrpc.newBlockingStub(trusted)
                    .withDeadlineAfter(10, TimeUnit.SECONDS)
                    .topology(GatewayOuterClass.TopologyRequest.getDefaultInstance()));
                assertThat(ctl.getStatus().getCode()).as("control").isEqualTo(io.grpc.Status.Code.UNIMPLEMENTED);
            } finally {
                trusted.shutdownNow();
            }

            var config = mock(ZeebeClientConfig.class, RETURNS_DEEP_STUBS);
            when(config.certificatePath()).thenReturn(cert.getAbsolutePath());
            when(config.keepAlive()).thenReturn(Duration.ofSeconds(45));
            when(config.grpc().url()).thenReturn("https://localhost:" + server.getPort());
            when(config.grpc().maxMessageSize()).thenReturn(Size.of(4, Size.Type.MiB));
            when(config.grpc().retryPolicy().enabled()).thenReturn(false);
            GrpcClientTelemetryFactory telemetryFactory = (c, s, u) -> null;

            var wrapped = ZeebeManagedChannelFactory.build(config, All.of(), telemetryFactory, new GrpcOkHttpClientChannelFactory(null));
            ((Lifecycle) wrapped).init();
            ManagedChannel channel = wrapped.value();
            try {
                var e = catchThrowableOfType(StatusRuntimeException.class, () -> GatewayGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(10, TimeUnit.SECONDS)
                    .topology(GatewayOuterClass.TopologyRequest.getDefaultInstance()));
                assertThat(e).isNotNull();
                // UNIMPLEMENTED == TLS with the configured certificate succeeded; UNAVAILABLE(SSLHandshakeException) == certificatePath ignored
                assertThat(e.getStatus().getCode()).as(e.toString() + " cause " + e.getCause()).isEqualTo(io.grpc.Status.Code.UNIMPLEMENTED);
            } finally {
                ((Lifecycle) wrapped).release();
            }
        } finally {
            server.shutdownNow();
        }
    }
}
