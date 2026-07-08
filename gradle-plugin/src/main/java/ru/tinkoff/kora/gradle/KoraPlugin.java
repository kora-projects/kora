package ru.tinkoff.kora.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Kora Gradle plugin (id {@code ru.tinkoff.kora}). Pure build plumbing: it reacts to whichever
 * language plugin is applied and wires the Kora BOM, annotation processor / KSP, generated
 * sources and (for Java) the compiler {@code --add-exports}. No user-facing DSL.
 */
public class KoraPlugin implements Plugin<Project> {

    public static final String KOTLIN_JVM_PLUGIN_ID = "org.jetbrains.kotlin.jvm";

    @Override
    public void apply(Project project) {
        KoraPluginVersions versions = KoraPluginVersions.load();

        project.getPluginManager().withPlugin("java", applied ->
            KoraJavaSupport.apply(project, versions));

        project.getPluginManager().withPlugin(KOTLIN_JVM_PLUGIN_ID, applied ->
            KoraKotlinSupport.apply(project, versions));
    }
}
