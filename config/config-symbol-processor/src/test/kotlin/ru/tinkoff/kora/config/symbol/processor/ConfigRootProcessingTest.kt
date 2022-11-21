@file:OptIn(KspExperimental::class)

package ru.tinkoff.kora.config.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.config.ksp.processor.ConfigRootSymbolProcessorProvider
import ru.tinkoff.kora.config.symbol.processor.cases.PojoConfigRoot
import ru.tinkoff.kora.config.symbol.processor.cases.RecordConfigRoot
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.lang.reflect.Method
import kotlin.reflect.KClass

internal class ConfigRootProcessingTest {

    @Test
    @Throws(Exception::class)
    fun testPojoRootConfig() {
        val module = createModule(PojoConfigRoot::class)
        val methods = module.declaredMethods
            .map { method: Method ->
                val tagsPrefix = tagsPrefix(method)
                tagsPrefix + method.name + ": " + method.returnType.canonicalName
            }
            .filter { isNotJacocoInit(it) }
            .toList()
        Assertions.assertThat(methods).containsOnly(
            "pojoConfigRoot: ru.tinkoff.kora.config.symbol.processor.cases.PojoConfigRoot",
            "@ClassConfig pojoConfigValue: ru.tinkoff.kora.config.symbol.processor.cases.ClassConfig",
            "@DataClassConfig recConfigValue: ru.tinkoff.kora.config.symbol.processor.cases.DataClassConfig"
        )
    }

    @Test
    @Throws(Exception::class)
    fun testRecordRootConfig() {
        val module = createModule(RecordConfigRoot::class)
        val methods = module.declaredMethods
            .map { method: Method ->
                val tagsPrefix = tagsPrefix(method)
                tagsPrefix + method.name + ": " + method.returnType.canonicalName
            }
            .filter { isNotJacocoInit(it) }
            .toList()
        Assertions.assertThat(methods).containsOnly(
            "recordConfigRoot: ru.tinkoff.kora.config.symbol.processor.cases.RecordConfigRoot",
            "pojoConfigValue: ru.tinkoff.kora.config.symbol.processor.cases.ClassConfig",
            "@DataClassConfig recConfigValue: ru.tinkoff.kora.config.symbol.processor.cases.DataClassConfig"
        )
    }

    @Throws(Exception::class)
    private fun createModule(targetClass: KClass<*>): Class<*> {
        return try {
            val classLoader = symbolProcess(targetClass, ConfigRootSymbolProcessorProvider())
            classLoader.loadClass(targetClass.qualifiedName!! + "Module")
        } catch (e: Exception) {
            if (e.cause != null) {
                throw (e.cause as Exception?)!!
            }
            throw e
        }
    }

    private fun tagsPrefix(method: Method): String {
        val tag = method.getAnnotation(Tag::class.java) ?: return ""
        return tag.value
            .map { obj -> obj.simpleName }
            .joinToString(",", "@", " ")
    }

    // will be added by jacoco agent if build runs with coverage report generation
    private fun isNotJacocoInit(name: String): Boolean {
        return "\$jacocoInit: boolean[]" != name
    }
}
