package io.koraframework.s3.client.kora.symbol.processor.gen

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpec.Companion.interfaceBuilder
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.TagUtils.addTag
import io.koraframework.ksp.common.TagUtils.toTagAnnotation
import io.koraframework.ksp.common.generatedClassName
import io.koraframework.ksp.common.nestedIntoInterface
import io.koraframework.s3.client.kora.symbol.processor.S3ClientUtils
import io.koraframework.s3.client.kora.symbol.processor.S3ClassNames


object ModuleGenerator {
    /**
     * Client and buckets config are written as nested classes of the module, the same way java annotation processor does it
     */
    fun generate(s3client: KSClassDeclaration, client: TypeSpec, bucketsConfig: TypeSpec?): TypeSpec {
        // functions of the module are named as <client>_<Role>
        val methodPrefix = s3client.simpleName.asString().replaceFirstChar { it.lowercaseChar() }
        val packageName = s3client.packageName.asString()
        val bucketsType = ClassName(packageName, s3client.generatedClassName("Module"), "BucketsConfig")
        val b: TypeSpec.Builder = interfaceBuilder(s3client.generatedClassName("Module"))
            .generated(ModuleGenerator::class)
            .addAnnotation(CommonClassNames.module)

        val paths = S3ClientUtils.parseConfigBuckets(s3client)
        if (!paths.isEmpty()) {
            b.addFunction(
                FunSpec.builder(methodPrefix + "_BucketsConfig")
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

        val s3ClientAnnotation = s3client.findAnnotation(S3ClassNames.Annotation.client)
        var s3ClientConfigPath = s3ClientAnnotation?.findValueNoDefault<String>("value")

        if (s3ClientConfigPath.isNullOrEmpty()) {
            s3ClientConfigPath = s3client.simpleName.asString()
        }
        b.addFunction(
            // config is tagged with the client interface: every client has its own config of the same type
            FunSpec.builder(methodPrefix + "_Config")
                .addAnnotation(s3client.toClassName().toTagAnnotation())
                .returns(configType)
                .addParameter("config", CommonClassNames.config)
                .addParameter("mapper", CommonClassNames.configValueMapper.parameterizedBy(configType))
                .addStatement("return mapper.mapOrThrow(config.get(%S))", s3ClientConfigPath)
                .build()
        )
        val factoryTag = s3ClientAnnotation?.findValueNoDefault<KSType>("factoryTag")
        // low level client is a component tagged with the client interface, so it is created by the graph and not by the client constructor
        b.addFunction(
            FunSpec.builder(methodPrefix + "_Client")
                .addAnnotation(s3client.toClassName().toTagAnnotation())
                .returns(S3ClassNames.client)
                .addParameter(
                    ParameterSpec.builder("clientFactory", S3ClassNames.clientFactory)
                        .addTag(factoryTag?.toTypeName())
                        .build()
                )
                .addParameter(
                    ParameterSpec.builder("clientConfig", configType)
                        .addTag(s3client.toClassName())
                        .build()
                )
                .addStatement("return clientFactory.create(%S, %T::class.java, clientConfig)", s3ClientConfigPath, s3client.toClassName())
                .build()
        )

        b.addType(client.nestedIntoInterface())
        if (bucketsConfig != null) {
            b.addType(bucketsConfig.nestedIntoInterface())
        }

        return b.build()
    }
}
