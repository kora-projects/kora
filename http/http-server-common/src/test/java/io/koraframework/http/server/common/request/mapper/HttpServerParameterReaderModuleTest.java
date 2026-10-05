package io.koraframework.http.server.common.request.mapper;

import io.koraframework.http.server.common.request.HttpServerParameterReader;
import io.koraframework.http.server.common.response.HttpServerResponseException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;

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
    void offsetDateTimeReaderErrorMessageQuotesValueOnce() {
        HttpServerParameterReader<OffsetDateTime> reader = module.offsetDateTimeHttpServerParameterReader();

        assertThatThrownBy(() -> reader.read("bad"))
            .isInstanceOf(HttpServerResponseException.class)
            .hasMessage("Parameter has incorrect value 'bad', expected format is '2007-12-03T10:15:30+01:00'");
    }

    @Test
    void instantReaderParsesUtcAndOffsetValues() {
        HttpServerParameterReader<Instant> reader = module.instantHttpServerParameterReader();

        assertThat(reader.read("2007-12-03T10:15:30Z")).isEqualTo(Instant.parse("2007-12-03T10:15:30Z"));
        assertThat(reader.read("2007-12-03T11:15:30+01:00")).isEqualTo(Instant.parse("2007-12-03T10:15:30Z"));
        assertThatThrownBy(() -> reader.read("2007-12-03"))
            .isInstanceOf(HttpServerResponseException.class)
            .hasMessage("Parameter has incorrect value '2007-12-03', expected format is '2007-12-03T10:15:30Z'");
    }

    @Test
    void byteArrayReaderDecodesBase64() {
        HttpServerParameterReader<byte[]> reader = module.byteArrayHttpServerParameterReader();

        assertThat(reader.read("aGVsbG8=")).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> reader.read("not base64!"))
            .isInstanceOf(HttpServerResponseException.class);
    }
}
