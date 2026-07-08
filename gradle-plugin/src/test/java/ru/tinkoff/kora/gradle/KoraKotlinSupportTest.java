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

    private Project kotlinKspProject() {
        Project project = kotlinProject();
        project.getPluginManager().apply("com.google.devtools.ksp");
        return project;
    }

    @Test
    void doesNotApplyKspItself() {
        // The consumer applies KSP (choosing the version); the plugin must not apply it for them.
        assertThat(kotlinProject().getPlugins().hasPlugin("com.google.devtools.ksp")).isFalse();
    }

    @Test
    void addsSymbolProcessorWhenKspApplied() {
        Project project = kotlinKspProject();
        assertThat(project.getConfigurations().getByName("ksp").getDependencies())
            .anySatisfy(d -> assertThat(d.getName()).isEqualTo("symbol-processors"));
        assertThat(project.getConfigurations().getByName("kspTest").getDependencies())
            .anySatisfy(d -> assertThat(d.getName()).isEqualTo("symbol-processors"));
    }

    @Test
    void wiresBomOntoKspClasspath() {
        Project project = kotlinKspProject();
        Configuration bom = project.getConfigurations().getByName("koraBom");
        assertThat(project.getConfigurations().getByName("ksp").getExtendsFrom()).contains(bom);
    }

    @Test
    void wiresBomOntoKotlinCompileClasspath() {
        Project project = kotlinProject();
        Configuration bom = project.getConfigurations().getByName("koraBom");
        assertThat(project.getConfigurations().getByName("implementation").getExtendsFrom()).contains(bom);
    }
}
