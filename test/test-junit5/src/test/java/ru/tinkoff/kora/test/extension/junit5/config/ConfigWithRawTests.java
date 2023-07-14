package ru.tinkoff.kora.test.extension.junit5.config;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestConfigModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraConfigModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestConfigApplication;

import javax.annotation.Nonnull;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestConfigApplication.class)
public class ConfigWithRawTests implements KoraAppTestConfigModifier {

    @Override
    public @Nonnull KoraConfigModification config() {
        return KoraConfigModification.ofString("""
            myconfig {
              myinnerconfig {
                myproperty = 1
              }
            }
            """);
    }

    @Test
    void parameterConfigFromMethodInjected(@TestComponent Config config) {
        assertThat(config.get("myconfig")).isInstanceOf(ConfigValue.ObjectValue.class);
        assertThat(config.get("myconfig.myinnerconfig")).isInstanceOf(ConfigValue.ObjectValue.class);
        assertEquals(1, config.get("myconfig.myinnerconfig.myproperty").asNumber());
    }
}
