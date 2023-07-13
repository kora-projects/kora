package ru.tinkoff.kora.config.hocon;

import com.typesafe.config.ConfigFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.origin.SimpleConfigOrigin;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class HoconConfigFactoryTest {

    @Test
    void testFromHocon() {
        var hocon = ConfigFactory.parseString("""
                testObject {
                  f1 = "test1"
                }
                test {
                  f1 = "test1"
                  f2 = prefix_${testObject.f1}_suffix
                  f3 = 10
                  f4 = 15 seconds
                  f5 = true
                }
                testArray = [1, 2, 3]
                """)
            .resolve();

        var config = HoconConfigFactory.fromHocon(new SimpleConfigOrigin(""), hocon);

        assertThat(config.get("testObject")).isInstanceOf(ConfigValue.ObjectValue.class);
        assertThat(config.get("testObject.f1")).isInstanceOf(ConfigValue.StringValue.class)
            .extracting(ConfigValue::asString).isEqualTo("test1");

        assertThat(config.get("test")).isInstanceOf(ConfigValue.ObjectValue.class);
        assertThat(config.get("test.f1")).isInstanceOf(ConfigValue.StringValue.class)
            .extracting(ConfigValue::asString).isEqualTo("test1");
        assertThat(config.get("test.f2")).isInstanceOf(ConfigValue.StringValue.class)
            .extracting(ConfigValue::asString).isEqualTo("prefix_test1_suffix");
        assertThat(config.get("test.f3")).isInstanceOf(ConfigValue.NumberValue.class)
            .extracting(ConfigValue::asNumber).isEqualTo(10);
        assertThat(config.get("test.f4")).isInstanceOf(ConfigValue.StringValue.class)
            .extracting(ConfigValue::asString).isEqualTo("15 seconds");
        assertThat(config.get("test.f5")).isInstanceOf(ConfigValue.BooleanValue.class)
            .extracting(ConfigValue::asBoolean).isEqualTo(true);
        assertThat(config.get("testArray")).isInstanceOf(ConfigValue.ArrayValue.class)
            .extracting(v -> v.asArray().value().stream().map(ConfigValue::value).toList())
            .isEqualTo(List.of(1, 2, 3));
    }
}
