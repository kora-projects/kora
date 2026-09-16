package io.koraframework.gradle.hint

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.nio.file.Files

@CacheableTask
abstract class MergeHintsTask : DefaultTask() {

    @get:Input
    abstract val projectPath: Property<String>

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:InputFiles
    abstract val hintFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val resultFile: RegularFileProperty

    @TaskAction
    fun run() {
        val mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build()
        val rootArray = mapper.createArrayNode()
        val targetFile = resultFile.get().asFile
        val allResolvedFiles = mutableListOf<File>()

        hintFiles.files.forEach { entry ->
            if (entry.isFile) {
                if (entry.name == "kora-module-hints.json" || entry.name == "kora-hints.json") {
                    allResolvedFiles.add(entry)
                }
            } else if (entry.isDirectory) {
                entry.walkTopDown().filter { it.isFile && it.name == "kora-module-hints.json" }.forEach {
                    allResolvedFiles.add(it)
                }
            }
        }

        var filesProcessed = 0

        allResolvedFiles.forEach { file ->
            filesProcessed++
            val node = mapper.readTree(file)
            if (node.isArray) {
                node.forEach { rootArray.add(it) }
            } else {
                rootArray.add(node)
            }
        }

        Files.createDirectories(targetFile.parentFile.toPath())
        val jsonString = mapper.writeValueAsString(rootArray)
        targetFile.writeText(jsonString)
    }
}
