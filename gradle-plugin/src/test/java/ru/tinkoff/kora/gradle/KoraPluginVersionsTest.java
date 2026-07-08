package ru.tinkoff.kora.gradle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoraPluginVersionsTest {
    @Test
    void loadsBakedVersions() {
        KoraPluginVersions v = KoraPluginVersions.load();
        assertThat(v.koraVersion()).isNotBlank();
        assertThat(v.kspVersion()).contains("-");            // e.g. 1.9.25-1.0.20
        assertThat(v.kotlinVersion()).matches("\\d+\\.\\d+\\.\\d+");
    }
}
