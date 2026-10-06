package io.koraframework.http.server.common.form;

import io.koraframework.http.server.common.request.mapper.FormUrlEncodedServerRequestMapper;
import io.koraframework.http.server.common.response.HttpServerResponseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormUrlEncodedServerRequestMapperTest {
    @Test
    void test() {
        var string = "val1=2112&val1=3232&val2=test";

        var map = FormUrlEncodedServerRequestMapper.read(string);

        assertThat(map)
            .hasSize(2)
            .hasEntrySatisfying("val1", v -> assertThat(v.values()).containsExactly("2112", "3232"))
            .hasEntrySatisfying("val2", v -> assertThat(v.values()).containsExactly("test"));
    }

    @Test
    void testNoValue() {
        var string = "val1=2112&val1=3232&val2";

        var map = FormUrlEncodedServerRequestMapper.read(string);

        assertThat(map)
            .hasSize(2)
            .hasEntrySatisfying("val1", v -> assertThat(v.values()).containsExactly("2112", "3232"))
            .hasEntrySatisfying("val2", v -> assertThat(v.values()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a=%zz&b=1", "a=%2", "%zz=1"})
    void malformedPercentEscapeIsBadRequest(String body) {
        assertThatThrownBy(() -> FormUrlEncodedServerRequestMapper.read(body))
            .isInstanceOfSatisfying(HttpServerResponseException.class, e -> assertThat(e.code()).isEqualTo(400))
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
