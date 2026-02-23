package ru.tinkoff.kora.http.server.common;

import io.opentelemetry.api.trace.Span;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.liveness.LivenessProbe;
import ru.tinkoff.kora.common.liveness.LivenessProbeFailure;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.common.util.Size;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;
import ru.tinkoff.kora.http.server.common.$HttpServerConfig_ConfigValueExtractor.HttpServerConfig_Impl;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.http.server.common.privateapi.LivenessHandler;
import ru.tinkoff.kora.http.server.common.privateapi.MetricsHandler;
import ru.tinkoff.kora.http.server.common.privateapi.ReadinessHandler;
import ru.tinkoff.kora.http.server.common.router.HttpServerHandler;
import ru.tinkoff.kora.http.server.common.telemetry.*;
import ru.tinkoff.kora.telemetry.common.MetricsScraper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;
import static ru.tinkoff.kora.http.common.HttpMethod.GET;
import static ru.tinkoff.kora.http.common.HttpMethod.POST;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public abstract class HttpServerTestKit {
    protected static MetricsScraper registry = Mockito.mock(MetricsScraper.class);
    private final ReadinessProbe readinessProbe = Mockito.mock(ReadinessProbe.class);
    private final SettablePromiseOf<ReadinessProbe> readinessProbePromise = new SettablePromiseOf<>(readinessProbe);
    private final LivenessProbe livenessProbe = Mockito.mock(LivenessProbe.class);
    private final SettablePromiseOf<LivenessProbe> livenessProbePromise = new SettablePromiseOf<>(livenessProbe);

    private final HttpServerHandler privateApiHandler = new HttpServerHandler(
        List.of(
            new LivenessHandler(valueOf($PrivateHttpServerConfig_ConfigValueExtractor.DEFAULTS), All.of(livenessProbePromise)),
            new ReadinessHandler(valueOf($PrivateHttpServerConfig_ConfigValueExtractor.DEFAULTS), All.of(readinessProbePromise)),
            new MetricsHandler(valueOf($PrivateHttpServerConfig_ConfigValueExtractor.DEFAULTS), valueOf(Optional.of(registry)))
        ),
        List.of(),
        $PrivateHttpServerConfig_ConfigValueExtractor.DEFAULTS
    );

    private volatile HttpServer httpServer = null;
    private volatile HttpServer privateHttpServer = null;

    protected final OkHttpClient client = new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(0, 1, TimeUnit.MICROSECONDS))
        .build();
    private final HttpServerObservation observation = Mockito.mock(HttpServerObservation.class);
    private final HttpServerTelemetry telemetry = Mockito.mock(HttpServerTelemetry.class, AdditionalAnswers.answer((_, _) -> observation));

    protected HttpServerTestKit() {
        this.reset();
    }

    private void reset() {
        Mockito.reset(telemetry, observation);
        when(telemetry.observe(any(), any())).thenReturn(observation);
        when(observation.span()).thenReturn(Span.getInvalid());
        when(observation.observeRequest(any())).thenAnswer(AdditionalAnswers.returnsArgAt(0));
        when(observation.observeResponse(any())).thenAnswer(AdditionalAnswers.returnsArgAt(0));
    }

    protected abstract HttpServer httpServer(ValueOf<? extends HttpServerConfig> config, HttpServerHandler httpServerHandler, HttpServerTelemetry telemetry);

    @Nested
    public class PrivateApiTest {
        @Test
        void testLivenessSuccess() throws Exception {
            when(livenessProbe.probe()).thenReturn(null);
            startPrivateHttpServer();

            var request = privateApiRequest($PrivateHttpServerConfig_ConfigValueExtractor.DEFAULTS.livenessPath())
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("OK");
            }
        }


        @Test
        void testLivenessFailure() throws Exception {
            when(livenessProbe.probe()).thenReturn(new LivenessProbeFailure("Failure"));
            startPrivateHttpServer();

            var request = privateApiRequest($PrivateHttpServerConfig_ConfigValueExtractor.DEFAULTS.livenessPath())
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(503);
                assertThat(response.body().string()).isEqualTo("Failure");
            }
        }

        @Test
        void testLivenessFailureOnUninitializedProbe() throws IOException {
            livenessProbePromise.setValue(null);
            startPrivateHttpServer();

            var request = privateApiRequest($PrivateHttpServerConfig_ConfigValueExtractor.DEFAULTS.livenessPath())
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(503);
                assertThat(response.body().string()).isEqualTo("Probe is not ready yet");
            }
        }

        @Test
        void testReadinessSuccess() throws Exception {
            when(readinessProbe.probe()).thenReturn(null);
            startPrivateHttpServer();

            var request = privateApiRequest($PrivateHttpServerConfig_ConfigValueExtractor.DEFAULTS.readinessPath())
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("OK");
            }
        }

        @Test
        void testReadinessFailure() throws Exception {
            when(readinessProbe.probe()).thenReturn(new ReadinessProbeFailure("Failed"));
            startPrivateHttpServer();

            var request = privateApiRequest($PrivateHttpServerConfig_ConfigValueExtractor.DEFAULTS.readinessPath())
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(503);
                assertThat(response.body().string()).isEqualTo("Failed");
            }
        }

        @Test
        void testReadinessFailureOnUninitializedProbe() throws IOException {
            readinessProbePromise.setValue(null);
            startPrivateHttpServer();

            var request = privateApiRequest($PrivateHttpServerConfig_ConfigValueExtractor.DEFAULTS.readinessPath())
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(503);
                assertThat(response.body().string()).isEqualTo("Probe is not ready yet");
            }
        }
    }


    @Nested
    public class PublicApiTest {
        @Test
        void testException() throws IOException {
            var handler = handler(GET, "/", (request) -> {
                throw new RuntimeException();
            });

            startServer(handler);

            var request = request("/")
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(500);
            }
            verifyResponse("GET", "/", 500, () -> ArgumentMatchers.isA(RuntimeException.class));
        }

        @Test
        void testExceptionIsResponse() throws IOException {
            var handler = handler(GET, "/", (request) -> {
                throw HttpServerResponseException.of(400, "Bad Request");
            });

            startServer(handler);

            var request = request("/")
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(400);
            }
            verifyResponse("GET", "/", 400, () -> ArgumentMatchers.isA(HttpServerResponseException.class));
        }

        @Test
        void testExceptionIsResponseNoBody() throws IOException {
            var handler = handler(GET, "/", (request) -> {
                throw new HttpServerResponseExceptionNoBody(400);
            });

            startServer(handler);

            var request = request("/")
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(400);
            }
            verifyResponse("GET", "/", 400, () -> ArgumentMatchers.isA(HttpServerResponseExceptionNoBody.class));
        }

        @Test
        void testExceptionIsFutureOfResponse() throws IOException {
            var handler = handler(GET, "/", (request) -> {
                Thread.sleep(100);
                throw HttpServerResponseException.of(400, "Bad Request");
            });

            startServer(handler);

            var request = request("/")
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(400);
            }
            verifyResponse("GET", "/", 400, () -> ArgumentMatchers.isA(HttpServerResponseException.class));
        }

        @Test
        void testCompletedFullResponseBody() throws IOException {
            var httpResponse = HttpServerResponse.of(200, HttpBody.plaintext("hello world"));
            var handler = handler(GET, "/", (request) -> {
                return httpResponse;
            });

            startServer(handler);

            var request = request("/")
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("hello world");
            }
            verifyResponse("GET", "/", 200, null);
        }

        @Test
        void testStreamingResponseBody() throws IOException {
            var handler = handler(GET, "/", (request) -> {
                var body = new HttpBodyOutput() {

                    @Override
                    public void close() throws IOException {

                    }

                    @Override
                    public long contentLength() {
                        return -1;
                    }

                    @Nullable
                    @Override
                    public String contentType() {
                        return null;
                    }

                    @Override
                    public void write(OutputStream os) throws IOException {
                        for (int i = 0; i < 10; i++) {
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                throw new IOException(e);
                            }
                            os.write("hello world".getBytes(StandardCharsets.UTF_8));
                        }

                    }
                };
                var httpResponse = HttpServerResponse.of(200, body);
                Thread.sleep(10);
                return httpResponse;
            });

            startServer(handler);

            var request = request("/")
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("hello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello world");
            }
            verifyResponse("GET", "/", 200, null);
        }

        @Test
        void testHeadRequest() throws Exception {
            startServer(handler("HEAD", "/test", (request) -> HttpServerResponse.of(200, HttpHeaders.of(), new HttpBodyOutput() {
                @Override
                public long contentLength() {
                    return 100;
                }

                @Override
                public String contentType() {
                    return null;
                }

                @Override
                public void write(OutputStream os) throws IOException {

                }

                @Override
                public void close() throws IOException {

                }
            })));

            var request = request("/test")
                .head()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().bytes()).isEmpty();
                assertThat(response.header("content-length")).isEqualTo("100");
            }
        }

        //todo request body tests
    }

    @Test
    void testHelloWorld() throws IOException, InterruptedException {
        var httpResponse = HttpServerResponse.of(200, HttpBody.plaintext("hello world"));
        var handler = handler(GET, "/", (_) -> {
            Thread.sleep(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500)));
            return httpResponse;
        });

        this.startServer(handler);

        var request = request("/")
            .get()
            .build();

        var start = now();
        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).isEqualTo("hello world");
        }
        Thread.sleep(1000);
        verifyResponse("GET", "/", 200, null);
    }

    @Test
    void serverWithBigResponse() throws IOException {
        var data = new byte[10 * 1024 * 1024];
        ThreadLocalRandom.current().nextBytes(data);
        var httpResponse = new SimpleHttpServerResponse(200, HttpHeaders.of(), HttpBodyOutput.of("text/plain", 10 * 1024 * 1024, os -> os.write(data)));
        var handler = handler(GET, "/", (_) -> {
            Thread.sleep(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500)));
            return httpResponse;
        });

        this.startServer(handler);

        var request = request("/")
            .get()
            .build();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().bytes()).isEqualTo(data);
        }
        verifyResponse("GET", "/", 200, null);
    }

    @Test
    void serverWithBigRequest() throws IOException {
        var data = new byte[10 * 1024 * 1024];
        ThreadLocalRandom.current().nextBytes(data);
        var httpResponse = HttpServerResponse.of(200);
        var handler = handler(POST, "/", (request) -> {
            try (var body = request.body(); var is = body.asInputStream()) {
                var b = is.readAllBytes();
                assertThat(b).isEqualTo(data);
                return httpResponse;
            }
        });

        this.startServer(handler);

        var request = request("/")
            .post(RequestBody.create(data))
            .post(new RequestBody() {
                @Override
                public okhttp3.@Nullable MediaType contentType() {
                    return null;
                }

                @Override
                public long contentLength() throws IOException {
                    return data.length;
                }

                @Override
                public void writeTo(@NotNull BufferedSink bufferedSink) throws IOException {
                    bufferedSink.flush();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                    bufferedSink.write(data);
                }
            })
            .build();

        try (var response = client.newBuilder().readTimeout(10, TimeUnit.SECONDS).build().newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }
        verifyResponse("POST", "/", 200, null);
    }

    @Test
    void testStreamResult() throws IOException {
        var dataList = new ArrayList<byte[]>(100);
        var data = new byte[102400];
        for (int i = 0; i < 100; i++) {
            var bytes = new byte[1024];
            ThreadLocalRandom.current().nextBytes(bytes);
            dataList.add(bytes);
            System.arraycopy(bytes, 0, data, i * 1024, 1024);
        }

        var httpResponse = new SimpleHttpServerResponse(200, HttpHeaders.of(), HttpBodyOutput.of("text/plain", 102400, os -> {
            for (var bytes : dataList) {
                os.write(bytes);
            }
        }));
        var handler = handler(GET, "/", (_) -> {
            Thread.sleep(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500)));
            return httpResponse;
        });

        this.startServer(handler);

        var request = request("/")
            .get()
            .build();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().bytes()).isEqualTo(data);
        }
        verifyResponse("GET", "/", 200, null);
    }

    @Test
    void testHelloWorldParallel() throws ExecutionException, InterruptedException {
        var httpResponse = HttpServerResponse.of(200, HttpBody.plaintext("hello world"));
        var handler = handler(GET, "/", (_) -> {
            Thread.sleep(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500)));
            return httpResponse;
        });

        this.startServer(handler);
        var request = request("/")
            .get()
            .build();


        record CodeAndBody(int code, String body) {}
        var futures = new ArrayList<CompletableFuture<CodeAndBody>>();
        for (int i = 0; i < 100; i++) {
            var future = new CompletableFuture<CodeAndBody>();
            ForkJoinPool.commonPool().submit(() -> {
                try (var response = client.newCall(request).execute()) {
                    future.complete(new CodeAndBody(response.code(), response.body().string()));
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            });
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        for (var future : futures) {
            assertThat(future.get().code()).isEqualTo(200);
            assertThat(future.get().body()).isEqualTo("hello world");
        }
        verifyResponse("GET", "/", 200, null, timeout(100).times(100));
    }

    @Test
    void testUnknownPath() throws IOException {
        var httpResponse = HttpServerResponse.of(200, HttpBody.plaintext("hello world"));
        var handler = handler(GET, "/", (_) -> {
            Thread.sleep(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500)));
            return httpResponse;
        });
        this.startServer(handler);

        var request = request("/test")
            .get()
            .build();
        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(404);
        }
        verify(this.observation).observeRequest(ArgumentMatchers.argThat(rq -> rq.route() == null));
    }

    @Test
    void testTimeoutAndBrokenPipe() {
        var bytes = "hello world".repeat(1024).getBytes(StandardCharsets.UTF_8);
        var httpResponse = httpResponse(200, -1, "text/plain", os -> {
            for (int i = 0; i < 1024; i++) {
                os.write(bytes);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            }
        });
        var handler = handler(GET, "/", (_) -> {
            Thread.sleep(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 200)));
            return httpResponse;
        });
        this.startServer(handler);

        var request = request("/")
            .get()
            .build();
        var newClient = client.newBuilder().callTimeout(100, TimeUnit.MILLISECONDS).build();
        var start = now();

        assertThatThrownBy(() -> {
            try (var response = newClient.newCall(request).execute()) {
                fail();
                assertThat(response.code()).isEqualTo(200);
            }
        })
            .isInstanceOf(IOException.class);
        var duration = Duration.between(start, now()).toNanos();
        verifyResponse("GET", "/", 200, ArgumentMatchers::notNull, timeout(10000));
    }

    @Test
    void testExceptionOnResponse() throws IOException {
        var handler = handler(GET, "/", (_) -> {
            Thread.sleep(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500)));
            throw new RuntimeException("test");
        });
        this.startServer(handler);

        var request = request("/")
            .get()
            .build();
        var start = now();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(500);
            assertThat(response.body().string()).isEqualTo("test");
        }
        verifyResponse("GET", "/", 500, any(RuntimeException.class));
    }

    @Test
    void testExceptionOnResponseBody() {
        var bytes = ByteBuffer.wrap("hello world".getBytes(StandardCharsets.UTF_8));
        var body = new HttpBodyOutput() {
            @Override
            public long contentLength() {
                return bytes.remaining() * 50L;
            }

            @Override
            public String contentType() {
                return "text/plain";
            }

            @Override
            public void write(OutputStream os) throws IOException {
                os.write("hello world".getBytes(StandardCharsets.UTF_8));
                os.write("hello world".getBytes(StandardCharsets.UTF_8));
                os.write("hello world".getBytes(StandardCharsets.UTF_8));
                os.write("hello world".getBytes(StandardCharsets.UTF_8));
                os.flush();
                throw new RuntimeException("test");
            }

            @Override
            public void close() throws IOException {

            }
        };
        var httpResponse = HttpServerResponse.of(200, body);
        var handler = handler(GET, "/", (_) -> {
            return httpResponse;
        });
        this.startServer(handler);

        var request = request("/")
            .get()
            .build();
        var start = now();

        assertThatThrownBy(() -> {
            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isNotNull();
                fail();
            }
        })
            .isInstanceOf(IOException.class);
        verifyResponse("GET", "/", 200, any(RuntimeException.class));
    }

    @Test
    void testExceptionOnFirstResponseBodyPart() throws IOException {
        var bytes = ByteBuffer.wrap("hello world".getBytes(StandardCharsets.UTF_8));
        var httpResponse = httpResponse(200, bytes.remaining() * 5, "text/plain", os -> {
            throw new RuntimeException("test");
        });
        var handler = handler(GET, "/", (_) -> httpResponse);
        this.startServer(handler);

        var request = request("/")
            .get()
            .build();
        var start = now();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(500);
            assertThat(response.body().string()).isEqualTo("test");
        }
        verifyResponse("GET", "/", 500, any(RuntimeException.class));
    }

    @Test
    void testHttpResponseExceptionOnHandle() throws IOException {
        var handler = handler(GET, "/", (_) -> {
            throw HttpServerResponseException.of(400, "test");
        });
        this.startServer(handler);

        var start = now();
        var request = request("/")
            .get()
            .build();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(400);
            assertThat(response.body().string()).isEqualTo("test");
        }
        verifyResponse("GET", "/", 400, any(HttpServerResponseException.class));
    }

    @Test
    void testErrorWithEmptyMessage() throws IOException {
        var handler = handler(GET, "/", (_) -> {
            throw new RuntimeException();
        });
        this.startServer(handler);

        var request = request("/")
            .get()
            .build();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(500);
            assertThat(response.body().string()).isEqualTo("Unknown error");
        }
    }

    @Test
    void testEmptyBodyHandling() throws IOException {
        var handler = handler(POST, "/", (request) -> {
            try (var body = request.body(); var is = body.asInputStream()) {
                is.readAllBytes();
                return new SimpleHttpServerResponse(
                    200,
                    HttpHeaders.of(),
                    null
                );
            }
        });
        this.startServer(handler);

        var request = request("/")
            .post(RequestBody.create(new byte[0]))
            .build();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }
    }

    @Test
    void testRequestBody() throws IOException {
        var httpResponse = HttpServerResponse.of(200, HttpBody.plaintext("hello world"));
        var executor = Executors.newSingleThreadExecutor();
        var size = 20 * 1024 * 1024;
        var handler = handler(POST, "/", (request) -> {
            try (var is = request.body().asInputStream()) {
                var data = is.readAllBytes();
                Assertions.assertEquals(data.length, size);
                return httpResponse;
            } catch (Throwable e) {
                e.printStackTrace();
                throw e;
            }
        });

        this.startServer(handler);
        var body = new byte[size];
        ThreadLocalRandom.current().nextBytes(body);

        var request = request("/")
            .post(RequestBody.create(body))
            .build();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).isEqualTo("hello world");
        } finally {
            executor.shutdown();
        }

    }

    @Test
    void testSyncByteArrayRequestMapper() throws IOException {
        var module = new HttpServerRequestMapperModule() {};
        var mapper = module.byteArrayRequestMapper();

        testByteArrayMapper(mapper);
    }

    private void testByteArrayMapper(HttpServerRequestMapper<byte[]> mapper) throws IOException {
        var httpResponse = HttpServerResponse.of(200, HttpBody.plaintext("hello world"));
        var executor = Executors.newSingleThreadExecutor();
        var size = 2 * 1024 * 1024;
        var body = new ConcurrentLinkedDeque<byte[]>();
        for (int i = 0; i < 5; i++) {
            var buf = new byte[size];
            ThreadLocalRandom.current().nextBytes(buf);
            body.add(buf);
        }
        var handler = handler(POST, "/", (request) -> {
            try {
                var data = mapper.apply(request);
                var expectedData = body.pollFirst();
                Assertions.assertArrayEquals(data, expectedData);
                return httpResponse;
            } catch (Throwable e) {
                e.printStackTrace();
                throw e;
            }
        });

        this.startServer(handler);
        try {
            byte[] buf;
            while ((buf = body.peek()) != null) {
                var request = request("/")
                    .post(RequestBody.create(buf))
                    .build();

                try (var response = client.newCall(request).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string()).isEqualTo("hello world");
                }
            }
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testInterceptor() throws IOException {
        var httpResponse = HttpServerResponse.of(200, HttpBody.plaintext("hello world"));
        var handler = handler(GET, "/", (_) -> {
            Thread.sleep(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500)));
            return httpResponse;
        });
        var interceptor1 = new HttpServerInterceptor() {
            @Override
            public HttpServerResponse intercept(HttpServerRequest request, InterceptChain chain) throws Exception {
                var header = request.headers().getFirst("test-header1");
                if (header != null) {
                    request.body().close();
                    return HttpServerResponse.of(500, HttpBody.plaintext("error"));
                }
                return chain.process(request);
            }
        };
        var interceptor2 = new HttpServerInterceptor() {
            @Override
            public HttpServerResponse intercept(HttpServerRequest request, InterceptChain chain) throws Exception {
                var header = request.headers().getFirst("test-header2");
                if (header != null) {
                    request.body().close();
                    return HttpServerResponse.of(400, HttpBody.plaintext("error"));
                }
                return chain.process(request);
            }
        };

        this.startServer(List.of(interceptor1, interceptor2), handler);

        var request = request("/")
            .get()
            .build();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).isEqualTo("hello world");
        }
        verifyResponse("GET", "/", 200, null);
        reset();

        try (var response = client.newCall(request.newBuilder()
            .header("test-header1", "somevalue")
            .header("test-header2", "somevalue")
            .build()).execute()) {
            assertThat(response.code()).isEqualTo(400);
            assertThat(response.body().string()).isEqualTo("error");
        }
        verifyResponse("GET", "/", 400, null);
        reset();
        try (var response = client.newCall(request.newBuilder()
            .header("test-header1", "somevalue")
            .build()).execute()) {
            assertThat(response.code()).isEqualTo(500);
            assertThat(response.body().string()).isEqualTo("error");
        }
        verifyResponse("GET", "/", 500, null);
    }

    private <T> Supplier<T> any(Class<T> t) {
        return () -> Mockito.any(t);
    }

    private <T> T any() {
        return Mockito.any();
    }

    private void verifyResponse(String method, String route, int code, @Nullable Supplier<? extends Throwable> throwable) {
        this.verifyResponse(method, route, code, throwable, timeout(100));
    }

    private void verifyResponse(String method, String route, int code, @Nullable Supplier<? extends Throwable> throwable, VerificationMode mode) {
        if (throwable != null) {
            verify(this.observation, mode).observeError(throwable.get());
        } else {
            verify(this.observation, never()).observeError(any());
        }
        verify(this.observation, mode).observeRequest(ArgumentMatchers.argThat(rq -> rq.route().equals(route)
            && rq.method().equals(method)));
        verify(this.observation, mode).end();
        verify(this.observation, mode).observeResponse(ArgumentMatchers.argThat(rs -> rs.code() == code));
    }


    private static HttpServerResponse httpResponse(int code, int contentLength, String contentType, HttpBodyOutput.HttpBodyWriter body) {
        return new HttpServerResponse() {
            @Override
            public int code() {
                return code;
            }

            @Override
            public MutableHttpHeaders headers() {
                return HttpHeaders.of();
            }

            @Override
            public HttpBodyOutput body() {
                return HttpBodyOutput.of(contentType, contentLength, body);
            }
        };
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

    protected void startServer(HttpServerRequestHandler... handlers) {
        this.startServer(List.of(), handlers);
    }

    protected void startServer(List<HttpServerInterceptor> interceptors, HttpServerRequestHandler... handlers) {
        startServer(false, interceptors, handlers);
    }

    protected void startServer(boolean ignoreTrailingSlash, List<HttpServerInterceptor> interceptors, HttpServerRequestHandler... handlers) {
        var config = new HttpServerConfig_Impl(
            0,
            ignoreTrailingSlash,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            false,
            Duration.ofMillis(1),
            new $HttpServerTelemetryConfig_ConfigValueExtractor.HttpServerTelemetryConfig_Impl(
                new $HttpServerTelemetryConfig_HttpServerLoggingConfig_ConfigValueExtractor.HttpServerLoggingConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerMetricsConfig_ConfigValueExtractor.HttpServerMetricsConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerTracingConfig_ConfigValueExtractor.HttpServerTracingConfig_Defaults()
            ),
            Size.of(1, Size.Type.GiB)
        );
        var publicApiHandler = new HttpServerHandler(List.of(handlers), interceptors, config);
        this.httpServer = this.httpServer(valueOf(config), publicApiHandler, this.telemetry);
        try {
            this.httpServer.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void startPrivateHttpServer() {
        this.privateHttpServer = this.httpServer(valueOf($PrivateHttpServerConfig_ConfigValueExtractor.DEFAULTS), privateApiHandler, NoopHttpServerTelemetry.INSTANCE);
        try {
            this.privateHttpServer.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (this.httpServer != null) {
            this.httpServer.release();
            this.httpServer = null;
        }
        if (this.privateHttpServer != null) {
            this.privateHttpServer.release();
            this.privateHttpServer = null;
        }
        this.readinessProbePromise.setValue(readinessProbe);
        this.livenessProbePromise.setValue(livenessProbe);
    }

    protected static <T> ValueOf<T> valueOf(T instance) {
        return new ValueOf<>() {
            @Override
            public T get() {
                return instance;
            }

            @Override
            public void refresh() {
            }
        };
    }

    protected Request.Builder privateApiRequest(String path) {
        return request(this.privateHttpServer.port(), path);
    }

    protected Request.Builder request(String path) {
        return request(this.httpServer.port(), path);
    }

    protected Request.Builder request(int port, String path) {
        return new Request.Builder()
            .url("http://localhost:%d%s".formatted(port, path));
    }


    private static class SettablePromiseOf<T> implements PromiseOf<T> {
        private T value;

        private SettablePromiseOf(T value) {
            this.value = value;
        }

        public void setValue(T value) {
            this.value = value;
        }

        @Override
        public Optional<T> get() {
            return Optional.ofNullable(value);
        }
    }

    private static class HttpServerResponseExceptionNoBody extends RuntimeException implements HttpServerResponse {
        private final int code;

        private HttpServerResponseExceptionNoBody(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return this.code;
        }

        @Override
        public MutableHttpHeaders headers() {
            return HttpHeaders.of();
        }

        @Nullable
        @Override
        public HttpBodyOutput body() {
            return null;
        }
    }
}
