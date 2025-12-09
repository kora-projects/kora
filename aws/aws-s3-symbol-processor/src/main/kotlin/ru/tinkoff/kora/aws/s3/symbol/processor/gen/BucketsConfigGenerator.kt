package ru.tinkoff.kora.aws.s3.symbol.processor.gen

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import ru.tinkoff.kora.aws.s3.symbol.processor.S3ClassNames
import ru.tinkoff.kora.aws.s3.symbol.processor.S3ClientUtils
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.generatedClassName


object BucketsConfigGenerator {
    fun generate(s3client: KSClassDeclaration): TypeSpec? {
        val paths = S3ClientUtils.parseConfigBuckets(s3client)
        if (paths.isEmpty()) {
            return null
        }
        val packageName = s3client.packageName.asString()
        val configType = ClassName(packageName, s3client.generatedClassName("BucketsConfig"))
        val b = TypeSpec.classBuilder(configType)
            .generated(BucketsConfigGenerator::class)
        val constructor = FunSpec.constructorBuilder()
            .addModifiers(KModifier.PRIVATE)
        val primaryConstructorArgs = mutableListOf<CodeBlock>()
        for (i in paths.indices) {
            var path = paths[i]
            if (path.startsWith(".")) {
                val annotation = s3client.findAnnotation(S3ClassNames.Annotation.client)
                var annotationValue = annotation?.findValueNoDefault<String>("value")
                if (annotationValue == null) {
                    annotationValue = s3client.simpleName.asString()
                }
                path = annotationValue + path
            }
            b.addProperty(
                PropertySpec.builder("bucket_$i", String::class)
                    .initializer("bucket_$i")
                    .build()
            )
            constructor.addParameter("bucket_$i", String::class)
            primaryConstructorArgs.add(CodeBlock.of("config.get(%S).asString()", path))
        }
        b.primaryConstructor(constructor.build())
        b.addFunction(
            FunSpec.constructorBuilder()
                .addParameter("config", CommonClassNames.config)
                .callThisConstructor(primaryConstructorArgs)
                .build()
        )
        return b.build()
    }

}
