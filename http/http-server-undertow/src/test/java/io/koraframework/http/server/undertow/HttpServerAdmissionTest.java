package io.koraframework.http.server.undertow;

import io.koraframework.common.annotation.Tag;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.server.common.$HttpServerConfig_ConfigValueMapper;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.RawHttpClient;
import io.koraframework.http.server.common.admission.HttpServerAdmission;
import io.koraframework.http.server.common.interceptor.HttpServerInterceptor;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.request.HttpServerRequestHandlerImpl;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.router.HttpServerRouter;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.HttpServerObservation;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.koraframework.http.server.common.telemetry.impl.NoopHttpServerObservation;
import io.koraframework.http.server.common.telemetry.impl.NoopHttpServerTelemetry;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpServerAdmissionTest {

    private UndertowHttpServer server;

    @AfterEach
    void stopServer() {
        if (this.server != null) {
            this.server.release();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acquiresRoutedRequestOnVirtualThreadAndReleasesOnCompletion(boolean telemetryEnabled) throws Exception {
        var permit = new RecordingPermit();
        var admittedRequest = new AtomicReference<HttpServerRequest>();
        var acquireThread = new AtomicReference<Thread>();
        HttpServerAdmission admission = request -> {
            admittedRequest.set(request);
            acquireThread.set(Thread.currentThread());
            return permit;
        };
        startServer(admission, telemetry(telemetryEnabled), List.of(),
            HttpServerRequestHandlerImpl.get("/items/{id}", request -> {
                assertThat(request).isSameAs(admittedRequest.get());
                assertThat(permit.closes.get()).isZero();
                return HttpServerResponse.of(204);
            }));

        assertThat(get("/items/42").code()).isEqualTo(204);
        permit.assertClosedOnce();
        assertThat(admittedRequest.get().pathTemplate()).isEqualTo("/items/{id}");
        assertThat(admittedRequest.get().pathParams()).containsEntry("id", "42");
        assertThat(acquireThread.get().isVirtual()).isTrue();
        assertThat(permit.closeThread.get().isVirtual()).isFalse();
        assertThat(permit.errors).isEmpty();
    }

    @Test
    void absentAdmissionKeepsNormalProcessing() throws Exception {
        startServer(null, telemetry(false), List.of(), HttpServerRequestHandlerImpl.get("/ok", _ -> HttpServerResponse.of(204)));

        assertThat(get("/ok").code()).isEqualTo(204);
    }

    @Test
    void rejectedAdmissionSkipsInterceptorsAndHandler() throws Exception {
        var invocations = new AtomicInteger();
        HttpServerInterceptor interceptor = (request, chain) -> {
            invocations.incrementAndGet();
            return chain.process(request);
        };
        startServer(_ -> null, telemetry(false), List.of(interceptor),
            HttpServerRequestHandlerImpl.get("/rejected", _ -> {
                invocations.incrementAndGet();
                return HttpServerResponse.of(204);
            }));

        assertThat(get("/rejected").code()).isEqualTo(503);
        assertThat(invocations.get()).isZero();
    }

    @Test
    void handlerFailureIsReportedBeforeRelease() throws Exception {
        var permit = new RecordingPermit();
        var failure = new IllegalStateException("handler failed");
        startServer(_ -> permit, telemetry(false), List.of(), HttpServerRequestHandlerImpl.get("/failure", _ -> {
            throw failure;
        }));

        assertThat(get("/failure").code()).isEqualTo(500);
        permit.assertClosedOnce();
        assertThat(permit.errors).containsExactly(failure);
    }

    @Test
    void responseBodyFailureIsReportedBeforeRelease() throws Exception {
        var permit = new RecordingPermit();
        var failure = new IOException("body failed");
        var body = body(os -> {
            throw failure;
        });
        startServer(_ -> permit, telemetry(false), List.of(), HttpServerRequestHandlerImpl.get("/failure", _ -> HttpServerResponse.of(200, body)));

        assertThat(get("/failure").code()).isEqualTo(500);
        permit.assertClosedOnce();
        assertThat(permit.errors).containsExactly(failure);
    }

    @Test
    void streamingResponseHoldsAdmissionUntilExchangeCompletes() throws Exception {
        var slots = new Semaphore(1);
        RecordingPermit permit = new RecordingPermit() {
            @Override
            public void close() {
                slots.release();
                super.close();
            }
        };
        var finishBody = new CountDownLatch(1);
        var producerReady = new CountDownLatch(1);
        var chunk = new byte[80 * 1024];
        var body = body(os -> {
            os.write(chunk);
            producerReady.countDown();
            try {
                if (!finishBody.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting to finish response");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        });
        startServer(_ -> slots.tryAcquire() ? permit : null, telemetry(false), List.of(),
            HttpServerRequestHandlerImpl.get("/stream", _ -> HttpServerResponse.of(200, body)));

        try (var raw = new RawHttpClient(this.server.port())) {
            raw.send(RawHttpClient.request("GET", "/stream"));
            var head = raw.readHead();
            assertThat(head.code()).isEqualTo(200);
            assertThat(producerReady.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(permit.closes.get()).isZero();
            assertThat(get("/stream").code()).isEqualTo(503);
            finishBody.countDown();
            assertThat(raw.readBody(head)).hasSize(chunk.length);
        } finally {
            finishBody.countDown();
        }
        permit.assertClosedOnce();
        assertThat(slots.availablePermits()).isEqualTo(1);
    }

    @Test
    void streamingDisconnectReleasesAdmission() throws Exception {
        var permit = new RecordingPermit();
        var body = body(os -> {
            var chunk = new byte[16 * 1024];
            for (var i = 0; i < 2048; i++) {
                os.write(chunk);
            }
        });
        startServer(_ -> permit, telemetry(false), List.of(), HttpServerRequestHandlerImpl.get("/stream", _ -> HttpServerResponse.of(200, body)));

        try (var raw = new RawHttpClient(this.server.port(), 16 * 1024)) {
            raw.send(RawHttpClient.request("GET", "/stream"));
            assertThat(raw.readHead().code()).isEqualTo(200);
            raw.abort();
        }
        permit.assertClosedOnce();
    }

    @Test
    void failedCompletionListenerRegistrationReturnsPermitWithoutInvokingHandler() throws Exception {
        var permit = new RecordingPermit();
        var invocations = new AtomicInteger();
        HttpServerAdmission admission = _ -> {
            UndertowContext.VALUE.get().exchange().endExchange();
            return permit;
        };
        startServer(admission, telemetry(false), List.of(), HttpServerRequestHandlerImpl.get("/complete", _ -> {
            invocations.incrementAndGet();
            return HttpServerResponse.of(204);
        }));

        get("/complete");
        permit.assertClosedOnce();
        assertThat(invocations.get()).isZero();
    }

    @Test
    void failingTelemetryCompletionDoesNotPreventPermitRelease() throws Exception {
        var permit = new RecordingPermit();
        var observation = mock(HttpServerObservation.class);
        when(observation.span()).thenReturn(Span.getInvalid());
        when(observation.observeRequest(any())).thenAnswer(returnsFirstArg());
        when(observation.observeResponse(any())).thenAnswer(returnsFirstArg());
        doThrow(new IllegalStateException("telemetry failed")).when(observation).end();
        startServer(_ -> permit, _ -> observation, List.of(), HttpServerRequestHandlerImpl.get("/ok", _ -> HttpServerResponse.of(204)));

        assertThat(get("/ok").code()).isEqualTo(204);
        permit.assertClosedOnce();
    }

    @Test
    void factoryAdmissionDependencyIsNullableAndUsesFactoryTag() {
        var method = java.util.Arrays.stream(UndertowHttpServerFactoryModule.class.getDeclaredMethods())
            .filter(m -> m.getName().equals("handler"))
            .findFirst().orElseThrow();
        var parameter = java.util.Arrays.stream(method.getParameters())
            .filter(p -> p.getType().equals(HttpServerAdmission.class))
            .findFirst().orElseThrow();

        assertThat(parameter.getAnnotation(Tag.class).value()).isEqualTo(Tag.Factory.class);
        assertThat(parameter.getAnnotatedType().isAnnotationPresent(Nullable.class)).isTrue();
    }

    private void startServer(@Nullable HttpServerAdmission admission, HttpServerTelemetry telemetry,
                             List<HttpServerInterceptor> interceptors, HttpServerRequestHandler... handlers) {
        var config = new HttpServerConfig() {
            @Override
            public int port() {
                return 0;
            }

            @Override
            public HttpServerTelemetryConfig telemetry() {
                return $HttpServerConfig_ConfigValueMapper.DEFAULTS.telemetry();
            }
        };
        var undertowConfig = new UndertowConfig() {};
        var factory = new UndertowHttpServerFactoryModule("admission-test", "httpServer");
        var router = new HttpServerRouter(List.of(handlers), interceptors, config);
        var handler = factory.handler(() -> undertowConfig, config, router, (_, _, _) -> telemetry, admission);
        this.server = factory.server(null, () -> undertowConfig, () -> handler, () -> config, null);
        this.server.init();
    }

    private RawHttpClient.Response get(String path) throws IOException {
        try (var raw = new RawHttpClient(this.server.port())) {
            return raw.exchange("GET", path);
        }
    }

    private static HttpServerTelemetry telemetry(boolean enabled) {
        return enabled ? _ -> NoopHttpServerObservation.INSTANCE : NoopHttpServerTelemetry.INSTANCE;
    }

    private static HttpBodyOutput body(BodyWriter writer) {
        return new HttpBodyOutput() {
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
                writer.write(os);
            }

            @Override
            public void close() {}
        };
    }

    private interface BodyWriter {
        void write(OutputStream os) throws IOException;
    }

    private static class RecordingPermit implements HttpServerAdmission.Permit {
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicReference<Thread> closeThread = new AtomicReference<>();
        private final ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        private final CountDownLatch closed = new CountDownLatch(1);

        @Override
        public void observeError(Throwable error) {
            assertThat(this.closes.get()).isZero();
            this.errors.add(error);
        }

        @Override
        public void close() {
            this.closeThread.set(Thread.currentThread());
            this.closes.incrementAndGet();
            this.closed.countDown();
        }

        private void assertClosedOnce() throws InterruptedException {
            assertThat(this.closed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(this.closes.get()).isEqualTo(1);
        }
    }
}
