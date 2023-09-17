package ru.tinkoff.kora.http.server.common;

import jakarta.annotation.Nullable;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.liveness.LivenessProbe;
import ru.tinkoff.kora.common.liveness.LivenessProbeFailure;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.common.util.ByteBufferPublisherInputStream;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpOutBody;
import ru.tinkoff.kora.http.server.common.$HttpServerConfig_ConfigValueExtractor.HttpServerConfig_Impl;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.DefaultHttpServerTelemetry;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerLogger;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerMetrics;
import ru.tinkoff.kora.http.server.common.telemetry.PrivateApiMetrics;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static ru.tinkoff.kora.http.common.HttpMethod.GET;
import static ru.tinkoff.kora.http.common.HttpMethod.POST;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public abstract class HttpServerTestKit {
    protected static PrivateApiMetrics registry = Mockito.mock(PrivateApiMetrics.class);
    private final ReadinessProbe readinessProbe = Mockito.mock(ReadinessProbe.class);
    private final SettablePromiseOf<ReadinessProbe> readinessProbePromise = new SettablePromiseOf<>(readinessProbe);
    private final LivenessProbe livenessProbe = Mockito.mock(LivenessProbe.class);
    private final SettablePromiseOf<LivenessProbe> livenessProbePromise = new SettablePromiseOf<>(livenessProbe);

    private static final ValueOf<HttpServerConfig> config = valueOf($HttpServerConfig_ConfigValueExtractor.DEFAULTS);

    private final PrivateApiHandler privateApiHandler = new PrivateApiHandler(config, valueOf(Optional.of(registry)), All.of(readinessProbePromise), All.of(livenessProbePromise));

    private volatile HttpServer httpServer = null;
    private volatile PrivateHttpServer privateHttpServer = null;
    OkHttpClient b = new OkHttpClient.Builder()
        .build();

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(0, 1, TimeUnit.MICROSECONDS))
        .build();

    protected HttpServerMetrics metrics = Mockito.mock(HttpServerMetrics.class);
    protected HttpServerLogger logger = Mockito.mock(HttpServerLogger.class);

    protected abstract HttpServer httpServer(ValueOf<HttpServerConfig> config, PublicApiHandler publicApiHandler);

    protected abstract PrivateHttpServer privateHttpServer(ValueOf<HttpServerConfig> config, PrivateApiHandler privateApiHandler);

    @Nested
    public class PrivateApiTest {
        @Test
        void testLivenessSuccess() throws IOException {
            when(livenessProbe.probe()).thenReturn(CompletableFuture.completedFuture(null));
            startPrivateHttpServer();

            var request = privateApiRequest(config.get().privateApiHttpLivenessPath())
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("OK");
            }
        }


        @Test
        void testLivenessFailure() throws IOException {
            when(livenessProbe.probe()).thenReturn(CompletableFuture.completedFuture(new LivenessProbeFailure("Failure")));
            startPrivateHttpServer();

            var request = privateApiRequest(config.get().privateApiHttpLivenessPath())
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

            var request = privateApiRequest(config.get().privateApiHttpLivenessPath())
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(503);
                assertThat(response.body().string()).isEqualTo("Probe is not ready yet");
            }
        }

        @Test
        void testReadinessSuccess() throws IOException {
            when(readinessProbe.probe()).thenReturn(CompletableFuture.completedFuture(null));
            startPrivateHttpServer();

            var request = privateApiRequest(config.get().privateApiHttpReadinessPath())
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("OK");
            }
        }

        @Test
        void testReadinessFailure() throws IOException {
            when(readinessProbe.probe()).thenReturn(CompletableFuture.completedFuture(new ReadinessProbeFailure("Failed")));
            startPrivateHttpServer();

            var request = privateApiRequest(config.get().privateApiHttpReadinessPath())
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

            var request = privateApiRequest(config.get().privateApiHttpReadinessPath())
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
        void testCompletedFullResponseBody() throws IOException {
            var httpResponse = HttpServerResponse.of(200, HttpBody.plaintext("hello world"));
            var handler = handler(GET, "/", (ctx, request) -> {
                return CompletableFuture.completedFuture(httpResponse);
            });

            startServer(handler);

            var request = request("/")
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("hello world");
            }
            verifyResponse("GET", "/", 200, HttpResultCode.SUCCESS, "localhost", "http", ArgumentMatchers::isNull, anyLong());
        }

        @Test
        void testAsyncFullResponseBody() throws IOException {
            var httpResponse = HttpServerResponse.of(200, HttpBody.plaintext("hello world"));
            var handler = handler(GET, "/", (ctx, request) -> {
                var f = new CompletableFuture<HttpServerResponse>();
                ForkJoinPool.commonPool().submit(() -> {
                    Thread.sleep(100);
                    return f.complete(httpResponse);
                });
                return f;
            });

            startServer(handler);

            var request = request("/")
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("hello world");
            }
            verifyResponse("GET", "/", 200, HttpResultCode.SUCCESS, "localhost", "http", ArgumentMatchers::isNull, anyLong());
        }

        @Test
        void testReactiveResponseBody() throws IOException {
            var handler = handler(GET, "/", (ctx, request) -> {
                var body = new HttpOutBody() {

                    @Override
                    public void close() throws IOException {

                    }

                    @Override
                    public int contentLength() {
                        return -1;
                    }

                    @Nullable
                    @Override
                    public String contentType() {
                        return null;
                    }

                    @Override
                    public void write(OutputStream os) throws IOException {
                        throw new IllegalStateException();
                    }

                    @Override
                    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
                        Flux.<ByteBuffer>create(fluxSink -> {
                            for (int i = 0; i < 10; i++) {
                                fluxSink.next(ByteBuffer.wrap("hello world".getBytes(StandardCharsets.UTF_8)));
                            }
                            fluxSink.complete();
                        }).subscribe(FlowAdapters.toSubscriber(subscriber));
                    }
                };
                var httpResponse = HttpServerResponse.of(200, body);
                return CompletableFuture.completedFuture(httpResponse);
            });

            startServer(handler);

            var request = request("/")
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("hello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello world");
            }
            verifyResponse("GET", "/", 200, HttpResultCode.SUCCESS, "localhost", "http", ArgumentMatchers::isNull, anyLong());
        }

        @Test
        void testStreamingResponseBody() throws IOException {
            var handler = handler(GET, "/", (ctx, request) -> {
                var body = new HttpOutBody() {

                    @Override
                    public void close() throws IOException {

                    }

                    @Override
                    public int contentLength() {
                        return -1;
                    }

                    @Nullable
                    @Override
                    public String contentType() {
                        return null;
                    }

                    @Override
                    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
                        throw new IllegalStateException();
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
                var f = new CompletableFuture<HttpServerResponse>();
                ForkJoinPool.commonPool().submit(() -> {
                    var httpResponse = HttpServerResponse.of(200, body);
                    Thread.sleep(10);
                    return f.complete(httpResponse);
                });
                return f;
            });

            startServer(handler);

            var request = request("/")
                .get()
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("hello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello world");
            }
            verifyResponse("GET", "/", 200, HttpResultCode.SUCCESS, "localhost", "http", ArgumentMatchers::isNull, anyLong());
        }

        //todo request body tests
    }

    @Test
    void testHelloWorld() throws IOException, InterruptedException {
        var httpResponse = HttpServerResponse.of(200, HttpBody.plaintext("hello world"));
        var handler = handler(GET, "/", request -> Mono.delay(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500))).thenReturn(httpResponse));

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
        verifyResponse("GET", "/", 200, HttpResultCode.SUCCESS, "localhost", "http", ArgumentMatchers::isNull, anyLong());
    }

    @Test
    void serverWithBigResponse() throws IOException {
        var data = new byte[10 * 1024 * 1024];
        ThreadLocalRandom.current().nextBytes(data);
        var httpResponse = new SimpleHttpServerResponse(200, HttpHeaders.of(), HttpOutBody.of("text/plain", 10 * 1024 * 1024,
            JdkFlowAdapter.publisherToFlowPublisher(Mono.just(ByteBuffer.wrap(data)).delayElement(Duration.ofMillis(100))))
        );
        var handler = handler(GET, "/", request -> Mono.delay(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500))).thenReturn(httpResponse));

        this.startServer(handler);

        var request = request("/")
            .get()
            .build();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().bytes()).isEqualTo(data);
        }
        verifyResponse("GET", "/", 200, HttpResultCode.SUCCESS, "localhost", "http", ArgumentMatchers::isNull, anyLong());
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

        var httpResponse = new SimpleHttpServerResponse(200, HttpHeaders.of(), HttpOutBody.of("text/plain", 102400, JdkFlowAdapter.publisherToFlowPublisher(Flux.fromIterable(dataList).map(ByteBuffer::wrap))));
        var handler = handler(GET, "/", request -> Mono.delay(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500))).thenReturn(httpResponse));

        this.startServer(handler);

        var request = request("/")
            .get()
            .build();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().bytes()).isEqualTo(data);
        }
        verifyResponse("GET", "/", 200, HttpResultCode.SUCCESS, "localhost", "http", ArgumentMatchers::isNull, anyLong());
    }

    @Test
    void testHelloWorldParallel() throws ExecutionException, InterruptedException {
        var httpResponse = HttpServerResponse.of(200, HttpBody.plaintext("hello world"));
        var handler = handler(GET, "/", request -> Mono.delay(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500))).thenReturn(httpResponse));

        this.startServer(handler);
        var request = request("/")
            .get()
            .build();


        var futures = new ArrayList<CompletableFuture<Tuple2<Integer, String>>>();
        for (int i = 0; i < 100; i++) {
            var future = new CompletableFuture<Tuple2<Integer, String>>();
            ForkJoinPool.commonPool().submit(() -> {
                try (var response = client.newCall(request).execute()) {
                    future.complete(Tuples.of(response.code(), response.body().string()));
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            });
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        for (var future : futures) {
            assertThat(future.get().getT1()).isEqualTo(200);
            assertThat(future.get().getT2()).isEqualTo("hello world");
        }
        verifyResponse("GET", "/", 200, HttpResultCode.SUCCESS, "localhost", "http", ArgumentMatchers::isNull, anyLong(), timeout(100).times(100));
    }

    @Test
    void testUnknownPath() throws IOException {
        var httpResponse = HttpServerResponse.of(200, HttpBody.plaintext("hello world"));
        var handler = handler(GET, "/", request -> Mono.delay(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500))).thenReturn(httpResponse));
        this.startServer(handler);

        var request = request("/test")
            .get()
            .build();
        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(404);
        }
        verify(logger, never()).logStart(any(), any());
        verify(logger, never()).logEnd(any(), any(), any(), anyLong().getAsLong(), any(), any());
        verify(metrics, times(1)).requestStarted(eq(GET), eq("UNKNOWN_ROUTE"), eq("localhost"), eq("http"));
        verify(metrics, timeout(100).times(1)).requestFinished(eq(GET), eq("UNKNOWN_ROUTE"), eq("localhost"), eq("http"), eq(404), Mockito.anyLong());
    }

    @Test
    void testTimeoutAndBrokenPipe() {
        var bytes = ByteBuffer.wrap("hello world".repeat(1024).getBytes(StandardCharsets.UTF_8));
        var body = Mono.fromCallable(bytes::slice).repeat(1024).delayElements(Duration.ofMillis(1)).publishOn(Schedulers.parallel());
        var httpResponse = httpResponse(200, -1, "text/plain", body);
        var handler = handler(GET, "/", request -> Mono.delay(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 200))).thenReturn(httpResponse));
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
        verifyResponse("GET", "/", 200, HttpResultCode.CONNECTION_ERROR, "localhost", "http", ArgumentMatchers::notNull, () -> longThat(argument -> argument >= duration), timeout(10000));
    }

    @Test
    void testExceptionOnResponse() throws IOException {
        var handler = handler(GET, "/", request -> Mono.delay(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500))).then(Mono.error(new RuntimeException("test"))));
        this.startServer(handler);

        var request = request("/")
            .get()
            .build();
        var start = now();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(500);
            assertThat(response.body().string()).isEqualTo("test");
        }
        verifyResponse("GET", "/", 500, HttpResultCode.SERVER_ERROR, "localhost", "http", any(RuntimeException.class), anyLong());
    }

    @Test
    void testExceptionOnResponseBody() {
        var bytes = ByteBuffer.wrap("hello world".getBytes(StandardCharsets.UTF_8));
        var body = Mono.fromCallable(bytes::slice).repeat(4).concatWith(Mono.error(() -> new RuntimeException("test"))).publishOn(Schedulers.parallel());
        var httpResponse = httpResponse(200, bytes.remaining() * 50, "text/plain", body);
        var handler = handler(GET, "/", request -> Mono.just(httpResponse));
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
        verifyResponse("GET", "/", 200, HttpResultCode.SERVER_ERROR, "localhost", "http", any(RuntimeException.class), anyLong());
    }

    @Test
    void testExceptionOnFirstResponseBodyPart() throws IOException {
        var bytes = ByteBuffer.wrap("hello world".getBytes(StandardCharsets.UTF_8));
        var body = Flux.<ByteBuffer>error(() -> new RuntimeException("test")).publishOn(Schedulers.parallel());
        var httpResponse = httpResponse(200, bytes.remaining() * 5, "text/plain", body);
        var handler = handler(GET, "/", request -> Mono.just(httpResponse));
        this.startServer(handler);

        var request = request("/")
            .get()
            .build();
        var start = now();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(500);
            assertThat(response.body().string()).isEqualTo("test");
        }
        verifyResponse("GET", "/", 500, HttpResultCode.SERVER_ERROR, "localhost", "http", any(RuntimeException.class), anyLong());
    }

    @Test
    void testHttpResponseExceptionOnHandle() throws IOException {
        var handler = handler(GET, "/", request -> {
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
        verifyResponse("GET", "/", 400, HttpResultCode.CLIENT_ERROR, "localhost", "http", any(HttpServerResponseException.class), anyLong());
    }

    @Test
    void testHttpResponseExceptionInResult() throws IOException {
        var handler = handler(GET, "/", request -> Mono.error(HttpServerResponseException.of(400, "test")));
        this.startServer(handler);

        var request = request("/")
            .get()
            .build();
        var start = now();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(400);
            assertThat(response.body().string()).isEqualTo("test");
        }
        verifyResponse("GET", "/", 400, HttpResultCode.CLIENT_ERROR, "localhost", "http", any(HttpServerResponseException.class), anyLong());
    }

    @Test
    void testErrorOnEmptyStreamResult() throws IOException {
        var handler = handler(GET, "/", request -> Mono.empty());
        this.startServer(handler);

        var request = request("/")
            .get()
            .build();
        var start = now();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(500);
        }
        verifyResponse("GET", "/", 500, HttpResultCode.SERVER_ERROR, "localhost", "http", any(Exception.class), anyLong());
    }

    @Test
    void testMonoErrorWithEmptyMessage() throws IOException {
        var handler = handler(GET, "/", request -> Mono.error(new Exception()));
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
    void testErrorWithEmptyMessage() throws IOException {
        var handler = handler(GET, "/", request -> {
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
        var handler = handler(POST, "/", request -> ReactorUtils.toByteArrayMono(JdkFlowAdapter.flowPublisherToFlux(request.body()))
            .thenReturn(new SimpleHttpServerResponse(
                200,
                HttpHeaders.of(),
                null
            )));
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
        var handler = handler(POST, "/", request -> Mono.create(sink -> {
            executor.submit(() -> {
                try (var is = new ByteBufferPublisherInputStream(request.body())) {
                    var data = is.readAllBytes();
                    org.junit.jupiter.api.Assertions.assertTrue(data.length == size);
                    sink.success(httpResponse);
                } catch (IOException e) {
                    sink.error(e);
                }
            });
        }));

        this.startServer(handler);
        var body = new byte[size];
        ThreadLocalRandom.current().nextBytes(body);

        var request = request("/")
            .post(RequestBody.create(body))
            .build();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).isEqualTo("hello world");
        }
        executor.shutdown();
    }

    @Test
    void testInterceptor() throws IOException {
        var httpResponse = HttpServerResponse.of(200, HttpBody.plaintext("hello world"));
        var handler = handler(GET, "/", request -> Mono.delay(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500))).thenReturn(httpResponse));
        var interceptor1 = new HttpServerInterceptor() {
            @Override
            public CompletionStage<HttpServerResponse> intercept(Context ctx, HttpServerRequest request, InterceptChain chain) throws Exception {
                var header = request.headers().getFirst("test-header1");
                if (header != null) {
                    request.body().close();
                    return CompletableFuture.completedFuture(HttpServerResponse.of(500, HttpBody.plaintext("error")));
                }
                return chain.process(ctx, request);
            }
        };
        var interceptor2 = new HttpServerInterceptor() {
            @Override
            public CompletionStage<HttpServerResponse> intercept(Context ctx, HttpServerRequest request, InterceptChain chain) throws Exception {
                var header = request.headers().getFirst("test-header2");
                if (header != null) {
                    request.body().subscribe(FlowUtils.drain());
                    return CompletableFuture.completedFuture(HttpServerResponse.of(400, HttpBody.plaintext("error")));
                }
                return chain.process(ctx, request);
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
        verifyResponse("GET", "/", 200, HttpResultCode.SUCCESS, "localhost", "http", ArgumentMatchers::isNull, anyLong());
        reset(logger, metrics);
        try (var response = client.newCall(request.newBuilder()
            .header("test-header1", "somevalue")
            .header("test-header2", "somevalue")
            .build()).execute()) {
            assertThat(response.code()).isEqualTo(400);
            assertThat(response.body().string()).isEqualTo("error");
        }
        verifyResponse("GET", "/", 400, HttpResultCode.CLIENT_ERROR, "localhost", "http", ArgumentMatchers::isNull, anyLong());
        reset(logger, metrics);
        try (var response = client.newCall(request.newBuilder()
            .header("test-header1", "somevalue")
            .build()).execute()) {
            assertThat(response.code()).isEqualTo(500);
            assertThat(response.body().string()).isEqualTo("error");
        }
        verifyResponse("GET", "/", 500, HttpResultCode.SERVER_ERROR, "localhost", "http", ArgumentMatchers::isNull, anyLong());
    }

    private <T> Supplier<T> any(Class<T> t) {
        return () -> Mockito.any(t);
    }

    private <T> LongSupplier anyLong() {
        return Mockito::anyLong;
    }

    private <T> T any() {
        return Mockito.any();
    }

    private <T extends Comparable<T>> Supplier<T> lt(T t) {
        return () -> AdditionalMatchers.lt(t);
    }

    private <T extends Comparable<T>> Supplier<T> gt(T t) {
        return () -> AdditionalMatchers.gt(t);
    }

    private void verifyResponse(String method, String route, int code, HttpResultCode resultCode, String host, String scheme, Supplier<? extends Throwable> throwable, LongSupplier duration) {
        this.verifyResponse(method, route, code, resultCode, host, scheme, throwable, duration, timeout(100));
    }

    private void verifyResponse(String method, String route, int code, HttpResultCode resultCode, String host, String scheme, Supplier<? extends Throwable> throwable, LongSupplier duration, VerificationMode mode) {
        verify(metrics, mode).requestStarted(eq(method), eq(route), eq(host), eq(scheme));
        verify(logger, mode).logStart(eq(method + " " + route), any());
        verify(logger, mode).logEnd(eq(method + " " + route), eq(code), eq(resultCode), duration.getAsLong(), any(), throwable.get());
        verify(metrics, mode).requestFinished(eq(method), eq(route), eq(host), eq(scheme), eq(code), Mockito.anyLong());
    }


    private static HttpServerResponse httpResponse(int code, int contentLength, String contentType, Publisher<? extends ByteBuffer> body) {
        return httpResponse(code, contentLength, contentType, FlowAdapters.toFlowPublisher(body));
    }

    private static HttpServerResponse httpResponse(int code, int contentLength, String contentType, Flow.Publisher<? extends ByteBuffer> body) {
        return new HttpServerResponse() {
            @Override
            public int code() {
                return code;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of();
            }

            @Override
            public HttpOutBody body() {
                return HttpOutBody.of(contentType, contentLength, body);
            }
        };
    }


    private static HttpServerRequestHandler handler(String method, String route, Function<HttpServerRequest, Mono<HttpServerResponse>> handler) {
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
            public CompletionStage<HttpServerResponse> handle(Context ctx, HttpServerRequest request) {
                return handler.apply(request).toFuture();
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
            public CompletionStage<HttpServerResponse> handle(Context ctx, HttpServerRequest request) throws Exception {
                return handler.apply(ctx, request);
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
        var config = new HttpServerConfig_Impl(0, 0, "/metrics", "/system/readiness", "/system/liveness", ignoreTrailingSlash, 1, 10, Duration.ofMillis(1));
        var publicApiHandler = new PublicApiHandler(List.of(handlers), interceptors, new DefaultHttpServerTelemetry(this.metrics, this.logger, null), config);
        this.httpServer = this.httpServer(valueOf(config), publicApiHandler);
        try {
            this.httpServer.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void startPrivateHttpServer() {
        this.privateHttpServer = this.privateHttpServer(config, privateApiHandler);
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

    Request.Builder privateApiRequest(String path) {
        return request(this.privateHttpServer.port(), path);
    }

    Request.Builder request(String path) {
        return request(this.httpServer.port(), path);
    }

    Request.Builder request(int port, String path) {
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
}
