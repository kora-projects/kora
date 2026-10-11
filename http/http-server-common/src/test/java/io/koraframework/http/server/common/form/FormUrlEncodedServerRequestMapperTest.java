package io.koraframework.http.server.common.form;

import io.koraframework.http.server.common.request.mapper.FormUrlEncodedServerRequestMapper;
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
    void testDelimitedSplitsBeforeDecoding() {
        var string = "csv=a%2Cb,c&pipes=x|y%7Cz&spaces=one+two%20three&other=1";

        // an encoded delimiter belongs to a value
        assertThat(FormUrlEncodedServerRequestMapper.readDelimited(string, "csv", ",")).containsExactly("a,b", "c");
        assertThat(FormUrlEncodedServerRequestMapper.readDelimited(string, "pipes", "|")).containsExactly("x", "y|z");
        // `+` is a space of a value, the space delimiter is `%20`
        assertThat(FormUrlEncodedServerRequestMapper.readDelimited(string, "spaces", " ")).containsExactly("one two", "three");
    }

    @Test
    void testDelimitedWithEncodedDelimiterOnly() {
        // a client that encodes the delimiter along with the values
        assertThat(FormUrlEncodedServerRequestMapper.readDelimited("csv=1%2C2%2C3", "csv", ",")).containsExactly("1", "2", "3");
    }

    @Test
    void testDelimitedAbsentAndEmpty() {
        assertThat(FormUrlEncodedServerRequestMapper.readDelimited("other=1", "csv", ",")).isNull();
        assertThat(FormUrlEncodedServerRequestMapper.readDelimited("csv=&other=1", "csv", ",")).isEmpty();
        assertThat(FormUrlEncodedServerRequestMapper.readDelimited("csv", "csv", ",")).isEmpty();
    }
}
