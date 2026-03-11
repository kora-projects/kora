package io.koraframework.grpc.client.ksp

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
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.TagUtils.addTag
import io.koraframework.ksp.common.generatedClassName

class GrpcClientStubForSymbolProcessor(val env: SymbolProcessorEnvironment) : BaseSymbolProcessor(env) {
    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(GrpcClassNames.stubFor.canonicalName)
        val invalid = arrayListOf<KSAnnotated>()
        for (symbol in symbols) {
            this.process(symbol)
        }
        return invalid
    }

    private fun process(symbol: KSAnnotated) {
        if (symbol !is KSClassDeclaration) {
            return
        }
        val parent = symbol.parentDeclaration
        if (parent !is KSClassDeclaration) {
            return
        }
        val tag = symbol.findAnnotation(GrpcClassNames.stubFor)!!.findValueNoDefault<KSType>("value")!!.declaration.qualifiedName!!.asString()

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

