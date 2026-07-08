package ru.tinkoff.kora.gradle;

import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.List;

/**
 * Java-side wiring: the Kora BOM on the compile/processor classpaths, the annotation processor on
 * {@code annotationProcessor}/{@code testAnnotationProcessor}, and UTF-8 source encoding.
 *
 * <p>Note on {@code --add-exports}: the Kora examples historically carry a block of
 * {@code --add-exports jdk.compiler/com.sun.tools.javac.*} flags. A static search of the Kora
 * sources found no reference to those internals, and an empirical compile of a real {@code @KoraApp}
 * on JDK&nbsp;17 with no flags and in-process {@code javac} succeeds — so the plugin deliberately
 * does <em>not</em> add them and does not force forked compilation.
 */
final class KoraJavaSupport {
    static final List<String> JAVA_BOM_CONFIGURATIONS = List.of(
        "annotationProcessor", "testAnnotationProcessor", "compileOnly", "implementation", "api");
    private static final String PROCESSOR = "ru.tinkoff.kora:annotation-processors";

    private KoraJavaSupport() {
    }

    static void apply(Project project, KoraPluginVersions versions) {
        KoraBomSupport.wire(project, versions.koraVersion(), JAVA_BOM_CONFIGURATIONS);

        project.getDependencies().add("annotationProcessor", PROCESSOR);
        project.getDependencies().add("testAnnotationProcessor", PROCESSOR);

        project.getTasks().withType(JavaCompile.class).configureEach(compile ->
            compile.getOptions().setEncoding("UTF-8"));
    }
}
