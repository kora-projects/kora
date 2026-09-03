package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.http.server.common.HttpServer;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.HttpServerTestKit;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.server.common.request.HttpServerRequestHandlerImpl;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.router.HttpServerRouter;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.undertow.handler.KoraRequestProcessingHttpHandler;
import io.koraframework.http.server.undertow.handler.KoraVirtualThreadPerConnectionDispatchHttpHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UndertowHttpServerTest extends HttpServerTestKit {

    @Test
    void responseBodyIsMaterializedOnVirtualThreadAndClosedOnIoThread() throws Exception {
        var writeThread = new AtomicReference<Thread>();
        var closeThread = new AtomicReference<Thread>();
        var closed = new CountDownLatch(1);
        var body = new HttpBodyOutput() {
            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public String contentType() {
                return "application/json";
            }

            @Override
            public void write(OutputStream os) throws IOException {
                writeThread.set(Thread.currentThread());
                os.write("{\"message\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public void close() {
                closeThread.set(Thread.currentThread());
                closed.countDown();
            }
        };
        startServer(HttpServerRequestHandlerImpl.get("/thread-ownership", _ -> HttpServerResponse.of(200, body)));

        try (var response = client.newCall(request("/thread-ownership").get().build()).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).isEqualTo("{\"message\":\"ok\"}");
        }

        assertThat(closed.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(writeThread.get()).isNotNull();
        assertThat(writeThread.get().isVirtual()).isTrue();
        assertThat(closeThread.get()).isNotNull();
        assertThat(closeThread.get().isVirtual()).isFalse();
        assertThat(closeThread.get().getName()).contains("XNIO").contains("I/O");
    }

    @Test
    void largeResponseBodyIsProducedOnVirtualThreadAndStreamedByIoThread() throws Exception {
        var expected = new byte[256 * 1024];
        Arrays.fill(expected, (byte) 'a');
        var writeThread = new AtomicReference<Thread>();
        var closeThread = new AtomicReference<Thread>();
        var closed = new CountDownLatch(1);
        var body = new HttpBodyOutput() {
            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public String contentType() {
                return "application/octet-stream";
            }

            @Override
            public void write(OutputStream os) throws IOException {
                writeThread.set(Thread.currentThread());
                for (var offset = 0; offset < expected.length; offset += 4096) {
                    os.write(expected, offset, Math.min(4096, expected.length - offset));
                }
            }

            @Override
            public void close() {
                closeThread.set(Thread.currentThread());
                closed.countDown();
            }
        };
        startServer(HttpServerRequestHandlerImpl.get("/large-thread-ownership", _ -> HttpServerResponse.of(200, body)));

        try (var response = client.newCall(request("/large-thread-ownership").get().build()).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().bytes()).isEqualTo(expected);
        }

        assertThat(closed.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(writeThread.get()).isNotNull();
        assertThat(writeThread.get().isVirtual()).isTrue();
        assertThat(closeThread.get()).isNotNull();
        assertThat(closeThread.get().isVirtual()).isFalse();
        assertThat(closeThread.get().getName()).contains("XNIO").contains("I/O");
    }

    @Override
    protected HttpServer httpServer(ValueOf<? extends HttpServerConfig> config, HttpServerRouter httpServerRouter, HttpServerTelemetry telemetry) {
        return new UndertowHttpServer(
            "test",
            valueOf(new UndertowConfig() {}),
            valueOf(new KoraVirtualThreadPerConnectionDispatchHttpHandler("uvt", new KoraRequestProcessingHttpHandler(valueOf(new UndertowConfig() {}), (ValueOf<HttpServerConfig>) config, telemetry, httpServerRouter))),
            null,
            config,
            null,
            null
        );
    }
}
