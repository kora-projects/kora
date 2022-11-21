package ru.tinkoff.kora.http.server.annotation.processor.server;

import org.assertj.core.api.AbstractByteArrayAssert;
import org.assertj.core.api.Assertions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public class HttpResponseAssert {
    private final int code;
    private final int contentLength;
    private final String contentType;
    private final HttpHeaders headers;
    private final byte[] body;


    public HttpResponseAssert(HttpServerResponse httpResponse) {
        this.code = httpResponse.code();
        this.contentLength = httpResponse.contentLength();
        this.contentType = httpResponse.contentType();
        this.headers = httpResponse.headers();
        this.body = Flux.from(httpResponse.body())
            .reduce(new byte[0], (bytes, byteBuffer) -> {
                var newArr = Arrays.copyOf(bytes, bytes.length + byteBuffer.remaining());
                byteBuffer.get(newArr, bytes.length, byteBuffer.remaining());
                return newArr;
            })
            .switchIfEmpty(Mono.just(new byte[0]))
            .block();
    }

    public HttpResponseAssert verifyStatus(int expected) {
        Assertions.assertThat(this.code)
            .withFailMessage("Expected response code %d, got %d(%s)", expected, this.code, new String(this.body, StandardCharsets.UTF_8))
            .isEqualTo(expected);
        return this;
    }

    public HttpResponseAssert verifyContentLength(int expected) {
        Assertions.assertThat(this.contentLength)
            .withFailMessage("Expected response body length %d, got %d", this.contentLength, expected)
            .isEqualTo(expected);
        return this;
    }

    public HttpResponseAssert verifyBody(byte[] expected) {

        Assertions.assertThat(this.body)
            .withFailMessage(() -> {
                var expectedBase64 = Base64.getEncoder().encodeToString(expected).indent(4);
                var gotBase64 = Base64.getEncoder().encodeToString(this.body).indent(4);

                return "Expected response body: \n%s\n\n\tgot: \n%s".formatted(expectedBase64, gotBase64);
            })
            .isEqualTo(expected);
        return this;
    }

    public HttpResponseAssert verifyBody(String expected) {
        var bodyString = new String(this.body, StandardCharsets.UTF_8);

        Assertions.assertThat(bodyString)
            .withFailMessage(() -> "Expected response body: \n%s\n\n\tgot: \n%s".formatted(expected.indent(4), bodyString.indent(4)))
            .isEqualTo(expected);
        return this;
    }

    public AbstractByteArrayAssert<?> verifyBody() {
        return Assertions.assertThat(this.body);
    }
}
