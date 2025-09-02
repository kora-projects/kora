package ru.tinkoff.kora.http.server.annotation.processor.server;

import org.assertj.core.api.AbstractByteArrayAssert;
import org.assertj.core.api.Assertions;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class HttpResponseAssert {
    private final int code;
    private final long contentLength;
    private final String contentType;
    private final HttpHeaders headers;
    private final byte[] body;


    public HttpResponseAssert(HttpServerResponse httpResponse) {
        this.code = httpResponse.code();
        try (var body = httpResponse.body()) {
            this.contentLength = body == null ? -1 : body.contentLength();
            this.contentType = body == null ? null : body.contentType();
            this.headers = httpResponse.headers();
            if (body == null) {
                this.body = new byte[0];
            } else {
                var baos = new ByteArrayOutputStream();
                body.write(baos);
                this.body = baos.toByteArray();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public HttpResponseAssert hasStatus(int expected) {
        Assertions.assertThat(this.code)
            .withFailMessage("Expected response code %d, got %d(%s)", expected, this.code, new String(this.body, StandardCharsets.UTF_8))
            .isEqualTo(expected);
        return this;
    }

    public HttpResponseAssert hasContentLength(long expected) {
        Assertions.assertThat(this.contentLength)
            .withFailMessage("Expected response body length %d, got %d", this.contentLength, expected)
            .isEqualTo(expected);
        return this;
    }

    public HttpResponseAssert hasBody(byte[] expected) {

        Assertions.assertThat(this.body)
            .withFailMessage(() -> {
                var expectedBase64 = Base64.getEncoder().encodeToString(expected).indent(4);
                var gotBase64 = Base64.getEncoder().encodeToString(this.body).indent(4);

                return "Expected response body: \n%s\n\n\tgot: \n%s".formatted(expectedBase64, gotBase64);
            })
            .isEqualTo(expected);
        return this;
    }

    public HttpResponseAssert hasNoBody() {

        Assertions.assertThat(this.body)
            .withFailMessage(() -> {
                var gotBase64 = Base64.getEncoder().encodeToString(this.body).indent(4);

                return "Expected response body: \nempty\n\n\tgot: \n%s".formatted(gotBase64);
            })
            .isEqualTo(new byte[0]);
        return this;
    }

    public HttpResponseAssert hasBody(String expected) {
        var bodyString = new String(this.body, StandardCharsets.UTF_8);

        Assertions.assertThat(bodyString)
            .withFailMessage(() -> "Expected response body: \n%s\n\n\tgot: \n%s".formatted(expected.indent(4), bodyString.indent(4)))
            .isEqualTo(expected);
        return this;
    }

    public AbstractByteArrayAssert<?> hasBody() {
        return Assertions.assertThat(this.body);
    }

    public HttpResponseAssert hasHeader(String header, String value) {
        var actualValue = this.headers.getFirst(header);

        Assertions.assertThat(actualValue).isEqualTo(value);

        return this;
    }
}
