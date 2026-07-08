package ru.tinkoff.kora.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import java.util.List;
import java.util.Set;

/**
 * Creates the synthetic {@code koraBom} configuration holding the {@code kora-parent} platform
 * and makes the given configurations extend it, so Kora module versions can be omitted — including
 * on the annotation-processor / KSP classpaths, which do not inherit it otherwise.
 *
 * <p>Target configurations are matched lazily via {@code configureEach}: a name that does not exist
 * (e.g. {@code api} without the {@code java-library} plugin) is simply skipped, and a name created
 * later (e.g. {@code api} created by {@code java-library} after our {@code withPlugin("java")}
 * callback fires) is still wired when it appears.
 */
final class KoraBomSupport {
    static final String BOM_CONFIGURATION = "koraBom";
    private static final String BOM_COORDINATES = "ru.tinkoff.kora:kora-parent:";

    private KoraBomSupport() {
    }

    static void wire(Project project, String koraVersion, List<String> configurationNames) {
        Configuration bom = project.getConfigurations().findByName(BOM_CONFIGURATION);
        if (bom == null) {
            bom = project.getConfigurations().create(BOM_CONFIGURATION, c -> {
                c.setCanBeResolved(false);
                c.setCanBeConsumed(false);
            });
            Object platform = project.getDependencies().platform(BOM_COORDINATES + koraVersion);
            project.getDependencies().add(BOM_CONFIGURATION, platform);
        }
        Configuration bomFinal = bom;
        Set<String> targets = Set.copyOf(configurationNames);
        project.getConfigurations().configureEach(c -> {
            if (targets.contains(c.getName())) {
                c.extendsFrom(bomFinal);
            }
        });
    }
}
