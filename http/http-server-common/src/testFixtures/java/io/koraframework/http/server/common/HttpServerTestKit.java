package io.koraframework.http.server.common;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.PromiseOf;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.liveness.LivenessProbe;
import io.koraframework.common.liveness.LivenessProbeFailure;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.common.readiness.ReadinessProbeFailure;
import io.koraframework.common.util.Size;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.common.header.MutableHttpHeaders;
import io.koraframework.http.server.common.$HttpServerConfig_ConfigValueMapper.HttpServerConfig_Impl;
import io.koraframework.http.server.common.interceptor.HttpServerInterceptor;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.request.HttpServerRequestMapper;
import io.koraframework.http.server.common.request.mapper.HttpServerRequestMapperModule;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.response.HttpServerResponseException;
import io.koraframework.http.server.common.router.HttpServerRouter;
import io.koraframework.http.server.common.system.$SystemHttpServerConfig_ConfigValueMapper;
import io.koraframework.http.server.common.system.LivenessHandler;
import io.koraframework.http.server.common.system.MetricsHandler;
import io.koraframework.http.server.common.system.ReadinessHandler;
import io.koraframework.http.server.common.telemetry.*;
import io.koraframework.http.server.common.telemetry.impl.NoopHttpServerTelemetry;
import io.koraframework.telemetry.common.MetricsScraper;
import io.opentelemetry.api.trace.Span;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.koraframework.http.common.HttpMethod.GET;
import static io.koraframework.http.common.HttpMethod.POST;
import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public abstract class HttpServerTestKit {
    private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(1);

    protected static MetricsScraper registry = Mockito.mock(MetricsScraper.class);
    private final ReadinessProbe readinessProbe = Mockito.mock(ReadinessProbe.class);
    private final SettablePromiseOf<ReadinessProbe> readinessProbePromise = new SettablePromiseOf<>(readinessProbe);
    private final LivenessProbe livenessProbe = Mockito.mock(LivenessProbe.class);
    private final SettablePromiseOf<LivenessProbe> livenessProbePromise = new SettablePromiseOf<>(livenessProbe);

    private final HttpServerRouter privateApiHandler = new HttpServerRouter(
        List.of(
            new LivenessHandler($SystemHttpServerConfig_ConfigValueMapper.DEFAULTS, All.of(livenessProbePromise)),
            new ReadinessHandler($SystemHttpServerConfig_ConfigValueMapper.DEFAULTS, All.of(readinessProbePromise)),
            new MetricsHandler($SystemHttpServerConfig_ConfigValueMapper.DEFAULTS, valueOf(Optional.of(registry)))
        ),
        List.of(),
        $HttpServerConfig_ConfigValueMapper.DEFAULTS
    );

    private volatile HttpServer httpServer = null;
    private volatile HttpServer privateHttpServer = null;

    protected final OkHttpClient client = new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(0, 1, TimeUnit.MICROSECONDS))
        .build();
    private final HttpServerObservation observation = Mockito.mock(HttpServerObservation.class);
    private final HttpServerTelemetry telemetry = Mockito.mock(HttpServerTelemetry.class, AdditionalAnswers.answer((_) -> observation));

    protected HttpServerTestKit() {
        this.reset();
    }

    private void reset() {
        Mockito.reset(telemetry, observation);
        when(telemetry.observe(any())).thenReturn(observation);
        when(observation.span()).thenReturn(Span.getInvalid());
        when(observation.observeRequest(any())).thenAnswer(AdditionalAnswers.returnsArgAt(0));
        when(observation.observeResponse(any())).thenAnswer(AdditionalAnswers.returnsArgAt(0));
    }

    protected abstract HttpServer httpServer(ValueOf<? extends HttpServerConfig> config, HttpServerRouter httpServerRouter, HttpServerTelemetry telemetry);

    @Nested
    public class SystemApiTest {
        @Test
        void testLivenessSuccess() throws Exception {
            when(livenessProbe.probe()).thenReturn(null);
            startSystemHttpServer();

            var request = privateApiRequest($SystemHttpServerConfig_ConfigValueMapper.DEFAULTS.livenessPath())
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
            startSystemHttpServer();

            var request = privateApiRequest($SystemHttpServerConfig_ConfigValueMapper.DEFAULTS.livenessPath())
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
            startSystemHttpServer();

            var request = privateApiRequest($SystemHttpServerConfig_ConfigValueMapper.DEFAULTS.livenessPath())
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
            startSystemHttpServer();

            var request = privateApiRequest($SystemHttpServerConfig_ConfigValueMapper.DEFAULTS.readinessPath())
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
            startSystemHttpServer();

            var request = privateApiRequest($SystemHttpServerConfig_ConfigValueMapper.DEFAULTS.readinessPath())
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
            startSystemHttpServer();

            var request = privateApiRequest($SystemHttpServerConfig_ConfigValueMapper.DEFAULTS.readinessPath())
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

    /**
     * Server-agnostic response body contract: bytes are delivered intact whatever the size, declared length and write
     * pattern, the body is closed exactly once on every path, failures after the status line truncate the response,
     * and a slow or vanished client never makes the server buffer the whole response.
     */
    @Nested
    public class ResponseBodyTest {

        enum WritePattern {
            SINGLE_WRITE,
            RANDOM_SLICES,
            BYTE_BY_BYTE,
            WITH_EMPTY_WRITES
        }

        enum BufferKind {
            HEAP,
            HEAP_SLICE,
            READ_ONLY,
            DIRECT
        }

        enum Failure {
            THROW_BEFORE_FIRST_BYTE,
            THROW_AFTER_FIRST_BYTES,
            THROW_AFTER_LARGE_PART,
            SHORT_SMALL_BODY_OF_DECLARED_LENGTH,
            SHORT_LARGE_BODY_OF_DECLARED_LENGTH
        }

        static Stream<Arguments> streamingBodies() {
            // common buffer boundaries of HTTP servers: 8, 16 and 64 KiB
            var sizes = new int[]{0, 1, 8 * 1024 - 1, 8 * 1024 + 1, 16 * 1024, 64 * 1024 - 1, 64 * 1024, 64 * 1024 + 1, 200 * 1024 + 3, 1024 * 1024 + 7};
            var arguments = new ArrayList<Arguments>();
            for (var size : sizes) {
                for (var declaredLength : new boolean[]{true, false}) {
                    for (var pattern : WritePattern.values()) {
                        if (pattern == WritePattern.BYTE_BY_BYTE && size > 200 * 1024 + 3) {
                            continue;
                        }
                        arguments.add(Arguments.of(size, declaredLength, pattern));
                    }
                }
            }
            return arguments.stream();
        }

        static Stream<Arguments> fullBodies() {
            var arguments = new ArrayList<Arguments>();
            for (var size : new int[]{0, 1, 1023, 64 * 1024 + 1, 1024 * 1024 + 3}) {
                for (var kind : BufferKind.values()) {
                    arguments.add(Arguments.of(size, kind));
                }
            }
            return arguments.stream();
        }

        @ParameterizedTest(name = "{0} bytes, declared length: {1}, {2}")
        @MethodSource("streamingBodies")
        void streamingBodyIsDeliveredIntact(int size, boolean declaredLength, WritePattern pattern) throws Exception {
            var data = payload(size, size);
            var body = new TrackingBody(declaredLength ? size : -1, os -> write(os, data, pattern, size));
            startServer(handler(GET, "/body", _ -> HttpServerResponse.of(200, body)));

            try (var response = client.newCall(request("/body").get().build()).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().bytes()).isEqualTo(data);
                var contentLength = response.header("Content-Length");
                if (declaredLength) {
                    assertThat(contentLength).isEqualTo(Integer.toString(size));
                } else if (contentLength == null) {
                    assertThat(response.header("Transfer-Encoding")).isEqualToIgnoringCase("chunked");
                } else {
                    assertThat(contentLength).isEqualTo(Integer.toString(size));
                }
            }
            body.assertClosedOnce();
        }

        @ParameterizedTest(name = "{0} bytes, {1} buffer")
        @MethodSource("fullBodies")
        void fullContentBodyIsSentWithContentLength(int size, BufferKind kind) throws Exception {
            var data = payload(size, 31L * size + kind.ordinal());
            startServer(handler(GET, "/full", _ -> HttpServerResponse.of(200, HttpBody.octetStream(buffer(data, kind)))));

            // twice: the same body instance must not be consumed by the first response
            for (var i = 0; i < 2; i++) {
                try (var response = client.newCall(request("/full").get().build()).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.header("Content-Length")).isEqualTo(Integer.toString(size));
                    assertThat(response.body().bytes()).isEqualTo(data);
                }
            }
        }

        @ParameterizedTest(name = "declared length {0}")
        @ValueSource(longs = {-1, 0, 100, 64 * 1024 + 1, 10 * 1024 * 1024})
        void headResponseNeverProducesBody(long declaredLength) throws Exception {
            var body = new TrackingBody(declaredLength, os -> os.write(new byte[1024]));
            startServer(handler("HEAD", "/head", _ -> HttpServerResponse.of(200, body)));

            try (var response = client.newCall(request("/head").head().build()).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().bytes()).isEmpty();
                if (declaredLength >= 0) {
                    assertThat(response.header("Content-Length")).isEqualTo(Long.toString(declaredLength));
                }
            }
            body.assertClosedOnce();
            assertThat(body.writes).hasValue(0);
        }

        @ParameterizedTest
        @EnumSource(Failure.class)
        void bodyFailureIsReportedAndServerKeepsServing(Failure failure) throws Exception {
            var body = new TrackingBody(switch (failure) {
                case SHORT_SMALL_BODY_OF_DECLARED_LENGTH -> 1000;
                case SHORT_LARGE_BODY_OF_DECLARED_LENGTH -> 300 * 1024;
                default -> -1;
            }, os -> {
                switch (failure) {
                    case THROW_BEFORE_FIRST_BYTE -> throw new IllegalStateException("boom");
                    case THROW_AFTER_FIRST_BYTES -> {
                        os.write(new byte[10]);
                        os.flush();
                        throw new IllegalStateException("boom");
                    }
                    case THROW_AFTER_LARGE_PART -> {
                        os.write(new byte[100 * 1024]);
                        throw new IllegalStateException("boom");
                    }
                    case SHORT_SMALL_BODY_OF_DECLARED_LENGTH -> os.write(new byte[500]);
                    case SHORT_LARGE_BODY_OF_DECLARED_LENGTH -> os.write(new byte[200 * 1024]);
                }
            });
            startServer(
                handler(GET, "/failing", _ -> HttpServerResponse.of(200, body)),
                handler(GET, "/ok", _ -> HttpServerResponse.of(200, HttpBody.plaintext("ok")))
            );

            if (failure == Failure.THROW_BEFORE_FIRST_BYTE) {
                try (var response = client.newCall(request("/failing").get().build()).execute()) {
                    assertThat(response.code()).isEqualTo(500);
                    assertThat(response.body().string()).isEqualTo("boom");
                }
            } else {
                // once the body has started the client must see the response truncated, never complete
                assertThatThrownBy(() -> {
                    try (var response = client.newCall(request("/failing").get().build()).execute()) {
                        assertThat(response.code()).isEqualTo(200);
                        response.body().bytes();
                    }
                }).isInstanceOf(IOException.class);
            }
            body.assertClosedOnce();

            try (var response = client.newCall(request("/ok").get().build()).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("ok");
            }
        }

        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {1024, 1024 * 1024})
        void bodyCloseFailureDoesNotBreakDeliveredResponse(int size) throws Exception {
            var data = payload(size, 11);
            var closes = new AtomicInteger();
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
                    os.write(data);
                }

                @Override
                public void close() throws IOException {
                    closes.incrementAndGet();
                    throw new IOException("close failed");
                }
            };
            startServer(handler(GET, "/close-failure", _ -> HttpServerResponse.of(200, body)));

            for (var i = 0; i < 2; i++) {
                try (var response = client.newCall(request("/close-failure").get().build()).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().bytes()).isEqualTo(data);
                }
            }
            awaitValue(closes, 2);
        }

        @Test
        void keepAliveConnectionIsReusedAcrossAllResponseKinds() throws Exception {
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
                    assertRawBody(raw.exchange("GET", "/small"), 200, small);
                    assertRawBody(raw.exchange("GET", "/medium"), 200, medium);
                    assertRawBody(raw.exchange("GET", "/unknown-length"), 200, unknownLength);
                    assertRawBody(raw.exchange("GET", "/known-length"), 200, knownLength);
                    assertRawBody(raw.exchange("GET", "/empty"), 200, new byte[0]);
                    assertRawBody(raw.exchange("GET", "/error"), 500, "boom".getBytes(StandardCharsets.UTF_8));
                    var head = raw.exchange("HEAD", "/head");
                    assertRawBody(head, 200, new byte[0]);
                    assertThat(head.header("Content-Length")).isEqualTo(Integer.toString(knownLength.length));
                }
            }
        }

        @Test
        void pipelinedRequestsAreAnsweredInOrder() throws Exception {
            var small = "small".getBytes(StandardCharsets.UTF_8);
            var medium = payload(10 * 1024, 4);
            var large = payload(300 * 1024 + 1, 5);
            startServer(
                handler(GET, "/small", _ -> HttpServerResponse.of(200, HttpBody.octetStream(small))),
                handler(GET, "/medium", _ -> HttpServerResponse.of(200, HttpBodyOutput.octetStream(os -> os.write(medium)))),
                handler(GET, "/large", _ -> HttpServerResponse.of(200, HttpBodyOutput.octetStream(os -> write(os, large, WritePattern.RANDOM_SLICES, 5))))
            );

            try (var raw = new RawHttpClient(port())) {
                raw.send(
                    RawHttpClient.request("GET", "/small"),
                    RawHttpClient.request("GET", "/large"),
                    RawHttpClient.request("GET", "/medium"),
                    RawHttpClient.request("GET", "/large"),
                    RawHttpClient.request("GET", "/small")
                );
                assertRawBody(raw.readResponse(false), 200, small);
                assertRawBody(raw.readResponse(false), 200, large);
                assertRawBody(raw.readResponse(false), 200, medium);
                assertRawBody(raw.readResponse(false), 200, large);
                assertRawBody(raw.readResponse(false), 200, small);
            }
        }

        @Test
        void slowClientDoesNotMakeServerBufferWholeResponse() throws Exception {
            var chunk = payload(16 * 1024, 6);
            var total = 32L * 1024 * 1024;
            var produced = new AtomicLong();
            var body = new TrackingBody(-1, os -> {
                while (produced.get() < total) {
                    os.write(chunk);
                    produced.addAndGet(chunk.length);
                }
            });
            startServer(handler(GET, "/slow", _ -> HttpServerResponse.of(200, body)));

            try (var raw = new RawHttpClient(port(), 16 * 1024)) {
                raw.send(RawHttpClient.request("GET", "/slow"));
                var head = raw.readHead();

                // the client stops reading: the producer has to stall once socket and server buffers are full
                Thread.sleep(250);
                var stalledAt = produced.get();
                Thread.sleep(250);
                assertThat(stalledAt).isLessThan(total);
                assertThat(produced.get() - stalledAt).isLessThanOrEqualTo(1024 * 1024);

                var received = raw.readBody(head);
                assertThat(received).hasSize((int) total);
                for (var offset = 0; offset < received.length; offset += chunk.length) {
                    assertThat(Arrays.equals(received, offset, offset + chunk.length, chunk, 0, chunk.length))
                        .as("chunk at offset %d", offset)
                        .isTrue();
                }
            }
            body.assertClosedOnce();
        }

        @Test
        void clientDisconnectMidStreamStopsProducerAndClosesBody() throws Exception {
            var chunk = payload(16 * 1024, 8);
            var limit = 256L * 1024 * 1024;
            var produced = new AtomicLong();
            var producerFailure = new AtomicReference<Throwable>();
            var body = new TrackingBody(-1, os -> {
                try {
                    while (produced.get() < limit) {
                        os.write(chunk);
                        produced.addAndGet(chunk.length);
                    }
                } catch (IOException e) {
                    producerFailure.set(e);
                    throw e;
                }
            });
            startServer(
                handler(GET, "/endless", _ -> HttpServerResponse.of(200, body)),
                handler(GET, "/ok", _ -> HttpServerResponse.of(200, HttpBody.plaintext("ok")))
            );

            try (var raw = new RawHttpClient(port())) {
                raw.send(RawHttpClient.request("GET", "/endless"));
                raw.readHead();
                raw.readBytes(128 * 1024);
                raw.abort();
            }

            body.assertClosedOnce();
            assertThat(producerFailure.get()).isInstanceOf(IOException.class);
            assertThat(produced.get()).isLessThan(limit);
            try (var response = client.newCall(request("/ok").get().build()).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("ok");
            }
        }

        @Test
        void concurrentStreamsDoNotMixContent() throws Exception {
            var streams = 24;
            startServer(handler(GET, "/stream/{id}", request -> {
                var id = Integer.parseInt(request.pathParams().get("id"));
                var data = payload(256 * 1024 + id * 1013, id);
                return HttpServerResponse.of(200, HttpBodyOutput.octetStream(os -> write(os, data, WritePattern.RANDOM_SLICES, id)));
            }));

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var results = new ArrayList<Future<byte[]>>();
                for (var id = 0; id < streams; id++) {
                    var path = "/stream/" + id;
                    results.add(executor.submit(() -> {
                        try (var response = client.newCall(request(path).get().build()).execute()) {
                            assertThat(response.code()).isEqualTo(200);
                            return response.body().bytes();
                        }
                    }));
                }
                for (var id = 0; id < streams; id++) {
                    assertThat(results.get(id).get(30, TimeUnit.SECONDS))
                        .as("stream %d", id)
                        .isEqualTo(payload(256 * 1024 + id * 1013, id));
                }
            }
        }
    }

    /**
     * Response body that counts writes and closes, see {@link ResponseBodyTest}.
     */
    protected static final class TrackingBody implements HttpBodyOutput {

        private final long contentLength;
        private final HttpBodyOutput.HttpBodyWriter writer;
        private final AtomicInteger closes = new AtomicInteger();
        private final CountDownLatch closed = new CountDownLatch(1);
        public final AtomicInteger writes = new AtomicInteger();

        public TrackingBody(long contentLength, HttpBodyOutput.HttpBodyWriter writer) {
            this.contentLength = contentLength;
            this.writer = writer;
        }

        @Override
        public long contentLength() {
            return this.contentLength;
        }

        @Override
        public @Nullable String contentType() {
            return "application/octet-stream";
        }

        @Override
        public void write(OutputStream os) throws IOException {
            this.writes.incrementAndGet();
            this.writer.write(os);
        }

        @Override
        public void close() {
            this.closes.incrementAndGet();
            this.closed.countDown();
        }

        public void assertClosedOnce() throws InterruptedException {
            assertThat(this.closed.await(5, TimeUnit.SECONDS)).as("body closed").isTrue();
            // a second close would come from another completion path shortly after the first one
            Thread.sleep(20);
            assertThat(this.closes).as("body close count").hasValue(1);
        }
    }

    protected static byte[] payload(int size, long seed) {
        var bytes = new byte[size];
        new SplittableRandom(seed).nextBytes(bytes);
        return bytes;
    }

    private static void assertRawBody(RawHttpClient.Response response, int code, byte[] body) {
        assertThat(response.code()).isEqualTo(code);
        assertThat(response.body()).isEqualTo(body);
    }

    private static void awaitValue(AtomicInteger counter, int expected) throws InterruptedException {
        var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (counter.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(counter).hasValue(expected);
    }

    private static ByteBuffer buffer(byte[] data, ResponseBodyTest.BufferKind kind) {
        return switch (kind) {
            case HEAP -> ByteBuffer.wrap(data);
            case HEAP_SLICE -> {
                var padded = new byte[data.length + 20];
                System.arraycopy(data, 0, padded, 10, data.length);
                yield ByteBuffer.wrap(padded, 10, data.length).slice();
            }
            case READ_ONLY -> ByteBuffer.wrap(data).asReadOnlyBuffer();
            case DIRECT -> ByteBuffer.allocateDirect(data.length).put(data).flip();
        };
    }

    private static void write(OutputStream os, byte[] data, ResponseBodyTest.WritePattern pattern, long seed) throws IOException {
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
        var httpResponse = HttpServerResponse.of(200, HttpHeaders.of(), HttpBodyOutput.of("text/plain", 10 * 1024 * 1024, os -> os.write(data)));
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
                public void writeTo(@NonNull BufferedSink bufferedSink) throws IOException {
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

        var httpResponse = HttpServerResponse.of(200, HttpHeaders.of(), HttpBodyOutput.of("text/plain", 102400, os -> {
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
        verify(this.observation).observeRequest(ArgumentMatchers.argThat(rq -> rq.pathTemplate() == null));
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
                return HttpServerResponse.of(
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
    void testSocketReadTimeoutOnUnfinishedRequestBody() throws Exception {
        var bodyReadStarted = new CountDownLatch(1);
        var handler = handler(POST, "/", (request) -> {
            try (var body = request.body(); var is = body.asInputStream()) {
                bodyReadStarted.countDown();
                is.readAllBytes();
                return HttpServerResponse.of(200);
            }
        });
        this.startServer(handler);

        var awaitTimeout = SOCKET_TIMEOUT.multipliedBy(10);
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", this.httpServer.port()), (int) awaitTimeout.toMillis());
            socket.setSoTimeout((int) awaitTimeout.toMillis());
            var out = socket.getOutputStream();
            out.write("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 16\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();

            assertThat(bodyReadStarted.await(awaitTimeout.toMillis(), TimeUnit.MILLISECONDS))
                .as("server should have routed the request and started reading the announced body")
                .isTrue();

            var start = now();
            var in = socket.getInputStream();
            try {
                while (in.read() != -1) {
                    // the server may still flush an error response before dropping the connection
                }
            } catch (SocketTimeoutException e) {
                fail("server kept the connection with an unfinished request body open for %s, socketReadTimeout %s was not applied to it"
                    .formatted(Duration.between(start, now()), SOCKET_TIMEOUT));
            } catch (IOException e) {
                // a connection reset means the server dropped the connection as well
            }
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
        var mapper = module.byteArrayHttpServerRequestMapper();

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
        verify(this.observation, mode).observeRequest(ArgumentMatchers.argThat(rq -> rq.pathTemplate().equals(route)
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
            SOCKET_TIMEOUT,
            SOCKET_TIMEOUT,
            false,
            false,
            false,
            true,
            Duration.ofMillis(1),
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

    protected void startSystemHttpServer() {
        this.privateHttpServer = this.httpServer(valueOf($HttpServerConfig_ConfigValueMapper.DEFAULTS), privateApiHandler, NoopHttpServerTelemetry.INSTANCE);
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
        return () -> instance;
    }

    protected Request.Builder privateApiRequest(String path) {
        return request(this.privateHttpServer.port(), path);
    }

    protected Request.Builder request(String path) {
        return request(this.httpServer.port(), path);
    }

    protected int port() {
        return this.httpServer.port();
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
