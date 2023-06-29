package ru.tinkoff.kora.kafka.symbol.processor.producer

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.CommonAopUtils.extendsKeepAop
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.TagUtils.toTagAnnotation
import ru.tinkoff.kora.ksp.common.generatedClassName
import ru.tinkoff.kora.ksp.common.parseTags
import java.util.*

class KafkaPublisherSymbolProcessor(val env: SymbolProcessorEnvironment) : BaseSymbolProcessor(env) {

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val producers = mutableListOf<KSAnnotated>()
        val deferred = mutableListOf<KSAnnotated>()
        for (it in resolver.getSymbolsWithAnnotation(KafkaClassNames.kafkaPublisherAnnotation.canonicalName)) {
            if (it.validate()) {
                producers.add(it)
            } else {
                deferred.add(it)
            }
        }
        for (producer in producers) {
            if (producer !is KSClassDeclaration || producer.classKind != ClassKind.INTERFACE) {
                env.logger.error("@KafkaPublisher can be placed only on interfaces extending only Producer or TransactionalProducer", producer)
                continue
            }
            val supertypes = producer.superTypes.toList()
            if (supertypes.size != 1) {
                env.logger.error("@KafkaPublisher can be placed only on interfaces extending only Producer or TransactionalProducer", producer)
                continue
            }
            val supertype = supertypes[0].resolve()
            val supertypeName = supertype.toTypeName()
            if (supertypeName !is ParameterizedTypeName) {
                env.logger.error("@KafkaPublisher can be placed only on interfaces extending only Producer or TransactionalProducer", producer)
                continue
            }
            if (supertypeName.rawType == KafkaClassNames.producer) {
                val keyType = supertypeName.typeArguments[0]
                val valueType = supertypeName.typeArguments[1]
                this.generateProducerModule(producer, supertype, supertypeName, keyType, valueType)
                this.generateProducerImplementation(resolver, producer, supertype, supertypeName, keyType, valueType)
            } else if (supertypeName.rawType == KafkaClassNames.transactionalProducer) {
                val keyType = supertypeName.typeArguments[0]
                val valueType = supertypeName.typeArguments[1]
                this.generateProducerModule(producer, supertype, supertypeName, keyType, valueType)
                this.generateTransactionalProducerImplementation(resolver, producer, keyType, valueType)
            } else {
                env.logger.error("@KafkaPublisher can be placed only on interfaces extending only Producer or TransactionalProducer", producer)
                continue
            }


        }

