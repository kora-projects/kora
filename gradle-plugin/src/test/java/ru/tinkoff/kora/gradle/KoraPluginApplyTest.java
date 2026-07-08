package ru.tinkoff.kora.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class KoraPluginApplyTest {
    @Test
    void appliesCleanlyWithoutAnyLanguagePlugin() {
        Project project = ProjectBuilder.builder().build();
        assertThatCode(() -> project.getPluginManager().apply("ru.tinkoff.kora"))
            .doesNotThrowAnyException();
        assertThat(project.getPlugins().hasPlugin("ru.tinkoff.kora")).isTrue();
    }
}
