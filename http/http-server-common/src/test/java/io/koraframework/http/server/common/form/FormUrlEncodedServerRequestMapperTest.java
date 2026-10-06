package io.koraframework.http.server.common.form;

import io.koraframework.http.server.common.request.mapper.FormUrlEncodedServerRequestMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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

    @Test
    void testNoValue() {
        var string = "val1=2112&val1=3232&val2";

        var map = FormUrlEncodedServerRequestMapper.read(string);

        assertThat(map)
            .hasSize(2)
            .hasEntrySatisfying("val1", v -> assertThat(v.values()).containsExactly("2112", "3232"))
            .hasEntrySatisfying("val2", v -> assertThat(v.values()).isEmpty());
    }

    @Test
    void testValueWithEqualsSign() {
        var string = "val1=abc==&val2=x=1";

        var map = FormUrlEncodedServerRequestMapper.read(string);

        assertThat(map)
            .hasSize(2)
            .hasEntrySatisfying("val1", v -> assertThat(v.values()).containsExactly("abc=="))
            .hasEntrySatisfying("val2", v -> assertThat(v.values()).containsExactly("x=1"));
    }

    @Test
    void testEmptyValue() {
        var string = "val1=&val2=test";

        var map = FormUrlEncodedServerRequestMapper.read(string);

        assertThat(map)
            .hasSize(2)
            .hasEntrySatisfying("val1", v -> assertThat(v.values()).containsExactly(""))
            .hasEntrySatisfying("val2", v -> assertThat(v.values()).containsExactly("test"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testContentTypeWithCharset() throws Exception {
        var body = new ByteArrayInputStream("val1=test".getBytes(StandardCharsets.UTF_8));
        var request = new MultipartReaderUtilsTest.SimpleHttpServerRequest("POST", "/", body, new Map.Entry[]{
            Map.entry("content-type", "application/x-www-form-urlencoded; charset=UTF-8")
        }, Map.of());

        var form = new FormUrlEncodedServerRequestMapper().apply(request);

        assertThat(form.get("val1").values()).containsExactly("test");
    }
}
