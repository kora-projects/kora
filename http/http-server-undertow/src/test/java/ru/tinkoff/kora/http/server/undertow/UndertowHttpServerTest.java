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
import ru.tinkoff.kora.http.server.common.*;
import ru.tinkoff.kora.http.server.common.form.MultipartReader;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
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
}
