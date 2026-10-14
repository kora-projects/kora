package ru.tinkoff.kora.http.server.undertow;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.undertow.server.DefaultByteBufferPool;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;
import ru.tinkoff.kora.http.server.common.*;
import ru.tinkoff.kora.http.server.common.form.MultipartReader;
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static ru.tinkoff.kora.http.common.HttpMethod.POST;

class UndertowHttpServerTest extends HttpServerTestKit {

    @Override
    protected HttpServer httpServer(ValueOf<HttpServerConfig> config, PublicApiHandler publicApiHandler) {
        return new UndertowHttpServer(
            config,
            valueOf(new UndertowPublicApiHandler(publicApiHandler, null)),
            null,
            new DefaultByteBufferPool(false, 1024),
            null,
            null
        );
    }

    @Override
    protected PrivateHttpServer privateHttpServer(ValueOf<HttpServerConfig> config, PrivateApiHandler privateApiHandler) {
        return new UndertowPrivateHttpServer(config, valueOf(new UndertowPrivateApiHandler(privateApiHandler)), null, new DefaultByteBufferPool(false, 1024));
    }

    @Test
    void multipartWithLargeFileDoesNotLogAsyncIoDispatchError() throws Exception {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        var undertowLogger = (Logger) LoggerFactory.getLogger("io.undertow");
        undertowLogger.addAppender(appender);
        try {
            var handler = new HttpServerRequestHandler() {
                @Override
                public String method() {
                    return "POST";
                }

                @Override
                public String routeTemplate() {
                    return "/";
                }

                @Override
                public CompletionStage<HttpServerResponse> handle(Context ctx, HttpServerRequest request) {
                    return MultipartReader.read(request).thenApply(parts -> HttpServerResponse.of(204));
                }
            };
            this.startServer(handler);

            var boundary = "----koraBoundary";
            var file = new byte[3 * 1024 * 1024];
            ThreadLocalRandom.current().nextBytes(file);
            var head = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"message\"\r\n\r\n"
                + "hello\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"test.bin\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            var tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

            // Stream the body slowly so the initial prefetch read returns 0 and the body is
            // delivered asynchronously on the IO thread (the path that triggered UT000146).
            var request = request("/")
                .post(new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return MediaType.parse("multipart/form-data; boundary=" + boundary);
                    }

                    @Override
                    public long contentLength() {
                        return head.length + file.length + tail.length;
                    }

                    @Override
                    public void writeTo(okio.BufferedSink sink) throws IOException {
                        sink.write(head);
                        sink.flush();
                        sleep(200);
                        sink.write(file);
                        sink.flush();
                        sleep(200);
                        sink.write(tail);
                    }

                    private void sleep(long millis) throws IOException {
                        try {
                            Thread.sleep(millis);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException(e);
                        }
                    }
                })
                .build();

            try (var response = client.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(204);
            }

            var errors = appender.list.stream()
                .filter(e -> e.getLevel().levelInt >= ch.qos.logback.classic.Level.ERROR_INT)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
            assertThat(errors)
                .as("no async-IO-resumed + dispatch collision (UT000146) should be logged")
                .noneMatch(m -> m.contains("UT000146") || m.contains("UT005071"));
        } finally {
            undertowLogger.detachAppender(appender);
        }
    }

    @Test
    void blockingResponseToGetWithContentLengthBodyCompletesExchange() throws Exception {
        startServer(blockingHandler(ctx -> HttpServerResponse.of(200, streamingBody(ctx))));
        assertExchangeCompletes(HttpRequest.BodyPublishers.ofString("{}"), 200);
    }

    @Test
    void blockingResponseToGetWithChunkedBodyCompletesExchange() throws Exception {
        startServer(blockingHandler(ctx -> HttpServerResponse.of(200, streamingBody(ctx))));
        assertExchangeCompletes(HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofString("{}")), 200);
    }

    @Test
    void blockingErrorResponseToGetWithBodyCompletesExchange() throws Exception {
        startServer(blockingHandler(ctx -> {
            throw new StreamingResponseException(400, streamingBody(ctx));
        }));
        assertExchangeCompletes(HttpRequest.BodyPublishers.ofString("{}"), 400);
    }

    // GET with a body the handler never reads: the response has to complete the exchange anyway,
    // otherwise telemetry is never closed and the keep-alive connection stops accepting requests
    private void assertExchangeCompletes(HttpRequest.BodyPublisher body, int code) throws Exception {
        var uri = request("/").build().url().uri();
        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        var withBody = HttpRequest.newBuilder(uri).method("GET", body).timeout(Duration.ofSeconds(2)).build();
        assertThat(client.send(withBody, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(code);
        verify(metrics, timeout(1000)).requestFinished(eq(code), any(), any(), any(), eq("GET"), eq("/"), any(), anyLong(), any());

        var next = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(2)).build();
        assertThat(client.send(next, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(code);
    }

    // completes on a worker thread after the handler returned, like a blocking controller
    private static HttpServerRequestHandler blockingHandler(Function<Context, HttpServerResponse> response) {
        return new HttpServerRequestHandler() {
            @Override
            public String method() {
                return "GET";
            }

            @Override
            public String routeTemplate() {
                return "/";
            }

            @Override
            public CompletionStage<HttpServerResponse> handle(Context ctx, HttpServerRequest request) {
                var executor = CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS);
                return BlockingRequestExecutor.defaultExecute(ctx, executor::execute, () -> response.apply(ctx));
            }
        };
    }

    // no full content available, like JSON response bodies
    private static HttpBodyOutput streamingBody(Context ctx) {
        return HttpBodyOutput.of("text/plain", FlowUtils.one(ctx, ByteBuffer.wrap("ok".getBytes(StandardCharsets.UTF_8))));
    }

    private static final class StreamingResponseException extends RuntimeException implements HttpServerResponse {
        private final int code;
        private final HttpBodyOutput body;

        private StreamingResponseException(int code, HttpBodyOutput body) {
            this.code = code;
            this.body = body;
        }

        @Override
        public int code() {
            return this.code;
        }

        @Override
        public MutableHttpHeaders headers() {
            return HttpHeaders.of();
        }

        @Override
        public HttpBodyOutput body() {
            return this.body;
        }
    }
}
