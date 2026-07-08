package io.koraframework.config.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class ConfigModuleTest {
    @Test
    void testSystemProperties() {
        Assertions.assertNotNull(new ConfigModule() {}.systemPropertiesConfig());
    }
}
