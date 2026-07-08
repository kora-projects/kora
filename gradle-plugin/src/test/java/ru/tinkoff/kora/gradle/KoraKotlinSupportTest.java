package ru.tinkoff.kora.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoraKotlinSupportTest {
    private Project kotlinProject() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("ru.tinkoff.kora");
        project.getPluginManager().apply("org.jetbrains.kotlin.jvm");
        return project;
    }

    @Test
    void appliesKspPlugin() {
        assertThat(kotlinProject().getPlugins().hasPlugin("com.google.devtools.ksp")).isTrue();
    }

    @Test
    void addsSymbolProcessorToMainAndTest() {
        Project project = kotlinProject();
        assertThat(project.getConfigurations().getByName("ksp").getDependencies())
            .anySatisfy(d -> assertThat(d.getName()).isEqualTo("symbol-processors"));
        assertThat(project.getConfigurations().getByName("kspTest").getDependencies())
            .anySatisfy(d -> assertThat(d.getName()).isEqualTo("symbol-processors"));
    }

    @Test
    void wiresBomOntoKspClasspath() {
        Project project = kotlinProject();
        Configuration bom = project.getConfigurations().getByName("koraBom");
        assertThat(project.getConfigurations().getByName("ksp").getExtendsFrom()).contains(bom);
    }
}
