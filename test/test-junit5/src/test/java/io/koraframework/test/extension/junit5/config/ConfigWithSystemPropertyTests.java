package io.koraframework.test.extension.junit5.config;

import org.junit.jupiter.api.Test;
import io.koraframework.config.common.Config;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.KoraAppTestConfigModifier;
import io.koraframework.test.extension.junit5.KoraConfigModification;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestConfigApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestConfigApplication.class)
public class ConfigWithSystemPropertyTests implements KoraAppTestConfigModifier {
    @TestComponent
    Config config;

    @Override
    public KoraConfigModification config() {
        return KoraConfigModification.ofSystemProperty("one", "1")
            .withSystemProperty("two", "2");
    }

    @Test
    void parameterConfigFromMethodInjected() {
        assertEquals(1L, config.get("one").asNumber().longValue());
        assertEquals(2L, config.get("two").asNumber().longValue());
    }
}
