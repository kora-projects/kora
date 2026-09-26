plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    api(projects.core.applicationGraph)
    api(projects.telemetry.micrometerCommon)
    api(libs.slf4j.api)
    api(libs.opentelemetry.api)
}

abstract class GenerateKoraVersionTask : DefaultTask() {
    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val projectVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val name = projectName.get()
        val version = projectVersion.get()
        val targetFile = outputDir.get().file("META-INF/kora/version/$name").asFile

        targetFile.parentFile.mkdirs()
        if (targetFile.exists()) {
            targetFile.delete()
        }
        targetFile.createNewFile()
        targetFile.writeText(version)
    }
}

val generateKoraVersion = tasks.register<GenerateKoraVersionTask>("generateKoraVersion") {
    projectName.set(project.name)
    projectVersion.set(project.provider { project.version.toString() })
    outputDir.set(layout.buildDirectory.dir("kora-version"))
}

sourceSets {
    main {
        resources {
            srcDir(generateKoraVersion.map { it.outputDir })
        }
    }
}

tasks.withType<Jar>().configureEach {
    if (name == "sourcesJar") {
        dependsOn(generateKoraVersion)
    }
}
