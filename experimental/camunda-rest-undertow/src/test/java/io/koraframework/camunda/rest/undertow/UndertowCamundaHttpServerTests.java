package io.koraframework.camunda.rest.undertow;

import io.koraframework.camunda.rest.CamundaRestConfig;
import io.koraframework.camunda.rest.telemetry.CamundaRestTelemetryConfig;
import io.undertow.server.HttpHandler;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class UndertowCamundaHttpServerTests {

    @Test
    void requestStartTimeIsOnTheNanoTimeClock() throws Exception {
        var seen = new AtomicLong();
        HttpHandler handler = exchange -> {
            seen.set(exchange.getRequestStartTime());
            exchange.setStatusCode(200);
            exchange.endExchange();
        };
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var server = new UndertowCamundaHttpServer(() -> new CamundaRestConfig() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String host() {
                return "127.0.0.1";
            }

            @Override
            public Integer port() {
                return port;
            }

            @Override
            public CamundaOpenApiConfig openapi() {
                return null;
            }

            @Override
            public CamundaRestTelemetryConfig telemetry() {
                return null;
            }

            @Override
            public CamundaCorsConfig cors() {
                return null;
            }

            @Override
            public CamundaAuthConfig auth() {
                return null;
            }
        }, () -> handler);
        server.init();
        try (var client = HttpClient.newHttpClient()) {
            long before = System.nanoTime();
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/")).build(), HttpResponse.BodyHandlers.discarding());
            long after = System.nanoTime();
            assertThat(response.statusCode()).isEqualTo(200);
            // DefaultCamundaRestObservation measures processingTime as System.nanoTime() - exchange.getRequestStartTime()
            assertThat(seen.get()).isBetween(before, after);
        } finally {
            server.release();
        }
    }
}
