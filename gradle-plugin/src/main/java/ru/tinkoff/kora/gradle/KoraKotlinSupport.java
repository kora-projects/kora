package ru.tinkoff.kora.gradle;

import org.gradle.api.Project;
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension;
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapperKt;

import java.util.List;

/**
 * Kotlin-side wiring: validates the applied Kotlin version against the bundled KSP, applies the KSP
 * plugin, puts the Kora symbol processor on {@code ksp}/{@code kspTest}, wires the Kora BOM onto the
 * compile/KSP classpaths, and registers the KSP-generated source directories for {@code main} and
 * {@code test} so both the compiler and the IDE resolve generated code.
 */
final class KoraKotlinSupport {
    static final List<String> KOTLIN_BOM_CONFIGURATIONS = List.of(
        "ksp", "kspTest", "compileOnly", "implementation", "api");
    private static final String PROCESSOR = "ru.tinkoff.kora:symbol-processors";
    private static final String KSP_PLUGIN_ID = "com.google.devtools.ksp";

    private KoraKotlinSupport() {
    }

    static void apply(Project project, KoraPluginVersions versions) {
        String appliedKotlin = KotlinPluginWrapperKt.getKotlinPluginVersion(project);
        KspCompatibility.validate(appliedKotlin, versions.kotlinVersion());

        project.getPluginManager().apply(KSP_PLUGIN_ID);

        KoraBomSupport.wire(project, versions.koraVersion(), KOTLIN_BOM_CONFIGURATIONS);

        project.getDependencies().add("ksp", PROCESSOR);
        project.getDependencies().add("kspTest", PROCESSOR);

        KotlinJvmProjectExtension kotlin =
            project.getExtensions().getByType(KotlinJvmProjectExtension.class);
        kotlin.getSourceSets().getByName("main").getKotlin()
            .srcDir(project.getLayout().getBuildDirectory().dir("generated/ksp/main/kotlin"));
        kotlin.getSourceSets().getByName("test").getKotlin()
            .srcDir(project.getLayout().getBuildDirectory().dir("generated/ksp/test/kotlin"));
    }
}
