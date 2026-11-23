package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.http.server.common.HttpServer;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.HttpServerTestKit;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.server.common.RawHttpClient;
import io.koraframework.http.server.common.request.HttpServerRequestHandlerImpl;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.router.HttpServerRouter;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.undertow.handler.KoraRequestProcessingHttpHandler;
import io.koraframework.http.server.undertow.handler.KoraVirtualThreadPerConnectionDispatchHttpHandler;
import io.koraframework.common.util.Size;
import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.telemetry.HttpServerObservation;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

    /**
     * Response sending and request body limits on a server with its own config and a telemetry that records the
     * order of observation events.
     */
    @Nested
    class ResponseLifecycle {

        final List<String> events = new CopyOnWriteArrayList<>();
        final HttpServerTelemetry recordingTelemetry = _ -> new HttpServerObservation() {
            @Override
            public void observeResultCode(HttpResultCode resultCode) {
                events.add("code:" + resultCode);
            }

            @Override
            public HttpServerRequest observeRequest(HttpServerRequest request) {
                return request;
            }

            @Override
            public HttpServerResponse observeResponse(HttpServerResponse response) {
                return response;
            }

            @Override
            public Span span() {
                return Span.getInvalid();
            }

            @Override
            public void end() {
                events.add("end");
            }

            @Override
            public void observeError(Throwable e) {
                events.add("error:" + e.getClass().getSimpleName());
            }
        };
        @Nullable UndertowHttpServer server;

        @AfterEach
        void stop() {
            if (this.server != null) {
                this.server.release();
            }
        }

        void start(Duration writeTimeout, Size maxRequestBodySize, HttpServerRequestHandler... handlers) {
            var config = new HttpServerConfig() {
                @Override
                public int port() {
                    return 0;
                }

                @Override
                public Duration socketWriteTimeout() {
                    return writeTimeout;
                }

                @Override
                public Duration shutdownWait() {
                    return Duration.ofSeconds(1);
                }

                @Override
                public Size maxRequestBodySize() {
                    return maxRequestBodySize;
                }

                @Override
                public HttpServerTelemetryConfig telemetry() {
                    return null;
                }
            };
            var router = new HttpServerRouter(List.of(handlers), List.of(), config);
            var handler = new KoraVirtualThreadPerConnectionDispatchHttpHandler("uvt", new KoraRequestProcessingHttpHandler(valueOf(new UndertowConfig() {}), config, router, this.recordingTelemetry));
            this.server = new UndertowHttpServer("test", valueOf(new UndertowConfig() {}), valueOf(handler), null, valueOf(config), null);
            this.server.init();
        }

        void start(HttpServerRequestHandler... handlers) {
            start(Duration.ZERO, Size.of(1, Size.Type.GiB), handlers);
        }

        static byte[] readRequestBody(HttpServerRequest request) throws IOException {
            try (var body = request.body(); var is = body.asInputStream()) {
                return is.readAllBytes();
            }
        }

        void awaitEvent(String event) throws InterruptedException {
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!this.events.contains(event) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(this.events).contains(event);
        }

        @Test
        void fullResponseIsSentAfterRequestBodyWasRead() throws Exception {
            var data = payload(4 * 1024 * 1024, 1);
            start(HttpServerRequestHandlerImpl.post("/full", request -> {
                readRequestBody(request);
                return HttpServerResponse.of(200, HttpBody.octetStream(data));
            }));

            try (var raw = new RawHttpClient(this.server.port())) {
                raw.send("POST /full HTTP/1.1\r\nHost: localhost\r\nContent-Length: 3\r\n\r\nabc");
                var head = raw.readHead();
                assertThat(raw.readBytes(data.length)).isEqualTo(data);
                assertThat(head.code()).isEqualTo(200);
            }
            assertThat(this.events).doesNotContain("code:" + HttpResultCode.CONNECTION_ERROR);
        }

        @Test
        void streamedResponseIsSentToSlowClientAfterRequestBodyWasClosed() throws Exception {
            var data = payload(8 * 1024 * 1024, 2);
            var body = new TrackingBody(-1, os -> {
                for (var offset = 0; offset < data.length; offset += 8192) {
                    os.write(data, offset, 8192);
                }
            });
            start(HttpServerRequestHandlerImpl.post("/stream", request -> {
                request.body().close();
                return HttpServerResponse.of(200, body);
            }));

            try (var raw = new RawHttpClient(this.server.port(), 8 * 1024)) {
                raw.send("POST /stream HTTP/1.1\r\nHost: localhost\r\nContent-Length: 2\r\n\r\n{}");
                Thread.sleep(500);
                var head = raw.readHead();
                assertThat(raw.readBody(head)).isEqualTo(data);
            }
            body.assertClosedOnce();
        }

        @Test
        void writeTimeoutDuringStreamingDoesNotEndResponseWithTerminatingChunk() throws Exception {
            var data = payload(200 * 1024, 3);
            var body = new TrackingBody(-1, os -> {
                os.write(data, 0, 100 * 1024);
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
                os.write(data, 100 * 1024, 100 * 1024);
            });
            start(Duration.ofSeconds(1), Size.of(1, Size.Type.GiB), HttpServerRequestHandlerImpl.get("/stream", _ -> HttpServerResponse.of(200, body)));

            try (var raw = new RawHttpClient(this.server.port())) {
                raw.send(RawHttpClient.request("GET", "/stream"));
                var head = raw.readHead();
                assertThat(head.header("Transfer-Encoding")).isEqualToIgnoringCase("chunked");
                byte[] received;
                try {
                    received = raw.readBody(head);
                } catch (IOException expected) {
                    received = null;
                }
                assertThat(received).as("cut off response must not look complete").satisfiesAnyOf(
                    r -> assertThat(r).isNull(),
                    r -> assertThat(r).isEqualTo(data)
                );
            }
            body.assertClosedOnce();
        }

        @Test
        void writeTimeoutOnStalledClientClosesBodyAndObservesConnectionError() throws Exception {
            var chunk = new byte[16 * 1024];
            var body = new TrackingBody(-1, os -> {
                for (var i = 0; i < 100_000; i++) {
                    os.write(chunk);
                }
            });
            start(Duration.ofSeconds(1), Size.of(1, Size.Type.GiB), HttpServerRequestHandlerImpl.get("/stream", _ -> HttpServerResponse.of(200, body)));

            try (var raw = new RawHttpClient(this.server.port(), 16 * 1024)) {
                raw.send(RawHttpClient.request("GET", "/stream"));
                body.assertClosedOnce();
            }
            awaitEvent("end");
            var codeIndex = this.events.indexOf("code:" + HttpResultCode.CONNECTION_ERROR);
            assertThat(codeIndex).as("events: %s", this.events).isNotNegative().isLessThan(this.events.indexOf("end"));
            assertThat(this.events).filteredOn("end"::equals).hasSize(1);
        }

        @Test
        void nullResponseEndsObservation() throws Exception {
            start(HttpServerRequestHandlerImpl.get("/null", _ -> null));

            try (var raw = new RawHttpClient(this.server.port())) {
                assertThat(raw.exchange("GET", "/null").code()).isEqualTo(500);
            }
            awaitEvent("end");
            assertThat(this.events).contains("error:IllegalStateException");
        }

        @Test
        void failingContentLengthEndsObservationAndClosesBody() throws Exception {
            var closed = new CountDownLatch(1);
            var body = new HttpBodyOutput() {
                @Override
                public long contentLength() {
                    throw new IllegalStateException("unknown size");
                }

                @Override
                public String contentType() {
                    return "text/plain";
                }

                @Override
                public void write(OutputStream os) throws IOException {
                    os.write('x');
                }

                @Override
                public void close() {
                    closed.countDown();
                }
            };
            start(HttpServerRequestHandlerImpl.get("/body", _ -> HttpServerResponse.of(200, body)));

            try (var raw = new RawHttpClient(this.server.port())) {
                assertThat(raw.exchange("GET", "/body").code()).isEqualTo(500);
            }
            awaitEvent("end");
            assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "Content-Length: 2048\r\n\r\n",
            "Transfer-Encoding: chunked\r\n\r\n800\r\n"
        })
        void requestBodyOverLimitIs413(String framing) throws Exception {
            start(Duration.ZERO, Size.of(1, Size.Type.KiB), HttpServerRequestHandlerImpl.post("/upload", request ->
                HttpServerResponse.of(200, HttpBody.plaintext("read " + readRequestBody(request).length))));

            try (var raw = new RawHttpClient(this.server.port())) {
                var chunked = framing.startsWith("Transfer-Encoding");
                raw.send("POST /upload HTTP/1.1\r\nHost: localhost\r\n" + framing + "a".repeat(2048) + (chunked ? "\r\n0\r\n\r\n" : ""));
                assertThat(raw.readResponse(false).code()).isEqualTo(413);
            }
        }

        @Test
        void requestBodyAtLimitIsAccepted() throws Exception {
            start(Duration.ZERO, Size.of(1, Size.Type.KiB), HttpServerRequestHandlerImpl.post("/upload", request ->
                HttpServerResponse.of(200, HttpBody.plaintext("read " + readRequestBody(request).length))));

            try (var raw = new RawHttpClient(this.server.port())) {
                raw.send("POST /upload HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n400\r\n" + "a".repeat(1024) + "\r\n0\r\n\r\n");
                var response = raw.readResponse(false);
                assertThat(response.code()).isEqualTo(200);
                assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("read 1024");
            }
        }
    }

    @Override
    protected HttpServer httpServer(ValueOf<? extends HttpServerConfig> config, HttpServerRouter httpServerRouter, HttpServerTelemetry telemetry) {
        return new UndertowHttpServer(
            "test",
            valueOf(new UndertowConfig() {}),
            valueOf(new KoraVirtualThreadPerConnectionDispatchHttpHandler("uvt", new KoraRequestProcessingHttpHandler(valueOf(new UndertowConfig() {}), config.get(), httpServerRouter, telemetry))),
            null,
            (ValueOf<HttpServerConfig>) config,
            null
        );
    }
}
