package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.validation.symbol.processor.ValidTypes.VALID_TYPE

class ValidSymbolProcessor(environment: SymbolProcessorEnvironment) : BaseSymbolProcessor(environment) {

    private val gen = ValidatorGenerator(environment.codeGenerator)

    data class ValidatorSpec(val meta: ValidatorMeta, val spec: TypeSpec, val parameterSpecs: List<ParameterSpec>)

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(VALID_TYPE.canonicalName).toList()
        for (symbol in symbols) {
            if (symbol.validate()) {
                gen.generate(symbol)
            }
        }

        return symbols.filterNot { it.validate() }.toList()
    }
}
