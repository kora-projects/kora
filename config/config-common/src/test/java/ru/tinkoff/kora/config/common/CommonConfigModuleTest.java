package ru.tinkoff.kora.config.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class CommonConfigModuleTest {
    @Test
    void testSystemProperties() {
        Assertions.assertNotNull(new CommonConfigModule() {}.systemProperties());
        System.out.println(new CommonConfigModule() {}.systemProperties());
    }
}
