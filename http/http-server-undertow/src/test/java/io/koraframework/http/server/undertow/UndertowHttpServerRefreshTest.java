package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.http.server.common.$HttpServerConfig_ConfigValueMapper;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.undertow.server.HttpHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UndertowHttpServerRefreshTest {

    @Test
    void servesRequestsWithHandlerReplacedByGraphRefresh() throws Exception {
        var handler = new AtomicReference<HttpHandler>(exchange -> exchange.setStatusCode(418));
        ValueOf<HttpHandler> handlerValue = handler::get;
        var server = new UndertowHttpServer("test", handlerValue, null, config(), null, null);

        server.init();
        try {
            assertThat(status(server.port())).isEqualTo(418);

            handler.set(exchange -> exchange.setStatusCode(204));

            assertThat(status(server.port())).isEqualTo(204);
        } finally {
            server.release();
        }
    }

    private static ValueOf<HttpServerConfig> config() {
        var telemetry = $HttpServerConfig_ConfigValueMapper.DEFAULTS.telemetry();
        HttpServerConfig config = new HttpServerConfig() {
            @Override
            public int port() {
                return 0;
            }

            @Override
            public HttpServerTelemetryConfig telemetry() {
                return telemetry;
            }
        };
        return () -> config;
    }

    private static int status(int port) throws IOException {
        var connection = (HttpURLConnection) URI.create("http://localhost:" + port + "/").toURL().openConnection();
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }
}
