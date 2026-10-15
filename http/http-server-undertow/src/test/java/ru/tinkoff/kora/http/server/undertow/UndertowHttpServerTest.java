package ru.tinkoff.kora.http.server.undertow;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.undertow.server.DefaultByteBufferPool;
import jakarta.annotation.Nullable;
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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    @Test
    void blockingResponseToGetWithDelayedBodyDoesNotHoldWorkerThread() throws Exception {
        startServer(blockingHandler(ctx -> HttpServerResponse.of(200, streamingBody(ctx))));
        var uri = request("/").build().url().uri();
        try (var socket = new Socket(uri.getHost(), uri.getPort())) {
            socket.setSoTimeout(2000);
            var out = socket.getOutputStream();
            var in = socket.getInputStream();

            // body is declared, but not sent yet
            out.write("GET / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 2\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            assertThat(readResponse(in)).startsWith("HTTP/1.1 200");

            Thread.sleep(300);
            assertThat(threadsDrainingRequestBody()).isEmpty();
            verify(metrics, never()).requestFinished(anyInt(), any(), any(), any(), any(), any(), any(), anyLong(), any());

            // body arrives later: exchange completes and the same connection serves the next request
            out.write("{}".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            verify(metrics, timeout(1000)).requestFinished(eq(200), any(), any(), any(), eq("GET"), eq("/"), any(), anyLong(), any());

            out.write("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            assertThat(readResponse(in)).startsWith("HTTP/1.1 200");
        }
    }

    @Test
    void blockingErrorResponseWithoutBodyKeepsHeaders() throws Exception {
        startServer(blockingHandler(ctx -> {
            throw new StreamingResponseException(400, HttpHeaders.of("x-test-header", "test-value"), null);
        }));
        var uri = request("/").build().url().uri();
        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        var rs = client.send(HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(rs.statusCode()).isEqualTo(400);
        assertThat(rs.headers().firstValue("x-test-header")).hasValue("test-value");
    }

    @Test
    void blockingErrorResponseWithKnownLengthSetsContentLength() throws Exception {
        // larger than the server buffer, otherwise undertow computes the length itself
        var content = "a".repeat(4096);
        startServer(blockingHandler(ctx -> {
            var body = HttpBodyOutput.of("text/plain", content.length(), FlowUtils.one(ctx, ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8))));
            throw new StreamingResponseException(400, body);
        }));
        var uri = request("/").build().url().uri();
        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        var rs = client.send(HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(rs.statusCode()).isEqualTo(400);
        assertThat(rs.headers().firstValue("content-length")).hasValue(String.valueOf(content.length()));
        assertThat(rs.headers().firstValue("transfer-encoding")).isEmpty();
        assertThat(rs.body()).isEqualTo(content);
    }

    @Test
    void blockingResponseFailedAfterResponseStartedClosesConnection() throws Exception {
        startServer(blockingHandler(ctx -> HttpServerResponse.of(200, failingAfterFlushBody())));
        var uri = request("/").build().url().uri();
        try (var socket = new Socket(uri.getHost(), uri.getPort())) {
            socket.setSoTimeout(2000);
            var out = socket.getOutputStream();
            out.write("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();

            // partially sent chunked response can't be completed, so the server has to close the connection
            var received = socket.getInputStream().readAllBytes();
            assertThat(new String(received, StandardCharsets.US_ASCII)).startsWith("HTTP/1.1 200").contains("partial");
        }
        verify(metrics, timeout(1000)).requestFinished(eq(200), any(), any(), any(), eq("GET"), eq("/"), any(), anyLong(), any(IllegalStateException.class));
    }

    @Test
    void blockingResponseFailedBeforeResponseStartedSendsServerError() throws Exception {
        startServer(blockingHandler(ctx -> HttpServerResponse.of(200, failingBeforeFlushBody())));
        assertServerErrorWithoutPartialBody();
    }

    @Test
    void blockingErrorResponseFailedBeforeResponseStartedSendsServerError() throws Exception {
        startServer(blockingHandler(ctx -> {
            throw new StreamingResponseException(400, failingBeforeFlushBody());
        }));
        assertServerErrorWithoutPartialBody();
    }

    // body failed while its beginning was still buffered: client gets 500 instead of the truncated body, connection stays usable
    private void assertServerErrorWithoutPartialBody() throws Exception {
        var uri = request("/").build().url().uri();
        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        var rs = client.send(HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(rs.statusCode()).isEqualTo(500);
        assertThat(rs.body()).isEmpty();
        verify(metrics, timeout(1000)).requestFinished(eq(500), any(), any(), any(), eq("GET"), eq("/"), any(), anyLong(), any(IllegalStateException.class));

        var next = client.send(HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(next.statusCode()).isEqualTo(500);
    }

    private static List<String> threadsDrainingRequestBody() {
        return Thread.getAllStackTraces().entrySet().stream()
            .filter(e -> Arrays.stream(e.getValue()).anyMatch(f -> f.getClassName().equals("io.undertow.io.UndertowInputStream") && f.getMethodName().equals("close")))
            .map(e -> e.getKey().getName())
            .toList();
    }

    // reads one response with either content-length or chunked body, returns its status line
    private static String readResponse(InputStream in) throws IOException {
        var head = new StringBuilder();
        while (!head.toString().endsWith("\r\n\r\n")) {
            var b = in.read();
            if (b < 0) {
                throw new EOFException("Connection closed while reading response headers: " + head);
            }
            head.append((char) b);
        }
        var headers = head.toString().toLowerCase(Locale.ROOT);
        var contentLength = Pattern.compile("\r\ncontent-length: *(\\d+)").matcher(headers);
        if (contentLength.find()) {
            in.readNBytes(Integer.parseInt(contentLength.group(1)));
        } else if (headers.contains("\r\ntransfer-encoding: chunked")) {
            var body = new StringBuilder();
            while (!body.toString().endsWith("\r\n0\r\n\r\n") && !body.toString().equals("0\r\n\r\n")) {
                var b = in.read();
                if (b < 0) {
                    throw new EOFException("Connection closed while reading chunked response body");
                }
                body.append((char) b);
            }
        }
        return head.substring(0, head.indexOf("\r\n"));
    }

    // body fails while its beginning is still in the server buffer
    private static HttpBodyOutput failingBeforeFlushBody() {
        return new HttpBodyOutput() {
            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public String contentType() {
                return "application/json";
            }

            @Override
            public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void write(OutputStream os) throws IOException {
                os.write("{\"items\":[1,2".getBytes(StandardCharsets.UTF_8));
                throw new IllegalStateException("serialization failed");
            }

            @Override
            public void close() {
            }
        };
    }

    // response is started with a flushed chunk and then the body fails
    private static HttpBodyOutput failingAfterFlushBody() {
        return new HttpBodyOutput() {
            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public String contentType() {
                return "text/plain";
            }

            @Override
            public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void write(OutputStream os) throws IOException {
                os.write("partial".getBytes(StandardCharsets.UTF_8));
                os.flush();
                throw new IllegalStateException("body failed");
            }

            @Override
            public void close() {
            }
        };
    }

    private static final class StreamingResponseException extends RuntimeException implements HttpServerResponse {
        private final int code;
        private final MutableHttpHeaders headers;
        @Nullable
        private final HttpBodyOutput body;

        private StreamingResponseException(int code, HttpBodyOutput body) {
            this(code, HttpHeaders.of(), body);
        }

        private StreamingResponseException(int code, MutableHttpHeaders headers, @Nullable HttpBodyOutput body) {
            this.code = code;
            this.headers = headers;
            this.body = body;
        }

        @Override
        public int code() {
            return this.code;
        }

        @Override
        public MutableHttpHeaders headers() {
            return this.headers;
        }

        @Override
        public HttpBodyOutput body() {
            return this.body;
        }
    }
}
