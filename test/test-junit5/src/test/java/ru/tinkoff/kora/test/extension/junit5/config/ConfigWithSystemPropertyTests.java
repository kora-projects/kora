package ru.tinkoff.kora.test.extension.junit5.config;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestConfigModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraConfigModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestConfigApplication;

import javax.annotation.Nonnull;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestConfigApplication.class)
public class ConfigWithSystemPropertyTests implements KoraAppTestConfigModifier {
    @TestComponent
    Config config;

    @Override
    public @Nonnull KoraConfigModification config() {
        return KoraConfigModification.ofSystemProperty("one", "1")
            .withSystemProperty("two", "2");
    }

    @Test
    void parameterConfigFromMethodInjected() {
        assertEquals(1L, config.get("one").asNumber().longValue());
        assertEquals(2L, config.get("two").asNumber().longValue());
    }
}
