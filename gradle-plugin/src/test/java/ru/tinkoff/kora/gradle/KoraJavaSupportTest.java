package ru.tinkoff.kora.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoraJavaSupportTest {
    private Project javaProject() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("ru.tinkoff.kora");
        project.getPluginManager().apply("java");
        return project;
    }

    @Test
    void addsAnnotationProcessorToMainAndTest() {
        Project project = javaProject();
        assertThat(project.getConfigurations().getByName("annotationProcessor").getDependencies())
            .anySatisfy(d -> assertThat(d.getName()).isEqualTo("annotation-processors"));
        assertThat(project.getConfigurations().getByName("testAnnotationProcessor").getDependencies())
            .anySatisfy(d -> assertThat(d.getName()).isEqualTo("annotation-processors"));
    }

    @Test
    void wiresBomOntoProcessorClasspath() {
        Project project = javaProject();
        Configuration bom = project.getConfigurations().getByName("koraBom");
        assertThat(project.getConfigurations().getByName("annotationProcessor").getExtendsFrom()).contains(bom);
    }

    @Test
    void setsUtf8Encoding() {
        Project project = javaProject();
        JavaCompile compile = (JavaCompile) project.getTasks().getByName("compileJava");
        assertThat(compile.getOptions().getEncoding()).isEqualTo("UTF-8");
    }

    @Test
    void doesNotForceForkedCompilation() {
        // --add-exports proved unnecessary for the Kora processor on JDK 17 (Phase 6 spike),
        // so the plugin must not force forked javac.
        Project project = javaProject();
        JavaCompile compile = (JavaCompile) project.getTasks().getByName("compileJava");
        assertThat(compile.getOptions().isFork()).isFalse();
    }
}
