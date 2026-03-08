package io.koraframework.http.client.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import io.koraframework.http.client.symbol.processor.HttpClientClassNames.declarativeHttpClientConfig
import io.koraframework.http.client.symbol.processor.HttpClientClassNames.httpClientOperationConfig
import io.koraframework.ksp.common.CommonClassNames.configValueExtractorAnnotation
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated

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
