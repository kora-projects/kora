package io.koraframework.test.extension.junit5.config;

import org.junit.jupiter.api.Test;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.KoraAppTestConfigModifier;
import io.koraframework.test.extension.junit5.KoraConfigModification;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestConfigApplication;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestConfigApplication.class)
public class ConfigWithRawTests implements KoraAppTestConfigModifier {

    @Override
    public KoraConfigModification config() {
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
