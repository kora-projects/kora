package io.koraframework.http.server.netty;

import io.koraframework.application.graph.*;
import io.koraframework.common.util.*;
import io.koraframework.http.common.body.*;
import io.koraframework.http.server.common.*;
import io.koraframework.http.server.common.interceptor.*;
import io.koraframework.http.server.common.request.*;
import io.koraframework.http.server.common.response.*;
import io.koraframework.http.server.common.router.*;
import io.koraframework.http.server.common.telemetry.*;
import io.koraframework.http.server.netty.handler.*;
import io.netty.channel.*;
import io.netty.channel.nio.*;
import io.netty.channel.socket.nio.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.io.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static io.koraframework.http.common.HttpMethod.GET;
import static org.assertj.core.api.Assertions.*;

class NettyHttpServerTest extends HttpServerTestKit {

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
        assertThat(closeThread.get().getName().toLowerCase())
            .matches(name -> name.contains("loop") || name.contains("event") || name.contains("kora") || name.contains("netty"));
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
        assertThat(closeThread.get().getName().toLowerCase())
            .matches(name -> name.contains("loop") || name.contains("event") || name.contains("kora") || name.contains("netty"));
    }

    @Nested
    class NettyResponseBody {

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
    @SuppressWarnings("unchecked")
    protected HttpServer httpServer(ValueOf<? extends HttpServerConfig> config, HttpServerRouter httpServerRouter, HttpServerTelemetry telemetry) {
        var boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        var worker = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        var resources = new NettySystemHttpServerModule.NettyResources(boss, worker, NioServerSocketChannel.class);
        var serverConfig = (ValueOf<HttpServerConfig>) config;

        return new NettyHttpServer(
            "test",
            resources,
            (ValueOf<HttpServerConfig>) config,
            httpServerRouter,
            valueOf(new KoraVirtualThreadHandler(serverConfig, false)),
            valueOf(new NettyResponseWriteHandler(serverConfig)),
            null
        );
    }

    @Test
    void keepAliveConnectionIsReusedAcrossAllResponseKinds2() throws Exception {
        var small = "small".getBytes(StandardCharsets.UTF_8);
        var medium = payload(10 * 1024, 1);
        var unknownLength = payload(200 * 1024 + 5, 2);
        var knownLength = payload(300 * 1024 + 9, 3);
        startServer(
            handler(GET, "/small", _ -> HttpServerResponse.of(200, HttpBody.octetStream(small))),
            handler(GET, "/medium", _ -> HttpServerResponse.of(200, HttpBodyOutput.octetStream(os -> os.write(medium)))),
            handler(GET, "/unknown-length", _ -> HttpServerResponse.of(200, HttpBodyOutput.octetStream(os -> write(os, unknownLength, WritePattern.RANDOM_SLICES, 2)))),
            handler(GET, "/known-length", _ -> HttpServerResponse.of(200, HttpBodyOutput.octetStream(knownLength.length, os -> write(os, knownLength, WritePattern.RANDOM_SLICES, 3)))),
            handler(GET, "/empty", _ -> HttpServerResponse.of(200)),
            handler(GET, "/error", _ -> {
                throw new IllegalStateException("boom");
            }),
            handler("HEAD", "/head", _ -> HttpServerResponse.of(200, HttpBodyOutput.octetStream(knownLength.length, os -> os.write(knownLength))))
        );

        try (var raw = new RawHttpClient(port())) {
            for (var round = 0; round < 2; round++) {
                try {
                    System.out.println("[DEBUG_ROUND] Отправляем GET /small");
                    assertRawBody(raw.exchange("GET", "/small"), 200, small);

                    System.out.println("[DEBUG_ROUND] Отправляем GET /medium");
                    assertRawBody(raw.exchange("GET", "/medium"), 200, medium);

                    assertRawBody(raw.exchange("GET", "/unknown-length"), 200, unknownLength);
                    assertRawBody(raw.exchange("GET", "/known-length"), 200, knownLength);
                    assertRawBody(raw.exchange("GET", "/empty"), 200, new byte[0]);
                    assertRawBody(raw.exchange("GET", "/error"), 500, "boom".getBytes(StandardCharsets.UTF_8));
                    var head = raw.exchange("HEAD", "/head");
                    assertRawBody(head, 200, new byte[0]);
                    assertThat(head.header("Content-Length")).isEqualTo(Integer.toString(knownLength.length));
                } catch (Throwable t) {
                    System.out.println("[DEBUG_FATAL_TEST] Ошибка прямо внутри цикла теста: " + t.getClass().getName() + " -> " + t.getMessage());
                    t.printStackTrace();
                    throw t;
                }
            }
        }
    }

    private static HttpServerRequestHandler handler(String method, String route, HttpServerRequestHandler.HandlerFunction handler) {
        return new HttpServerRequestHandler() {
            @Override
            public String method() {
                return method;
            }

            @Override
            public String routeTemplate() {
                return route;
            }

            @Override
            public HttpServerResponse handle(HttpServerRequest request) throws Exception {
                return handler.apply(request);
            }
        };
    }

    enum WritePattern {
        SINGLE_WRITE,
        RANDOM_SLICES,
        BYTE_BY_BYTE,
        WITH_EMPTY_WRITES
    }

    private static void write(OutputStream os, byte[] data, WritePattern pattern, long seed) throws IOException {
        switch (pattern) {
            case SINGLE_WRITE -> os.write(data);
            case BYTE_BY_BYTE -> {
                for (var b : data) {
                    os.write(b);
                }
            }
            case RANDOM_SLICES -> {
                // written from a larger array at a non-zero offset, in sizes up to 48 KiB
                var padded = new byte[data.length + 32];
                System.arraycopy(data, 0, padded, 16, data.length);
                var random = new SplittableRandom(seed);
                for (var offset = 0; offset < data.length; ) {
                    var length = Math.min(data.length - offset, 1 + random.nextInt(48 * 1024));
                    os.write(padded, 16 + offset, length);
                    offset += length;
                }
            }
            case WITH_EMPTY_WRITES -> {
                var half = data.length / 2;
                os.write(new byte[0]);
                os.write(data, 0, 0);
                os.write(data, 0, half);
                os.write(data, half, 0);
                os.write(data, half, data.length - half);
                os.write(new byte[0]);
            }
        }
    }

    private static void assertRawBody(RawHttpClient.Response response, int code, byte[] body) {
        System.out.println("[DEBUG_TEST] Проверяем ответ. Ожидаем код: " + code + ", получили: " + response.code());
        System.out.println("[DEBUG_TEST] Длина ожидаемого тела: " + body.length + ", полученного: " + (response.body() != null ? response.body().length : "null"));

        if (response.code() != code) {
            System.out.println("[DEBUG_TEST] !! МИССМАТЧ КОДА ОТВЕТА !!");
        }

        // Сравниваем массивы побайтово вручную для 100% гарантии в логах
        if (!Arrays.equals(response.body(), body)) {
            System.out.println("[DEBUG_TEST] !! МИССМАТЧ СОДЕРЖИМОГО ТЕЛА !!");
            System.out.println("Ожидалось: " + new String(body, java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("Получено: " + (response.body() != null ? new String(response.body(), java.nio.charset.StandardCharsets.UTF_8) : "null"));
        }

        // Родной метод AssertJ для побайтового сравнения массивов
        assertThat(response.code()).isEqualTo(code);
        assertThat(response.body()).containsExactly(body);
    }

    protected void startServer(HttpServerRequestHandler... handlers) {
        this.startServer(List.of(), handlers);
    }

    protected void startServer(List<HttpServerInterceptor> interceptors, HttpServerRequestHandler... handlers) {
        startServer(false, interceptors, handlers);
    }

    protected void startServer(boolean ignoreTrailingSlash, List<HttpServerInterceptor> interceptors, HttpServerRequestHandler... handlers) {
        var config = new $HttpServerConfig_ConfigValueMapper.HttpServerConfig_Impl(
            0,
            ignoreTrailingSlash,
            Duration.ofSeconds(10),
            Duration.ofSeconds(10),
            false,
            false,
            false,
            true,
            Duration.ofSeconds(5),
            Size.of(1, Size.Type.GiB),
            new $HttpServerTelemetryConfig_ConfigValueMapper.HttpServerTelemetryConfig_Impl(
                new $HttpServerTelemetryConfig_HttpServerLoggingConfig_ConfigValueMapper.HttpServerLoggingConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerMetricsConfig_ConfigValueMapper.HttpServerMetricsConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerTracingConfig_ConfigValueMapper.HttpServerTracingConfig_Defaults()
            )
        );
        var publicApiHandler = new HttpServerRouter(List.of(handlers), interceptors, config);
        this.httpServer = this.httpServer(valueOf(config), publicApiHandler, this.telemetry);
        try {
            this.httpServer.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
