package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.http.server.common.HttpServer;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.HttpServerTestKit;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.server.common.RawHttpClient;
import io.koraframework.http.server.common.request.HttpServerRequestHandlerImpl;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.router.HttpServerRouter;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.undertow.handler.KoraRequestProcessingHttpHandler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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

    @Test
    void expectContinueIsAnsweredWith100ContinueBeforeBodyIsSent() throws Exception {
        startServer(HttpServerRequestHandlerImpl.post("/upload", request -> {
            try (var body = request.body(); var is = body.asInputStream()) {
                return HttpServerResponse.of(200, HttpBody.plaintext("got " + is.readAllBytes().length));
            }
        }));

        try (var raw = new RawHttpClient(port())) {
            raw.send("POST /upload HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\nExpect: 100-continue\r\n\r\n");
            assertThat(raw.readHead().code()).isEqualTo(100);

            raw.send("hello");
            var response = raw.readResponse(false);
            assertThat(response.code()).isEqualTo(200);
            assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("got 5");
        }
    }

    /**
     * Undertow specifics of {@link KoraRequestProcessingHttpHandler} on top of {@link HttpServerTestKit.ResponseBodyTest}:
     * bodies of unknown length up to 64 KiB are buffered and sent with a Content-Length, larger ones are streamed
     * chunked in 16 KiB pieces with at most 4 chunks pending on a slow client.
     */
    @Nested
    class UndertowResponseBody {

        static final int SMALL_BODY_THRESHOLD = 64 * 1024;
        static final int STREAM_CHUNK_SIZE = 16 * 1024;
        static final int MAX_PENDING_CHUNKS = 4;

        @ParameterizedTest(name = "{0} bytes, declared length: {1}")
        @CsvSource({
            "0, false", "1, false", "65535, false", "65536, false", "65537, false", "81920, false", "1048583, false",
            "0, true", "65536, true", "65537, true", "1048583, true"
        })
        void bodyIsBufferedUpToThresholdAndChunkedAboveIt(int size, boolean declaredLength) throws Exception {
            var data = payload(size, size);
            var body = new TrackingBody(declaredLength ? size : -1, os -> {
                // small writes, so crossing the threshold happens in the middle of a write sequence
                for (var offset = 0; offset < data.length; offset += 1000) {
                    os.write(data, offset, Math.min(1000, data.length - offset));
                }
            });
            startServer(HttpServerRequestHandlerImpl.get("/body", _ -> HttpServerResponse.of(200, body)));

            try (var response = client.newCall(request("/body").get().build()).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().bytes()).isEqualTo(data);
                if (declaredLength || size <= SMALL_BODY_THRESHOLD) {
                    assertThat(response.header("Content-Length")).isEqualTo(Integer.toString(size));
                    assertThat(response.header("Transfer-Encoding")).isNull();
                } else {
                    assertThat(response.header("Content-Length")).isNull();
                    assertThat(response.header("Transfer-Encoding")).isEqualToIgnoringCase("chunked");
                }
            }
            body.assertClosedOnce();
        }

        @Test
        void slowClientKeepsAtMostPendingChunksInMemory() throws Exception {
            var chunk = payload(STREAM_CHUNK_SIZE, 6);
            var total = 32L * 1024 * 1024;
            var produced = new AtomicLong();
            var body = new TrackingBody(-1, os -> {
                while (produced.get() < total) {
                    os.write(chunk);
                    produced.addAndGet(chunk.length);
                }
            });
            startServer(HttpServerRequestHandlerImpl.get("/slow", _ -> HttpServerResponse.of(200, body)));

            try (var raw = new RawHttpClient(port(), 16 * 1024)) {
                raw.send(RawHttpClient.request("GET", "/slow"));
                var head = raw.readHead();
                assertThat(head.header("Transfer-Encoding")).isEqualToIgnoringCase("chunked");

                Thread.sleep(250);
                var stalledAt = produced.get();
                Thread.sleep(250);
                assertThat(stalledAt).isLessThan(total);
                assertThat(produced.get()).isLessThanOrEqualTo(stalledAt + (long) MAX_PENDING_CHUNKS * STREAM_CHUNK_SIZE);

                assertThat(raw.readBody(head)).hasSize((int) total);
            }
            body.assertClosedOnce();
        }
    }

    @Override
    protected HttpServer httpServer(ValueOf<? extends HttpServerConfig> config, HttpServerRouter httpServerRouter, HttpServerTelemetry telemetry) {
        return new UndertowHttpServer(
            "test",
            valueOf(new UndertowConfig() {}),
            valueOf(new UndertowHttpServerFactoryModule("test", "httpServer").handler(valueOf(new UndertowConfig() {}), config.get(), httpServerRouter, (_, _, _) -> telemetry)),
            null,
            (ValueOf<HttpServerConfig>) config,
            null
        );
    }
}
