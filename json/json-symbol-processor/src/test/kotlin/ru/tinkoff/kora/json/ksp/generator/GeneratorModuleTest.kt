package ru.tinkoff.kora.json.ksp.generator

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.ksp.GeneratorModuleProcessorProvider
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import java.lang.reflect.ParameterizedType

class GeneratorModuleTest : AbstractSymbolProcessorTest() {
    @Test
    fun test() {
        compile0(listOf(GeneratorModuleProcessorProvider()), """
            data class ExternalRecord1(val key: ExternalRecord2)
            """.trimIndent(), """
            data class ExternalRecord2(val key: String)
            """.trimIndent(), """
            @ru.tinkoff.kora.common.annotation.GeneratorModule(
                generator = ru.tinkoff.kora.json.common.annotation.Json::class,
                types = [ExternalRecord2::class, ExternalRecord1::class]
            )
            interface JsonGenerator {}
            """.trimIndent())
        compileResult.assertSuccess()

        val reader0 = loadClass("\$JsonGenerator_GeneratorModule_0_JsonReader")
        loadClass("\$JsonGenerator_GeneratorModule_1_JsonReader")
        loadClass("\$JsonGenerator_GeneratorModule_0_JsonWriter")
        loadClass("\$JsonGenerator_GeneratorModule_1_JsonWriter")

        val reader0Interface = reader0.genericInterfaces[0] as ParameterizedType

        Assertions.assertThat(reader0Interface.actualTypeArguments[0])
            .isEqualTo(loadClass("ExternalRecord2"))
    }
}
