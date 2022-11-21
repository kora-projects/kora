package ru.tinkoff.kora.http.client.common;

import org.assertj.core.api.AbstractByteArrayAssert;
import ru.tinkoff.kora.http.client.common.response.BlockingHttpResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

public record ResponseWithBody(int code, HttpHeaders headers, byte[] body) {
    public ResponseWithBody {
    }

    public ResponseWithBody(HttpClientResponse response, byte[] body) {
        this(response.code(), response.headers(), body);
    }

    public ResponseWithBody(BlockingHttpResponse response, byte[] body) {
        this(response.code(), response.headers(), body);
    }

    public ResponseWithBody assertCode(int expected) {
        assertThat(this.code).isEqualTo(expected);
        return this;
    }


    public ResponseWithBody assertHeader(String header, String expected) {
        var values = this.headers.get(header);
        assertThat(values).contains(expected);
        return this;
    }

    public ResponseWithBody assertHeader(String header) {
        var values = this.headers.get(header);
        assertThat(values).isNotEmpty();
        return this;
    }

    public ResponseWithBody assertNoHeader(String header) {
        var values = this.headers.get(header);
        assertThat(values).isNull();
        return this;
    }

    public AbstractByteArrayAssert<?> assertBody() {
        return assertThat(this.body);
    }
}
