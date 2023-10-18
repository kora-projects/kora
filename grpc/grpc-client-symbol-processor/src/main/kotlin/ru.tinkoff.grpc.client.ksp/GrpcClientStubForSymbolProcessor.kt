package ru.tinkoff.grpc.client.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.addOriginatingKSFile
import ru.tinkoff.kora.ksp.common.TagUtils.addTag
import ru.tinkoff.kora.ksp.common.generatedClassName

class GrpcClientStubForSymbolProcessor(val env: SymbolProcessorEnvironment) : BaseSymbolProcessor(env) {
    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(GrpcClassNames.stubFor.canonicalName)
        val invalid = arrayListOf<KSAnnotated>()
        for (symbol in symbols) {
            this.process(resolver, symbol)
        }
        return invalid
    }

    private fun process(resolver: Resolver, symbol: KSAnnotated) {
        if (symbol !is KSClassDeclaration) {
            return
        }
        val parent = symbol.parentDeclaration
        if (parent !is KSClassDeclaration) {
            return
        }
        val tag = setOf(symbol.findAnnotation(GrpcClassNames.stubFor)!!.findValueNoDefault<KSType>("value")!!
            .toClassName()
            .canonicalName
        )

        val type = symbol.toClassName()

        val module = TypeSpec.interfaceBuilder(parent.generatedClassName("_GrpcModule"))
            .addOriginatingKSFile(parent)
            .addAnnotation(CommonClassNames.module)
            .addFunction(FunSpec.builder(parent.simpleName.asString() + "_client")
                .addAnnotation(CommonClassNames.defaultComponent)
                .returns(type)
                .addParameter(ParameterSpec.builder("channel", GrpcClassNames.channel).addTag(tag).build())
                .addStatement("return %T(channel)", type)
                .build()
            )
            .build()

        FileSpec.builder(parent.packageName.asString(), module.name!!)
            .addType(module)
            .build()
            .writeTo(env.codeGenerator, false)
    }
}

