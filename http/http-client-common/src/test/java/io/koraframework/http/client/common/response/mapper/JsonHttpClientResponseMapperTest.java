package io.koraframework.http.client.common.response.mapper;

import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.json.common.JsonReader;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonHttpClientResponseMapperTest {

    // Strict object reader, like a generated one: fails on anything but '{...}'
    private final JsonHttpClientResponseMapper<String> mapper = new JsonHttpClientResponseMapper<>(parser -> {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new StreamReadException(parser, "expected an object, but got " + parser.currentToken());
        }
        parser.skipChildren();
        return "object";
    });

    @Test
    void emptyBodyMapsToNull() throws Exception {
        assertThat(mapper.apply(response(""))).isNull();
        assertThat(mapper.apply(response("  \n"))).isNull();
    }

    @Test
    void nonEmptyBodyIsReadAsBefore() throws Exception {
        assertThat(mapper.apply(response("{}"))).isEqualTo("object");
        assertThat(mapper.apply(response("null"))).isNull();
        assertThatThrownBy(() -> mapper.apply(response("[1]"))).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> mapper.apply(response("{garbage"))).isInstanceOf(JacksonException.class);
    }

    private static HttpClientResponse response(String body) {
        return new HttpClientResponse() {
            @Override
            public int code() {
                return 200;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.empty();
            }

            @Override
            public HttpBodyInput body() {
                return HttpBody.json(body);
            }

            @Override
            public void close() {}
        };
    }
}
