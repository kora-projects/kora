package io.koraframework.database.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcEnumValueMappingStrategyTest {

    @Test
    void nameKeepsAsIs() {
        assertThat(JdbcEnumValueMappingStrategy.asIs().map("ACTIVE")).isEqualTo("ACTIVE");
    }

    @Test
    void lowerCasing() {
        assertThat(JdbcEnumValueMappingStrategy.lowerCasing().map("ACTIVE")).isEqualTo("active");
    }

    @Test
    void upperCasing() {
        assertThat(JdbcEnumValueMappingStrategy.upperCasing().map("active")).isEqualTo("ACTIVE");
    }
}
