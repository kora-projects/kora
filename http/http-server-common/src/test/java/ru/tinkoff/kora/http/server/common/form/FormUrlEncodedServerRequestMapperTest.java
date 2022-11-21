package ru.tinkoff.kora.http.server.common.form;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
