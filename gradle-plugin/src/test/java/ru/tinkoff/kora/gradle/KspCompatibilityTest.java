package ru.tinkoff.kora.gradle;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KspCompatibilityTest {
    @Test
    void passesWhenVersionsMatch() {
        assertThatCode(() -> KspCompatibility.validate("1.9.25", "1.9.25")).doesNotThrowAnyException();
    }

    @Test
    void failsWithActionableMessageOnMismatch() {
        assertThatThrownBy(() -> KspCompatibility.validate("2.0.0", "1.9.25"))
            .isInstanceOf(GradleException.class)
            .hasMessageContaining("1.9.25")
            .hasMessageContaining("2.0.0");
    }
}
