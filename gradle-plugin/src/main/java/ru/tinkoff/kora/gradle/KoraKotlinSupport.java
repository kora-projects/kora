package ru.tinkoff.kora.gradle;

import org.gradle.api.Project;
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension;

import java.util.List;

/**
 * Kotlin-side wiring. The consumer applies the KSP plugin themselves (choosing the version that
 * matches their Kotlin), and this plugin does the rest: wires the Kora BOM onto the compile and KSP
 * classpaths, puts the Kora symbol processor on {@code ksp}/{@code kspTest}, and registers the
 * KSP-generated source directories. A warning is emitted if KSP is missing.
 */
final class KoraKotlinSupport {
    static final List<String> KOTLIN_BOM_CONFIGURATIONS = List.of("compileOnly", "implementation", "api");
    static final List<String> KSP_BOM_CONFIGURATIONS = List.of("ksp", "kspTest");
    private static final String PROCESSOR = "ru.tinkoff.kora:symbol-processors";
    private static final String KSP_PLUGIN_ID = "com.google.devtools.ksp";

    private KoraKotlinSupport() {
    }

    static void apply(Project project, KoraPluginVersions versions) {
        KoraBomSupport.wire(project, versions.koraVersion(), KOTLIN_BOM_CONFIGURATIONS);
        registerGeneratedSourceDirs(project);

        // KSP is applied by the consumer (so they pick the version matching their Kotlin). When they
        // do, add the Kora symbol processor and wire the BOM onto the KSP classpaths.
        project.getPluginManager().withPlugin(KSP_PLUGIN_ID, applied -> {
            KoraBomSupport.wire(project, versions.koraVersion(), KSP_BOM_CONFIGURATIONS);
            project.getDependencies().add("ksp", PROCESSOR);
            project.getDependencies().add("kspTest", PROCESSOR);
        });

        project.afterEvaluate(evaluated -> {
            if (!evaluated.getPluginManager().hasPlugin(KSP_PLUGIN_ID)) {
                evaluated.getLogger().warn(
                    "Kora: the Kotlin symbol processor requires KSP, but the '{}' plugin is not applied. "
                        + "Add it with a version that matches your Kotlin version, e.g. "
                        + "id(\"com.google.devtools.ksp\") version \"<kotlin>-<ksp>\".", KSP_PLUGIN_ID);
            }
        });
    }

    private static void registerGeneratedSourceDirs(Project project) {
        KotlinJvmProjectExtension kotlin =
            project.getExtensions().getByType(KotlinJvmProjectExtension.class);
        kotlin.getSourceSets().getByName("main").getKotlin()
            .srcDir(project.getLayout().getBuildDirectory().dir("generated/ksp/main/kotlin"));
        kotlin.getSourceSets().getByName("test").getKotlin()
            .srcDir(project.getLayout().getBuildDirectory().dir("generated/ksp/test/kotlin"));
    }
}
