package io.koraframework.http.server.common.request.mapper;

import io.koraframework.http.server.common.request.HttpServerParameterReader;
import io.koraframework.http.server.common.response.HttpServerResponseException;
import io.koraframework.json.common.JsonModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpServerParameterReaderModuleTest {

    private final HttpServerParameterReaderModule module = new HttpServerParameterReaderModule() {};

    @Test
    void booleanReaderParsesTrueAndFalse() {
        HttpServerParameterReader<Boolean> reader = module.booleanHttpServerParameterReader();

        assertThat(reader.read("true")).isTrue();
        assertThat(reader.read("false")).isFalse();
    }

    @Test
    void booleanReaderRejectsInvalidValueInsteadOfSilentlyReturningFalse() {
        HttpServerParameterReader<Boolean> reader = module.booleanHttpServerParameterReader();

        // Boolean.parseBoolean would silently return false here; strict parsing must reject it with a 400
        assertThatThrownBy(() -> reader.read("yes"))
            .isInstanceOf(HttpServerResponseException.class);
        assertThatThrownBy(() -> reader.read("1"))
            .isInstanceOf(HttpServerResponseException.class);
        assertThatThrownBy(() -> reader.read("TRUE"))
            .isInstanceOf(HttpServerResponseException.class);
    }

    @Test
    void jsonReaderParsesTheWholeValue() {
        var reader = new JsonHttpServerParameterReader<>(new JsonModule() {}.integerJsonReader());

        assertThat(reader.read("42")).isEqualTo(42);
    }

    @Test
    void jsonReaderRejectsInvalidValueWithBadRequest() {
        var reader = new JsonHttpServerParameterReader<>(new JsonModule() {}.integerJsonReader());

        assertThatThrownBy(() -> reader.read("\"x\""))
            .isInstanceOfSatisfying(HttpServerResponseException.class, e -> assertThat(e.code()).isEqualTo(400));
    }
}
