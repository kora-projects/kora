package ru.tinkoff.kora.config.common;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.tinkoff.kora.config.common.factory.MapConfigFactory.fromMap;

public class RelaxedNamesTest {
    @Test
    void testRelaxedNames() {
        assertThat(fromMap(Map.of("someTestFieldWithCAPSAnd42Numbers", "test-value")).get("someTestFieldWithCAPSAnd42Numbers").asString()).isEqualTo("test-value");
        assertThat(fromMap(Map.of("some-test-field-with-caps-and-42-numbers", "test-value")).get("someTestFieldWithCAPSAnd42Numbers").asString()).isEqualTo("test-value");
        assertThat(fromMap(Map.of("some_test_field_with_caps_and_42_numbers", "test-value")).get("someTestFieldWithCAPSAnd42Numbers").asString()).isEqualTo("test-value");
    }
}
