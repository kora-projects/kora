package ru.tinkoff.kora.http.server.common;

import okhttp3.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.liveness.LivenessProbe;
import ru.tinkoff.kora.common.liveness.LivenessProbeFailure;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.common.util.ByteBufferFluxInputStream;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.DefaultHttpServerTelemetry;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerLogger;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerMetrics;
import ru.tinkoff.kora.http.server.common.telemetry.PrivateApiMetrics;

import javax.annotation.Nonnull;
import java.io.IOException;
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
import java.util.stream.Stream;

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
    private ReadinessProbe readinessProbe = Mockito.mock(ReadinessProbe.class);
    private SettablePromiseOf<ReadinessProbe> readinessProbePromise = new SettablePromiseOf<>(readinessProbe);
    private LivenessProbe livenessProbe = Mockito.mock(LivenessProbe.class);
    private SettablePromiseOf<LivenessProbe> livenessProbePromise = new SettablePromiseOf<>(livenessProbe);



    private static ValueOf<HttpServerConfig> config = valueOf(new HttpServerConfig(0, 0, HttpServerConfig.DEFAULT_PRIVATE_API_METRICS_PATH, HttpServerConfig.DEFAULT_PRIVATE_API_READINESS_PATH, HttpServerConfig.DEFAULT_PRIVATE_API_LIVENESS_PATH, 1, 10, 1));

    private final PrivateApiHandler privateApiHandler = new PrivateApiHandler(config, valueOf(Optional.of(registry)), All.of(readinessProbePromise), All.of(livenessProbePromise));

    private volatile HttpServer httpServer = null;
    private volatile PrivateHttpServer privateHttpServer = null;
    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(0, 1, TimeUnit.MICROSECONDS))
        .build();

    protected HttpServerMetrics metrics = Mockito.mock(HttpServerMetrics.class);
    protected HttpServerLogger logger = Mockito.mock(HttpServerLogger.class);

    protected abstract HttpServer httpServer(ValueOf<HttpServerConfig> config, PublicApiHandler publicApiHandler);

    protected abstract PrivateHttpServer privateHttpServer(ValueOf<HttpServerConfig> config, PrivateApiHandler privateApiHandler);

    @Test
    void testLivenessSuccess() throws IOException {
        when(livenessProbe.probe()).thenReturn(Mono.empty());
        this.startPrivateHttpServer();

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
        when(livenessProbe.probe()).thenReturn(Mono.just(new LivenessProbeFailure("Failure")));
        this.startPrivateHttpServer();

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
        this.livenessProbePromise.setValue(null);
        this.startPrivateHttpServer();

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
        when(readinessProbe.probe()).thenReturn(Mono.empty());
        this.startPrivateHttpServer();

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
        when(readinessProbe.probe()).thenReturn(Mono.just(new ReadinessProbeFailure("Failed")));
        this.startPrivateHttpServer();

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
        this.readinessProbePromise.setValue(null);
        this.startPrivateHttpServer();

        var request = privateApiRequest(config.get().privateApiHttpReadinessPath())
            .get()
            .build();

        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(503);
            assertThat(response.body().string()).isEqualTo("Probe is not ready yet");
        }
    }

    @Test
    void testHelloWorld() throws IOException {
        var httpResponse = new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), ByteBuffer.wrap("hello world".getBytes(StandardCharsets.UTF_8)));
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
        verifyResponse("GET", "/", 200, HttpResultCode.SUCCESS, "localhost", "http", ArgumentMatchers::isNull, anyLong());
    }

    @Test
    void serverWithBigResponse() throws IOException {
        var data = new byte[10 * 1024 * 1024];
        ThreadLocalRandom.current().nextBytes(data);
        var httpResponse = new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), 10 * 1024 * 1024, Mono.fromCallable(() -> ByteBuffer.wrap(data))
            .delayElement(Duration.ofMillis(100))
            .flux());
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

        var httpResponse = new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), 102400, Flux.fromIterable(dataList).map(ByteBuffer::wrap));
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
        var httpResponse = new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), ByteBuffer.wrap("hello world".getBytes(StandardCharsets.UTF_8)));
        var handler = handler(GET, "/", request -> Mono.delay(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500))).thenReturn(httpResponse));

        this.startServer(handler);
        var request = request("/")
            .get()
            .build();
        var start = now();


        var futures = new ArrayList<CompletableFuture<Tuple2<Integer, String>>>();
        for (int i = 0; i < 100; i++) {
            var future = new CompletableFuture<Tuple2<Integer, String>>();
            var dispatcher = new Dispatcher();
            dispatcher.setMaxRequests(100);
            dispatcher.setMaxRequestsPerHost(100);
            client.newBuilder()
                .dispatcher(dispatcher)
                .build()
                .newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@Nonnull Call call, @Nonnull IOException e) {
                        future.completeExceptionally(e);
                    }

                    @Override
                    public void onResponse(@Nonnull Call call, @Nonnull Response response) throws IOException {
                        future.complete(Tuples.of(response.code(), response.body().string()));
                    }
                });
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        for (var future : futures) {
            assertThat(future.get().getT1()).isEqualTo(200);
            assertThat(future.get().getT2()).isEqualTo("hello world");
        }
        verifyResponse("GET", "/", 200, HttpResultCode.SUCCESS, "localhost", "http", ArgumentMatchers::isNull, anyLong(), timeout(100).times(100));
    }

    @Test
    void testUnknownPath() throws IOException {
        var httpResponse = new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), ByteBuffer.wrap("hello world".getBytes(StandardCharsets.UTF_8)));
        var handler = handler(GET, "/", request -> Mono.delay(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500))).thenReturn(httpResponse));
        this.startServer(handler);

        var request = request("/test")
            .get()
            .build();
        try (var response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(404);
        }
        verify(logger, never()).logStart(any());
        verify(logger, never()).logEnd(any(), any(), any(), anyLong().getAsLong(), any());
        verify(metrics, times(1)).requestStarted(eq(GET), eq("UNKNOWN_ROUTE"), eq("localhost"), eq("http"));
        verify(metrics, timeout(100).times(1)).requestFinished(eq(GET), eq("UNKNOWN_ROUTE"), eq("localhost"), eq("http"), eq(404), Mockito.anyLong());
    }

    @Test
    void testTimeoutAndBrokenPipe() {
        var bytes = ByteBuffer.wrap("hello world".getBytes(StandardCharsets.UTF_8));
        var body = Mono.fromCallable(bytes::slice).repeat(5).publishOn(Schedulers.parallel());
        var httpResponse = httpResponse(200, bytes.remaining() * 5, "text/plain", body);
        var handler = handler(GET, "/", request -> Mono.delay(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500))).thenReturn(httpResponse));
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
        verifyResponse("GET", "/", 200, HttpResultCode.CONNECTION_ERROR, "localhost", "http", ArgumentMatchers::notNull, () -> longThat(argument -> argument >= duration), timeout(1000));
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
        var handler = handler(POST, "/", request -> ReactorUtils.toByteArrayMono(request.body())
            .thenReturn(new SimpleHttpServerResponse(
                200,
                "application/octet-stream",
                HttpHeaders.of(),
                0,
                Flux.empty()
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
        var httpResponse = new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), ByteBuffer.wrap("hello world".getBytes(StandardCharsets.UTF_8)));
        var executor = Executors.newSingleThreadExecutor();
        var size = 20 * 1024 * 1024;
        var handler = handler(POST, "/", request -> Mono.create(sink -> {
            executor.submit(() -> {
                try (var is = new ByteBufferFluxInputStream(request.body())) {
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
        var httpResponse = new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), ByteBuffer.wrap("hello world".getBytes(StandardCharsets.UTF_8)));
        var handler = handler(GET, "/", request -> Mono.delay(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 500))).thenReturn(httpResponse));
        var interceptor1 = new HttpServerInterceptor() {
            @Override
            public Mono<HttpServerResponse> intercept(HttpServerRequest request, Function<HttpServerRequest, Mono<HttpServerResponse>> chain) {
                var header = request.headers().getFirst("test-header1");
                if (header != null) {
                    request.body().subscribe();
                    return Mono.just(new SimpleHttpServerResponse(500, "text/plain", HttpHeaders.of(), ByteBuffer.wrap("error".getBytes(StandardCharsets.UTF_8))));
                }
                return chain.apply(request);
            }
        };
        var interceptor2 = new HttpServerInterceptor() {
            @Override
            public Mono<HttpServerResponse> intercept(HttpServerRequest request, Function<HttpServerRequest, Mono<HttpServerResponse>> chain) {
                var header = request.headers().getFirst("test-header2");
                if (header != null) {
                    request.body().subscribe();
                    return Mono.just(new SimpleHttpServerResponse(400, "text/plain", HttpHeaders.of(), ByteBuffer.wrap("error".getBytes(StandardCharsets.UTF_8))));
                }
                return chain.apply(request);
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
        verify(logger, mode).logStart(method + " " + route);
        verify(logger, mode).logEnd(eq(method + " " + route), eq(code), eq(resultCode), duration.getAsLong(), throwable.get());
        verify(metrics, mode).requestFinished(eq(method), eq(route), eq(host), eq(scheme), eq(code), Mockito.anyLong());
    }


    private static HttpServerResponse httpResponse(int code, int contentLength, String contentType, Flux<? extends ByteBuffer> body) {
        return new HttpServerResponse() {
            @Override
            public int code() {
                return code;
            }

            @Override
            public int contentLength() {
                return contentLength;
            }

            @Override
            public String contentType() {
                return contentType;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of();
            }

            @Override
            public Flux<? extends ByteBuffer> body() {
                return body;
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
            public Mono<HttpServerResponse> handle(HttpServerRequest request) {
                return handler.apply(request);
            }
        };
    }

    protected void startServer(HttpServerRequestHandler... handlers) {
        this.startServer(List.of(), handlers);
    }

    protected void startServer(List<HttpServerInterceptor> interceptors, HttpServerRequestHandler... handlers) {
        var interceptorsList = interceptors.stream().map(HttpServerTestKit::valueOf).toList();
        var handlerList = Stream.of(handlers)
            .map(HttpServerTestKit::valueOf)
            .toList();
        var publicApiHandler = new PublicApiHandler(All.of(handlerList), All.of(interceptorsList), valueOf(new DefaultHttpServerTelemetry(this.metrics, this.logger, null)));
        this.httpServer = this.httpServer(config, publicApiHandler);
        this.httpServer.init().block();
    }

    protected void startPrivateHttpServer() {
        this.privateHttpServer = this.privateHttpServer(config, privateApiHandler);
        this.privateHttpServer.init().block();
    }

    @AfterEach
    void tearDown() {
        if (this.httpServer != null) {
            this.httpServer.release().block();
            this.httpServer = null;
        }
        if (this.privateHttpServer != null) {
            this.privateHttpServer.release().block();
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
            public Mono<Void> refresh() {
                return Mono.empty();
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

        private SettablePromiseOf(T value) {this.value = value;}

        public void setValue(T value) {
            this.value = value;
        }

        @Override
        public Optional<T> get() {
            return Optional.ofNullable(value);
        }
    }
}
