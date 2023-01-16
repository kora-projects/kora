package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import ru.tinkoff.kora.annotation.processor.common.symbolProcessorProviders
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.reflect.KClass
import kotlin.reflect.KType

object TestUtils {
    fun testKoraExtension(targetClasses: Array<KType>, vararg requiredDependencies: KType): ClassLoader? {
        var template: String = """
        package test;
                    
        @ru.tinkoff.kora.common.KoraApp
        public interface TestApp {
            fun someLifecycle({targets}): ru.tinkoff.kora.application.graph.Lifecycle  {
                return null!!
            }

    """.trimIndent()
        for (i in requiredDependencies.indices) {
            template += """  fun component$i() : ${requiredDependencies[i]} { return null!!; }
"""
        }
        template += "\n}"
        val sb = StringBuilder()
        for (i in targetClasses.indices) {
            if (i > 0) {
                sb.append(",\n  ")
            }
            sb.append("param").append(i).append(": ").append(targetClasses[i].toString())
        }
        val targets = sb.toString()
        val content = template.replace("\\{targets}".toRegex(), targets)
        val path = Path.of("build/in-test-generated/extension-test-dir/test/TestApp.kt")
        Files.deleteIfExists(path)
        Files.createDirectories(path.parent)
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE)
        val koraAppProcessor = Class.forName("ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider").getConstructor().newInstance() as SymbolProcessorProvider
        return symbolProcessFiles(listOf(path.toString()), listOf(koraAppProcessor))
    }
}

fun symbolProcess(targetClass: KClass<*>, vararg annotationProcessorProviders: SymbolProcessorProvider): ClassLoader {
    return symbolProcess(listOf(targetClass), *annotationProcessorProviders)
}

fun symbolProcessJava(targetClass: Class<*>, vararg annotationProcessorProviders: SymbolProcessorProvider): ClassLoader {
    return symbolProcessJava(listOf(targetClass), *annotationProcessorProviders)
}

fun symbolProcessJava(targetClasses: List<Class<*>>, vararg annotationProcessorProviders: SymbolProcessorProvider): ClassLoader {
    val srcFilesPath = targetClasses.map { targetClass ->
        "src/test/java/" + targetClass.canonicalName.replace(".", "/") + ".java"
    }
    return symbolProcessFiles(srcFilesPath, annotationProcessorProviders.toList(), true)
}

fun symbolProcess(targetClasses: List<KClass<*>>, vararg annotationProcessorProviders: SymbolProcessorProvider): ClassLoader {
    return symbolProcess(targetClasses, annotationProcessorProviders.toList())
}

fun symbolProcess(targetClasses: List<KClass<*>>, annotationProcessorProviders: List<SymbolProcessorProvider>): ClassLoader {
    val srcFilesPath = targetClasses.map { targetClass ->
        "src/test/kotlin/" + targetClass.qualifiedName!!.replace(".", "/") + ".kt"
    }
    return symbolProcessFiles(srcFilesPath, annotationProcessorProviders.toList())
}

fun symbolProcessFiles(files: List<String>, annotationProcessorProviders: List<SymbolProcessorProvider>, javaFile: Boolean = false): ClassLoader {
    val srcFiles = files.map { src ->
        val file = Path.of(src).toAbsolutePath().toFile()
        if (javaFile) {
            SourceFile.java(file.name, file.readText())
        } else {
            SourceFile.kotlin(file.name, file.readText())
        }
    }
    val compilation = KotlinCompilation().apply {
        jvmDefault = "all"
        jvmTarget = "17"
        verbose = false
        workingDir = Path.of("build/in-test-generated-ksp").toAbsolutePath().toFile()
        sources = srcFiles
        symbolProcessorProviders = annotationProcessorProviders
        inheritClassPath = true
        verbose = false
    }
    val result = compilation.compile()
    val messages = result.messages.split("\n")
    val errorMessages = mutableListOf<String>()
    val indexOfFirst = messages.indexOfFirst { it.startsWith("e: [ksp]") }
    if (indexOfFirst >= 0) {
        for (i in indexOfFirst until messages.size) {
            val message = messages[i]
            if (i == indexOfFirst + 1 && !message.startsWith("[")) break
            if (i != indexOfFirst && message.endsWith("]")) {
                errorMessages.add(message.replace("]", ""))
                break
            } else {
                errorMessages.add(
                    if (i != indexOfFirst) {
                        message.replace("[", "")
                    } else message
                )
            }
        }
    }
    if (result.exitCode != KotlinCompilation.ExitCode.OK) {
        throw CompilationErrorException(result.messages.split("\n"))
    }
    if (errorMessages.isNotEmpty()) {
        throw CompilationErrorException(errorMessages.map { it.replace(Regex("^.*:[0-9]*: "), "") })
    }
    return result.classLoader
}

data class CompilationErrorException(val messages: List<String>) : Exception()
