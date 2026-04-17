package io.koraframework.s3.client.kora.symbol.processor.gen

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpec.Companion.interfaceBuilder
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.generatedClassName
import io.koraframework.s3.client.kora.symbol.processor.S3ClientUtils
import io.koraframework.s3.client.symbol.processor.S3ClassNames


object ModuleGenerator {
    fun generate(s3client: KSClassDeclaration): TypeSpec {
        val packageName = s3client.packageName.asString()
        val bucketsType = ClassName(packageName, s3client.generatedClassName("BucketsConfig"))
        val clientType = ClassName(packageName, s3client.generatedClassName("ClientImpl"))
        val b: TypeSpec.Builder = interfaceBuilder(s3client.generatedClassName("Module"))
            .generated(ModuleGenerator::class)
            .addAnnotation(CommonClassNames.module)

        val paths = S3ClientUtils.parseConfigBuckets(s3client)
        if (!paths.isEmpty()) {
            b.addFunction(
                FunSpec.builder("bucketsConfig")
                    .returns(bucketsType)
                    .addParameter("config", CommonClassNames.config)
                    .addStatement("return %T(config)", bucketsType)
                    .build()
            )
        }
        val credsRequired = s3client.getAllFunctions()
            .filter { it.isAbstract }
            .any { S3ClientUtils.credentialsParameter(it) == null }

        val configType = if (credsRequired)
            S3ClassNames.configWithCreds
        else
            S3ClassNames.config

        val s3ClientAnnotation = s3client.findAnnotation(S3ClassNames.client)
        var s3ClientConfigPath = s3ClientAnnotation?.findValueNoDefault<String>("value")

        if (s3ClientConfigPath.isNullOrEmpty()) {
            s3ClientConfigPath = s3client.simpleName.asString()
        }
        b.addFunction(
            FunSpec.builder("clientConfig")
                .returns(configType)
                .addParameter("config", CommonClassNames.config)
                .addParameter("extractor", CommonClassNames.configValueExtractor.parameterizedBy(configType))
                .addStatement("val configValue = config.get(%S)", s3ClientConfigPath)
                .addStatement("val parsed = extractor.extract(configValue)", s3ClientConfigPath)
                .controlFlow("if (parsed == null)") {
                    addStatement("throw %T.missingValueAfterParse(configValue)", CommonClassNames.configValueExtractionException)
                }
                .addStatement("return parsed")
                .build()
        )
        val clientImpl = FunSpec.builder("clientImpl")
            .returns(clientType)
            .addParameter("clientFactory", S3ClassNames.clientFactory)
            .addParameter("clientConfig", configType)
        if (paths.isEmpty()) {
            clientImpl
                .addStatement("return %T(clientFactory, clientConfig)", clientType)
        } else {
            clientImpl.addParameter("bucketsConfig", bucketsType)
                .addStatement("return %T(clientFactory, clientConfig, bucketsConfig)", clientType)
        }
        b.addFunction(clientImpl.build())

        return b.build()
    }

}
