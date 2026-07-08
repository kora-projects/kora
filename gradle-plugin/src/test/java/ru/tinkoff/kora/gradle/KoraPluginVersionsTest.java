package ru.tinkoff.kora.gradle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoraPluginVersionsTest {
    @Test
    void loadsBakedKoraVersion() {
        KoraPluginVersions v = KoraPluginVersions.load();
        assertThat(v.koraVersion()).isNotBlank();
    }
}