        return deferred
    }

    private fun generateProducerModule(producer: KSClassDeclaration, supertype: KSType, supertypeName: ParameterizedTypeName, keyType: TypeName, valueType: TypeName) {
        val packageName = producer.packageName.asString()

        val moduleName = producer.generatedClassName("Module")
        val module = TypeSpec.interfaceBuilder(moduleName)
            .addOriginatingKSFile(producer.containingFile!!)
            .addAnnotation(CommonClassNames.module)
            .generated(KafkaPublisherSymbolProcessor::class)

        val implementationName = ClassName(packageName, producer.generatedClassName("Implementation"))
        module.addFunction(this.buildPropertiesFun(producer))
        module.addFunction(this.buildGeneratedProducerFun(producer, supertype, implementationName, keyType, valueType))
        if (supertypeName.rawType == KafkaClassNames.producer) {
            module.addFunction(this.buildKafkaProducerFun(producer, implementationName, keyType, valueType))
        }

        FileSpec.builder(packageName, moduleName)
            .addType(module.build())
            .build()
            .writeTo(env.codeGenerator, false)

    }

    private fun buildPropertiesFun(producer: KSClassDeclaration): FunSpec {
        val configPath = producer.findAnnotation(KafkaClassNames.kafkaPublisherAnnotation)
            ?.findValueNoDefault<String>("value")!!

        return FunSpec.builder(producer.simpleName.asString() + "_ProducerProperties")
            .returns(KafkaClassNames.publisherConfig)
            .addAnnotation(setOf(producer.qualifiedName!!.asString()).toTagAnnotation()!!)
            .addParameter("config", CommonClassNames.config)
            .addParameter("extractor", CommonClassNames.configValueExtractor.parameterizedBy(KafkaClassNames.publisherConfig))
            .addStatement("val configValue = config.getValue(%S)", configPath)
            .addStatement("return extractor.extract(configValue)!!", Objects::class.java)
            .build()
    }

    private fun buildKafkaProducerFun(producer: KSClassDeclaration, implementationName: ClassName, keyType: TypeName, valueType: TypeName): FunSpec {
        return FunSpec.builder(producer.simpleName.asString() + "_kafkaProducer")
            .returns(KafkaClassNames.kafkaProducer.parameterizedBy(keyType, valueType))
            .addAnnotation(setOf(producer.qualifiedName!!.asString()).toTagAnnotation()!!)
            .addParameter("producer", implementationName)
            .addStatement("return producer.delegate()")
            .build()
    }

    private fun buildGeneratedProducerFun(producer: KSClassDeclaration, supertype: KSType, implementationName: ClassName, keyType: TypeName, valueType: TypeName): FunSpec {
        val keySerializer = ParameterSpec.builder("keySerializer", KafkaClassNames.serializer.parameterizedBy(keyType))
        val valueSerializer = ParameterSpec.builder("valueSerializer", KafkaClassNames.serializer.parameterizedBy(valueType))

        supertype.arguments[0].parseTags()
            .map { it.toClassName().canonicalName }.toSet()
            .toTagAnnotation()?.let { keySerializer.addAnnotation(it) }

        supertype.arguments[1].parseTags()
            .map { it.toClassName().canonicalName }.toSet()
            .toTagAnnotation()?.let { valueSerializer.addAnnotation(it) }

        val propertiesTag = setOf(producer.qualifiedName!!.asString()).toTagAnnotation()!!
        val config = ParameterSpec.builder("properties", KafkaClassNames.publisherConfig).addAnnotation(propertiesTag).build()

        return FunSpec.builder(producer.simpleName.asString() + "_ProducerImpl")
            .returns(implementationName)
            .addParameter(config)
            .addParameter(keySerializer.build())
            .addParameter(valueSerializer.build())
            .addParameter("telemetryFactory", KafkaClassNames.producerTelemetryFactory)
            .addStatement("return %T(telemetryFactory, properties, keySerializer, valueSerializer)", implementationName)
            .build()
    }

    private fun generateProducerImplementation(resolver: Resolver, producer: KSClassDeclaration, supertype: KSType, supertypeName: ParameterizedTypeName, keyType: TypeName, valueType: TypeName) {
        val packageName = producer.packageName.asString()
        val implementationName = producer.generatedClassName("Implementation")
        val kafkaProducerType = KafkaClassNames.kafkaProducer.parameterizedBy(keyType, valueType)
        val recordMetadataFuture = ClassName("java.util.concurrent", "Future").parameterizedBy(ClassName("org.apache.kafka.clients.producer", "RecordMetadata"))

        val b = producer.extendsKeepAop(resolver, implementationName)
            .addSuperinterface(CommonClassNames.lifecycle)
            .addProperty(PropertySpec.builder("config", KafkaClassNames.publisherConfig, KModifier.PRIVATE, KModifier.FINAL).initializer("config").build())
            .addProperty(PropertySpec.builder("keySerializer", KafkaClassNames.serializer.parameterizedBy(keyType), KModifier.PRIVATE, KModifier.FINAL).initializer("keySerializer").build())
            .addProperty(PropertySpec.builder("valueSerializer", KafkaClassNames.serializer.parameterizedBy(valueType), KModifier.PRIVATE, KModifier.FINAL).initializer("valueSerializer").build())
            .addProperty(PropertySpec.builder("telemetryFactory", KafkaClassNames.producerTelemetryFactory, KModifier.PRIVATE, KModifier.FINAL).initializer("telemetryFactory").build())
            .addProperty(PropertySpec.builder("delegate", kafkaProducerType.copy(true), KModifier.PRIVATE).addAnnotation(Volatile::class).initializer("null").mutable().build())
            .addProperty(PropertySpec.builder("telemetry", KafkaClassNames.producerTelemetry.copy(true), KModifier.PRIVATE).addAnnotation(Volatile::class).initializer("null").mutable().build())
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("telemetryFactory", KafkaClassNames.producerTelemetryFactory)
                .addParameter("config", KafkaClassNames.publisherConfig)
                .addParameter("keySerializer", KafkaClassNames.serializer.parameterizedBy(keyType))
                .addParameter("valueSerializer", KafkaClassNames.serializer.parameterizedBy(valueType))
                .build())
            .addFunction(FunSpec.builder("delegate")
                .returns(kafkaProducerType)
                .addStatement("return delegate!!")
                .build()
            )
            .addFunction(FunSpec.builder("init")
                .addModifiers(KModifier.OVERRIDE)
                .returns(CommonClassNames.mono.parameterizedBy(STAR))
                .controlFlow("return ru.tinkoff.kora.common.util.ReactorUtils.ioMono() {") {
                    addStatement("val properties = this.config.driverProperties()") // todo process some props?
                    addStatement("this.delegate = %T(properties, this.keySerializer, this.valueSerializer)", KafkaClassNames.kafkaProducer)
                    addStatement("this.telemetry = this.telemetryFactory.get(this.delegate, properties)")
                }
                .build())
            .addFunction(FunSpec.builder("release")
                .addModifiers(KModifier.OVERRIDE)
                .returns(CommonClassNames.mono.parameterizedBy(STAR))
                .controlFlow("return ru.tinkoff.kora.common.util.ReactorUtils.ioMono() {") {
                    controlFlow("delegate?.let") {
                        addStatement("it.close()")
                        addStatement("delegate = null")
                        controlFlow("telemetry?.let") {
                            addStatement("it.close()")
                            addStatement("telemetry = null")
                        }
                    }
                }
                .build()
            )
            .addFunction(FunSpec.builder("send")
                .returns(recordMetadataFuture)
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("record", KafkaClassNames.producerRecord.parameterizedBy(keyType, valueType))
                .addStatement("val tctx = this.telemetry!!.record(record)")
                .addStatement("return this.delegate!!.send(record, tctx)")
                .build()
            )
            .addFunction(FunSpec.builder("send")
                .returns(recordMetadataFuture)
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("record", KafkaClassNames.producerRecord.parameterizedBy(keyType, valueType))
                .addParameter("callback", ClassName("org.apache.kafka.clients.producer", "Callback"))
                .addStatement("val tctx = this.telemetry!!.record(record)")
                .controlFlow("return this.delegate!!.send(record) { metadata, error ->") {
                    addStatement("tctx.onCompletion(metadata, error)")
                    addStatement("callback.onCompletion(metadata, error)")
                }
                .build()
            )
        for (method in resolver.getClassDeclarationByName(KafkaClassNames.producer.canonicalName)!!.getDeclaredFunctions()) {
            if (method.simpleName.asString() == "send") {
                continue
            }
            val m = FunSpec.builder(method.simpleName.asString())
                .addModifiers(KModifier.OVERRIDE)
            val methodType = method.asMemberOf(supertype)
            m.addCode("return this.delegate!!.%N(", method.simpleName.asString())
            for ((idx, param) in method.parameters.withIndex()) {
                val paramType = methodType.parameterTypes[idx]
                if (idx > 0) {
                    m.addCode(", ")
                }
                m.addCode("%N", param.name!!.asString())
                m.addParameter(param.name!!.asString(), paramType!!.toTypeName())
            }
            m.addCode(")\n")
            b.addFunction(m.build())
        }

        FileSpec.builder(packageName, implementationName)
            .addType(b.build())
            .build()
            .writeTo(env.codeGenerator, false)
    }

    private fun generateTransactionalProducerImplementation(resolver: Resolver, producer: KSClassDeclaration, keyType: TypeName, valueType: TypeName) {
        val packageName = producer.packageName.asString()
        val implementationName = producer.generatedClassName("Implementation")
        val kafkaProducerType = KafkaClassNames.producer.parameterizedBy(keyType, valueType)

        val delegateType = KafkaClassNames.transactionalProducerImpl.parameterizedBy(keyType, valueType)
        val keySer = KafkaClassNames.serializer.parameterizedBy(keyType)
        val valSer = KafkaClassNames.serializer.parameterizedBy(valueType)

        val b = producer.extendsKeepAop(resolver, implementationName)
            .addSuperinterface(CommonClassNames.lifecycle)
            .addProperty(PropertySpec.builder("delegate", delegateType).initializer("%T(telemetryFactory, config, keySerializer, valueSerializer)", KafkaClassNames.transactionalProducerImpl).build())
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("telemetryFactory", KafkaClassNames.producerTelemetryFactory)
                .addParameter("config", KafkaClassNames.publisherConfig)
                .addParameter("keySerializer", keySer)
                .addParameter("valueSerializer", valSer)
                .build())
            .addFunction(FunSpec.builder("begin")
                .addModifiers(KModifier.OVERRIDE)
                .returns(kafkaProducerType)
                .addStatement("return delegate.begin()")
                .build()
            )
            .addFunction(FunSpec.builder("init")
                .addModifiers(KModifier.OVERRIDE)
                .returns(CommonClassNames.mono.parameterizedBy(STAR))
                .addStatement("return delegate.init()")
                .build()
            )
            .addFunction(FunSpec.builder("release")
                .addModifiers(KModifier.OVERRIDE)
                .returns(CommonClassNames.mono.parameterizedBy(STAR))
                .addStatement("return delegate.release()")
                .build()
            )

        FileSpec.builder(packageName, implementationName)
            .addType(b.build())
            .build()
            .writeTo(env.codeGenerator, false)
    }

}
