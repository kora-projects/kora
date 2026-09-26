package io.koraframework.gradle.soap

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@CacheableTask
abstract class CxfGenTask : DefaultTask() {

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Classpath
    @get:InputFiles
    abstract val cxfClasspath: ConfigurableFileCollection

    @TaskAction
    fun execute() {
        val targetDir = outputDir.get().asFile
        targetDir.deleteRecursively()
        targetDir.mkdirs()

        val queue = workerExecutor.classLoaderIsolation {
            classpath.from(cxfClasspath)
        }

        queue.submit(CxfRunnable::class.java) {
            args.set(
                listOf(
                    "-d", targetDir.absolutePath,
                    "-autoNameResolution",
                    "-verbose",
                    inputFile.get().asFile.absolutePath
                )
            )
        }
        queue.await()
    }
}
