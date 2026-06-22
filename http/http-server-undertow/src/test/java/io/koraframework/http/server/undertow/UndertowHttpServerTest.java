package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.util.Size;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.server.common.HttpServer;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.HttpServerTestKit;
import io.koraframework.http.server.common.request.HttpServerRequestHandlerImpl;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.router.HttpServerHandler;
import io.koraframework.http.server.common.telemetry.*;
import io.koraframework.http.server.common.telemetry.impl.NoopHttpServerTelemetry;
import io.koraframework.http.server.undertow.handler.KoraRequestProcessingHttpHandler;
import io.koraframework.http.server.undertow.handler.KoraVirtualThreadDispatchHttpHandler;
import okhttp3.Request;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UndertowHttpServerTest extends HttpServerTestKit {

    @Override
    protected HttpServer httpServer(ValueOf<? extends HttpServerConfig> config, HttpServerHandler httpServerHandler, HttpServerTelemetry telemetry) {
        return new UndertowHttpServer(
            "test",
            valueOf(new KoraVirtualThreadDispatchHttpHandler("uvt", new KoraRequestProcessingHttpHandler(telemetry, httpServerHandler))),
            null,
            config,
            null,
            null
        );
    }

    @Test
    void corsActualRequest() throws Exception {
        var server = this.corsServer();
        try {
            server.init();

            var request = new Request.Builder()
                .url("http://localhost:%d/".formatted(server.port()))
                .get()
                .header("Origin", "https://example.com")
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("https://example.com");
                assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
                assertThat(response.header("Vary")).isEqualTo("Origin");
                assertThat(response.body().string()).isEqualTo("ok");
            }
        } finally {
            server.release();
        }
    }

    @Test
    void corsPreflightRequest() throws Exception {
        var server = this.corsServer();
        try {
            server.init();

            var request = new Request.Builder()
                .url("http://localhost:%d/".formatted(server.port()))
                .method("OPTIONS", null)
                .header("Origin", "https://example.com")
                .header("Access-Control-Request-Method", "POST")
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(204);
                assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("https://example.com");
                assertThat(response.header("Access-Control-Allow-Methods")).isEqualTo("GET,POST,PUT,DELETE,PATCH,OPTIONS,HEAD");
                assertThat(response.body().string()).isEmpty();
            }
        } finally {
            server.release();
        }
    }

    private UndertowHttpServer corsServer() {
        var config = new TestHttpServerConfig();
        var handler = new HttpServerHandler(List.of(new HttpServerRequestHandlerImpl(
            "GET",
            "/",
            request -> HttpServerResponse.of(200, HttpBody.plaintext("ok"))
        )), List.of(), config);
        var processingHandler = new KoraRequestProcessingHttpHandler(NoopHttpServerTelemetry.INSTANCE, handler);
        return new UndertowHttpServer(
            "test-cors",
            valueOf(processingHandler),
            null,
            valueOf(config),
            null,
            null
        );
    }

    private record TestHttpServerConfig() implements HttpServerConfig {
        @Override
        public int port() {
            return 0;
        }

        @Override
        public Duration socketReadTimeout() {
            return Duration.ofSeconds(1);
        }

        @Override
        public Duration socketWriteTimeout() {
            return Duration.ofSeconds(1);
        }

        @Override
        public Duration shutdownWait() {
            return Duration.ofMillis(1);
        }

        @Override
        public HttpServerTelemetryConfig telemetry() {
            return new $HttpServerTelemetryConfig_ConfigValueExtractor.HttpServerTelemetryConfig_Impl(
                new $HttpServerTelemetryConfig_HttpServerLoggingConfig_ConfigValueExtractor.HttpServerLoggingConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerMetricsConfig_ConfigValueExtractor.HttpServerMetricsConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerTracingConfig_ConfigValueExtractor.HttpServerTracingConfig_Defaults()
            );
        }

        @Override
        public Size maxRequestBodySize() {
            return Size.of(1, Size.Type.GiB);
        }
    }
}
