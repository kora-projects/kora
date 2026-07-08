package ru.tinkoff.kora.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KoraBomSupportTest {
    @Test
    void createsBomConfigurationAndExtendsTargets() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");

        KoraBomSupport.wire(project, "1.2.12", List.of("implementation", "annotationProcessor"));

        Configuration bom = project.getConfigurations().getByName("koraBom");
        assertThat(bom.getDependencies())
            .anySatisfy(d -> assertThat(d.getName()).isEqualTo("kora-parent"));
        assertThat(project.getConfigurations().getByName("implementation").getExtendsFrom()).contains(bom);
        assertThat(project.getConfigurations().getByName("annotationProcessor").getExtendsFrom()).contains(bom);
    }

    @Test
    void wiringTwiceDoesNotDuplicateBom() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        KoraBomSupport.wire(project, "1.2.12", List.of("implementation"));
        KoraBomSupport.wire(project, "1.2.12", List.of("api"));
        assertThat(project.getConfigurations().getByName("koraBom").getDependencies()).hasSize(1);
    }

    @Test
    void wiresConfigurationCreatedAfterWireCall() {
        // Reproduces the java-library race: 'api' is created only after wire() runs.
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        KoraBomSupport.wire(project, "1.2.12", List.of("api"));   // 'api' does not exist yet
        project.getPluginManager().apply("java-library");         // now 'api' is created

        Configuration bom = project.getConfigurations().getByName("koraBom");
        assertThat(project.getConfigurations().getByName("api").getExtendsFrom()).contains(bom);
    }
}
