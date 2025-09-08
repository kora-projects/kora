package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import io.github.classgraph.ClassGraph
import org.junit.jupiter.api.fail
import java.nio.file.Path
import kotlin.reflect.KClass

object TestUtils {
    var classpath: List<String>

    init {
        val classGraph = ClassGraph()
            .enableSystemJarsAndModules()
            .removeTemporaryFilesAfterScan()

        val classpaths = classGraph.classpathFiles
        val modules = classGraph.modules
            .asSequence()
            .filterNotNull()
            .map { it.locationFile }

        classpath = (classpaths.asSequence() + modules)
            .filterNotNull()
            .map { it.toString() }
            .distinct()
            .toList()
    }

    sealed interface ProcessingResult {
        data class Success(val classLoader: ClassLoader) : ProcessingResult
        data class Failure(val messages: List<String>) : ProcessingResult

        fun assertSuccess(): Success {
            when (this) {
                is Failure -> throw messages.asCompilationException()
                is Success -> return this
            }
        }

        fun assertFailure(): Failure {
            if (this is Failure) {
                return this
            }
            fail { "Expected failure, got success" }
        }
    }

    fun List<String>.asCompilationException(): CompilationErrorException {
        val errorMessages = mutableListOf<String>()
        val builder = StringBuilder()
        for (message in this) {
            if (message.contains("error: [") || message.contains("warn: [") || message.contains("info: [") || message.contains("logging: [")) {
                errorMessages.add(builder.toString())
                builder.clear().append(message)
            } else {
                builder.append('\n').append(message)
            }
        }
        if (builder.isNotEmpty()) {
            errorMessages.add(builder.toString())
        }


        val indexOfFirst = this.indexOfFirst { it.contains("error: ") }
        if (indexOfFirst >= 0) {
            errorMessages.add(this[indexOfFirst])
            for (i in indexOfFirst + 1 until this.size) {
                val message = this[i]
                if (message.contains("error: [") || message.contains("warn: [") || message.contains("info: [") || message.contains("logging: [")) {
                    break
                } else {
                    errorMessages.add(message)
                }
            }
        }
        return CompilationErrorException(errorMessages)
    }
}


fun symbolProcess(processors: List<SymbolProcessorProvider>, targetClasses: List<KClass<*>>, params: Map<String, String> = mapOf()): ClassLoader {
    val srcFilesPath = targetClasses
        .map { targetClass ->
            "src/test/kotlin/" + targetClass.qualifiedName!!.replace(".", "/") + ".kt"
        }
        .map { Path.of(it) }
    return KotlinCompilation()
        .withProcessors(processors)
        .withSrc(srcFilesPath)
        .apply { processorsOptions.putAll(params) }
        .compile()
}
