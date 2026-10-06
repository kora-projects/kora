package io.koraframework.http.server.common.request.mapper;

import io.koraframework.http.server.common.request.HttpServerParameterReader;
import io.koraframework.http.server.common.response.HttpServerResponseException;
import io.koraframework.json.common.JsonModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
    void jsonReaderReadsObjectAndArray() {
        var json = new JsonModule() {};
        var mapReader = module.jsonHttpServerParameterReader(json.mapJsonReader(json.integerJsonReader()));
        var listReader = module.jsonHttpServerParameterReader(json.listJsonReader(json.stringJsonReader()));

        assertThat(mapReader.read("{\"a\":1}")).isEqualTo(Map.of("a", 1));
        assertThat(listReader.read("[\"x\"]")).isEqualTo(List.of("x"));
    }
}
