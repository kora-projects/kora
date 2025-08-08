package ru.tinkoff.kora.http.client.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.declarativeHttpClientConfig
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientOperationConfig
import ru.tinkoff.kora.ksp.common.CommonClassNames.configValueExtractorAnnotation
import ru.tinkoff.kora.ksp.common.KspCommonUtils.addOriginatingKSFile
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated

class ConfigClassGenerator {
    fun generate(declaration: KSClassDeclaration): TypeSpec {
        val functions = declaration.getAllFunctions()
            .filter { f -> f.isAbstract }
            .map { it.simpleName.asString() }

        val typeName = declaration.configName()

        val tb = TypeSpec.interfaceBuilder(typeName)
            .addOriginatingKSFile(declaration)
            .generated(HttpClientSymbolProcessor::class)
            .addSuperinterface(declarativeHttpClientConfig)
            .addAnnotation(configValueExtractorAnnotation)

        tb.addFunction(FunSpec.builder("telemetry")
            .addModifiers(KModifier.ABSTRACT, KModifier.OVERRIDE)
            .returns(HttpClientClassNames.telemetryHttpClientConfig)
            .build())

        functions.forEach { function ->
            tb.addFunction(FunSpec.builder(function)
                .returns(httpClientOperationConfig)
                .addModifiers(KModifier.ABSTRACT)
                .build())
        }
        return tb.build()
    }
}
