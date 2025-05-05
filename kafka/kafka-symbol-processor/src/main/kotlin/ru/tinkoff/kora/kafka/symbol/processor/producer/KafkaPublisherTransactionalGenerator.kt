package ru.tinkoff.kora.kafka.symbol.processor.producer

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.CommonAopUtils.extendsKeepAop
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.generatedClassName
import java.util.*
import java.util.function.Function
import java.util.function.Supplier

class KafkaPublisherTransactionalGenerator(
    val env: SymbolProcessorEnvironment,
    val resolver: Resolver
) {

    fun generatePublisherTransactionalModule(txPublisher: KSClassDeclaration, publisher: KSClassDeclaration, annotation: KSAnnotation) {
        val packageName = txPublisher.packageName.asString()
        val implementationName = txPublisher.generatedClassName("Impl")
        val implementationTypeName = ClassName(packageName, implementationName)
        val publisherPackageName = publisher.packageName.asString()
        val publisherImplementationTypeName = ClassName(publisherPackageName, publisher.generatedClassName("Impl"))
        val moduleName = txPublisher.generatedClassName("Module")
        val module = TypeSpec.interfaceBuilder(moduleName)
            .addOriginatingKSFile(txPublisher.containingFile!!)
            .addAnnotation(CommonClassNames.module)
            .generated(KafkaPublisherGenerator::class)

        val configPath = annotation.findValueNoDefault<String>("value")!!
        val tag = AnnotationSpec.builder(CommonClassNames.tag).addMember("%T::class", txPublisher.toClassName()).build()
        val config = FunSpec.builder(txPublisher.simpleName.asString().replaceFirstChar { it.lowercaseChar() } + "_PublisherTransactionalConfig")
            .returns(KafkaClassNames.publisherTransactionalConfig)
            .addAnnotation(tag)
            .addParameter("config", CommonClassNames.config)
            .addParameter("extractor", CommonClassNames.configValueExtractor.parameterizedBy(KafkaClassNames.publisherTransactionalConfig))
            .addStatement("val configValue = config.get(%S)", configPath)
            .addStatement("return extractor.extract(configValue)!!")
            .build()
        val publisherFunc = FunSpec.builder(txPublisher.simpleName.asString().replaceFirstChar { it.lowercaseChar() } + "_PublisherTransactional")
            .addParameter("factory", Function::class.asClassName().parameterizedBy(Properties::class.asClassName(), publisherImplementationTypeName))
            .addParameter(ParameterSpec.builder("config", KafkaClassNames.publisherTransactionalConfig).addAnnotation(tag).build())
            .returns(txPublisher.toClassName())
            .addCode(CodeBlock.builder()
                .add("return %T(config) {", implementationTypeName).indent().add("\n")
                .addStatement("val properties = %T()", Properties::class.asClassName())
                .addStatement("properties[%T.TRANSACTIONAL_ID_CONFIG] = config.idPrefix() + \"-\" + %T.randomUUID()", ClassName("org.apache.kafka.clients.producer", "ProducerConfig"), UUID::class.java)
                .addStatement("factory.apply(properties)")
                .unindent().add("\n}\n")
                .build()
            )
            .build()
        module.addFunction(config)
        module.addFunction(publisherFunc)
        FileSpec.builder(packageName, moduleName).addType(module.build())
            .build()
            .writeTo(this.env.codeGenerator, false)
    }

    fun generatePublisherTransactionalImpl(typeElement: KSClassDeclaration, publisherType: ClassName, publisherTypeElement: KSClassDeclaration) {
        val packageName = typeElement.packageName.asString()
        val publisherPackageName = publisherTypeElement.packageName.asString()
        val implementationName = typeElement.generatedClassName("Impl")
        val publisherImplementationTypeName = ClassName(publisherPackageName, publisherTypeElement.generatedClassName("Impl"))
        val b = typeElement.extendsKeepAop(implementationName, resolver)
            .addSuperinterface(CommonClassNames.lifecycle)
            .addOriginatingKSFile(typeElement.containingFile!!)
            .addProperty(PropertySpec.builder("delegate", KafkaClassNames.transactionalPublisherImpl.parameterizedBy(publisherImplementationTypeName), KModifier.PRIVATE, KModifier.FINAL)
                .initializer("%T(config, factory)", KafkaClassNames.transactionalPublisherImpl)
                .build())
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("config", KafkaClassNames.publisherTransactionalConfig)
                .addParameter("factory", Supplier::class.asClassName().parameterizedBy(publisherImplementationTypeName))
                .build()
            )
            .addFunction(FunSpec.builder("init")
                .addModifiers(KModifier.OVERRIDE)
                .addStatement("this.delegate.init()")
                .build()
            )
            .addFunction(FunSpec.builder("release")
                .addModifiers(KModifier.OVERRIDE)
                .addStatement("this.delegate.release()")
                .build()
            )
            .addFunction(FunSpec.builder("begin")
                .addModifiers(KModifier.OVERRIDE)
                .returns(KafkaClassNames.transaction.parameterizedBy(WildcardTypeName.producerOf(publisherType)))
                .addStatement("return this.delegate.begin()")
                .build()
            )
            .build()
        FileSpec.builder(packageName, b.name!!).addType(b)
            .build()
            .writeTo(this.env.codeGenerator, false)
    }
}
