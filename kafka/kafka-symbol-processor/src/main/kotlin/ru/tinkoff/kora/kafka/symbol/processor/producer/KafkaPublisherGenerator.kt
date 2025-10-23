package ru.tinkoff.kora.kafka.symbol.processor.producer

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.kafkaTopicAnnotation
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.producerRecord
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.producerTelemetryFactory
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.serializer
import ru.tinkoff.kora.kafka.symbol.processor.utils.KafkaPublisherUtils
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonAopUtils.extendsKeepAop
import ru.tinkoff.kora.ksp.common.CommonAopUtils.overridingKeepAop
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.FunctionUtils.isCompletionStage
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.nextControlFlow
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.observe
import ru.tinkoff.kora.ksp.common.KspCommonUtils.addOriginatingKSFile
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import ru.tinkoff.kora.ksp.common.TagUtils.toTagAnnotation
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.generatedClassName
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

class KafkaPublisherGenerator(val env: SymbolProcessorEnvironment, val resolver: Resolver) {

    fun generatePublisherModule(publisher: KSClassDeclaration, publishMethods: List<KSFunctionDeclaration>, publisherAnnotation: KSAnnotation, topicConfig: ClassName?, aopProxy: KSClassDeclaration?) {
        val packageName = publisher.packageName.asString()
        val moduleName = publisher.generatedClassName("PublisherModule")
        val module = TypeSpec.interfaceBuilder(moduleName)
            .addOriginatingKSFile(publisher)
            .addAnnotation(CommonClassNames.module)
            .generated(KafkaPublisherGenerator::class)

        module.addFunction(this.buildPublisherFactoryFunction(publisher, publishMethods, topicConfig, aopProxy))
        module.addFunction(this.buildPublisherFactoryImpl(publisher))
        module.addFunction(this.buildPublisherConfig(publisher, publisherAnnotation))
        topicConfig?.let {
            module.addFunction(this.buildTopicConfigMethod(publisher, publishMethods, publisherAnnotation, it))
        }

        FileSpec.builder(packageName, moduleName)
            .addType(module.build())
            .build()
            .writeTo(env.codeGenerator, false)
    }

    private fun buildTopicConfigMethod(publisher: KSClassDeclaration, publishMethods: List<KSFunctionDeclaration>, publisherAnnotation: KSAnnotation, configTypeName: ClassName): FunSpec {
        val configName = publisher.generatedClassName("TopicConfig")

        val m = FunSpec.builder(configName.substring(1).replaceFirstChar { it.lowercaseChar() })
            .addModifiers(KModifier.PUBLIC)
            .addParameter("config", CommonClassNames.config)
            .addParameter("parser", CommonClassNames.configValueExtractor.parameterizedBy(KafkaClassNames.publisherTopicConfig))
            .returns(configTypeName)

        val b = CodeBlock.builder()
            .add("return %T(", configTypeName).indent().add("\n")

        val root = publisherAnnotation.findValueNoDefault<String>("value")!!
        for ((i, method) in publishMethods.withIndex()) {
            val annotation = method.findAnnotation(kafkaTopicAnnotation)
            if (annotation != null) {
                var path = annotation.findValueNoDefault<String>("value")!!
                if (path.startsWith(".")) {
                    path = root + path
                }
                if (i > 0) {
                    b.add(",\n")
                }
                b.add("parser.extract(config.get(%S))!!", path)
            }
        }
        b.unindent().add("\n)\n")
        m.addCode(b.build())
        return m.build()
    }

    private fun buildPublisherConfig(publisher: KSClassDeclaration, annotation: KSAnnotation): FunSpec {
        val configPath = annotation.findValueNoDefault<String>("value")!!
        val propertiesTag = AnnotationSpec.builder(CommonClassNames.tag).addMember("%T::class", publisher.toClassName()).build()
        return FunSpec.builder(publisher.simpleName.asString() + "_PublisherConfig")
            .returns(KafkaClassNames.publisherConfig)
            .addAnnotation(propertiesTag)
            .addParameter("config", CommonClassNames.config)
            .addParameter("extractor", CommonClassNames.configValueExtractor.parameterizedBy(KafkaClassNames.publisherConfig))
            .addStatement("val configValue = config.get(%S)", configPath)
            .addStatement("return extractor.extract(configValue)!!")
            .build()
    }

    private fun buildPublisherFactoryImpl(publisher: KSClassDeclaration): FunSpec {
        val packageName = publisher.packageName.asString()
        val implementationName = publisher.generatedClassName("Impl")
        val implementationTypeName = ClassName(packageName, implementationName)

        val functionType = Function::class.asTypeName().parameterizedBy(Properties::class.asClassName(), implementationTypeName)
        val builder = FunSpec.builder(publisher.simpleName.asString() + "_PublisherImpl")
            .returns(publisher.toTypeName())

        return builder
            .addParameter("factory", functionType)
            .addStatement("return factory.apply(%T())", Properties::class.asClassName())
            .build()
    }

    private fun buildPublisherFactoryFunction(publisher: KSClassDeclaration, publishMethods: List<KSFunctionDeclaration>, topicConfig: ClassName?, aopProxy: KSClassDeclaration?): FunSpec {
        val propertiesTag = AnnotationSpec.builder(CommonClassNames.tag).addMember("%T::class", publisher.toClassName()).build()
        val config = ParameterSpec.builder("config", KafkaClassNames.publisherConfig).addAnnotation(propertiesTag).build()
        val packageName = publisher.packageName.asString()
        val implementationName = publisher.generatedClassName("Impl")
        val implementationTypeName = ClassName(packageName, implementationName)
        val returnType = Function::class.asClassName().parameterizedBy(Properties::class.asClassName(), implementationTypeName)

        val funBuilder = FunSpec.builder(publisher.simpleName.asString().replaceFirstChar { it.lowercaseChar() } + "_PublisherFactory")
            .addParameter("telemetryFactory", producerTelemetryFactory)
            .addParameter("meterRegistry", CommonClassNames.meterRegistry)
            .addParameter(config)
            .apply { topicConfig?.let { addParameter("topicConfig", it) } }
            .returns(returnType)

        data class TypeWithTag(val typeName: TypeName, val tag: Set<String>)

        val builder = CodeBlock.builder()

        builder.controlFlow("return %T { additionalProperties -> ", returnType) {
            addStatement("var properties = %T()", Properties::class.asClassName())
            addStatement("properties.putAll(config.driverProperties())")
            addStatement("properties.putAll(additionalProperties)")
            add("%T(telemetryFactory, config.telemetry(), properties", aopProxy?.toClassName() ?: implementationTypeName).indent()
            topicConfig?.let { add(", topicConfig") }
            add(", meterRegistry")
            val parameters = HashMap<TypeWithTag, String>()
            val counter = AtomicInteger(0)
            for (method in publishMethods) {
                val types = KafkaPublisherUtils.parsePublisherType(method)
                if (types.keyType != null) {
                    val keyType = TypeWithTag(types.keyType, types.keyTag)
                    var keyParserName = parameters[keyType]
                    if (keyParserName == null) {
                        keyParserName = "serializer" + counter.incrementAndGet()
                        val parameter = ParameterSpec.builder(keyParserName, serializer.parameterizedBy(keyType.typeName))
                        val tags = keyType.tag
                        if (tags.isNotEmpty()) {
                            parameter.addAnnotation(tags.toTagAnnotation())
                        }
                        funBuilder.addParameter(parameter.build())
                        parameters[keyType] = keyParserName
                        add(", %N", keyParserName)
                    }
                }
                val valueType = TypeWithTag(types.valueType, types.valueTag)
                var valueParserName = parameters[valueType]
                if (valueParserName == null) {
                    valueParserName = "serializer" + counter.incrementAndGet()
                    val parameter = ParameterSpec.builder(valueParserName, serializer.parameterizedBy(valueType.typeName))
                    val tags = valueType.tag
                    if (tags.isNotEmpty()) {
                        parameter.addAnnotation(tags.toTagAnnotation())
                    }
                    funBuilder.addParameter(parameter.build())
                    parameters[valueType] = valueParserName
                    add(", %N", valueParserName)
                }
            }

            if (aopProxy != null) {
                val constructor = aopProxy.getConstructors().filter { it.isPublic() }.firstOrNull() ?: throw ProcessingErrorException("Can't find aop proxy constructor", aopProxy)
                val baseParams = 3 + counter.get() + (topicConfig?.let { 1 } ?: 0)
                for (i in baseParams until constructor.parameters.size) {
                    val param = constructor.parameters[i]
                    val b = ParameterSpec.builder(param.name!!.asString(), param.type.toTypeName())
                    for (annotation in param.annotations) {
                        b.addAnnotation(annotation.toAnnotationSpec())
                    }
                    funBuilder.addParameter(b.build())
                    add(", %N", param.name!!.asString())
                }
            }
            builder.unindent().add("\n)\n")
        }

        return funBuilder.addCode(builder.build()).build()
    }

    fun generatePublisherImpl(classDeclaration: KSClassDeclaration, publishMethods: List<KSFunctionDeclaration>, publisherAnnotation: KSAnnotation, topicConfig: ClassName?) {
        val packageName = classDeclaration.packageName.asString()
        val implementationName = classDeclaration.generatedClassName("Impl")
        val configPath = publisherAnnotation.findValueNoDefault<String>("value")!!

        val b = classDeclaration.extendsKeepAop(implementationName, resolver)
            .generated(KafkaPublisherSymbolProcessor::class)
            .superclass(KafkaClassNames.abstractPublisher)
            .addSuperclassConstructorParameter("driverProperties")
            .addSuperclassConstructorParameter("telemetryConfig")
            .addSuperclassConstructorParameter("telemetryFactory.get(%S, telemetryConfig, driverProperties)", configPath)
            .addSuperclassConstructorParameter("meterRegistry")
            .apply { topicConfig?.let { addProperty(PropertySpec.builder("topicConfig", it, KModifier.PRIVATE, KModifier.FINAL).initializer("topicConfig").build()) } }

        val constructorBuilder = FunSpec.constructorBuilder()
            .addParameter("telemetryFactory", producerTelemetryFactory)
            .addParameter("telemetryConfig", KafkaClassNames.publisherTelemetryConfig)
            .addParameter("driverProperties", Properties::class)
            .apply { topicConfig?.let { addParameter("topicConfig", it) } }
            .addParameter("meterRegistry", CommonClassNames.meterRegistry)

        data class TypeWithTag(val type: TypeName, val tag: Set<String>)

        val parameters = mutableMapOf<TypeWithTag, String>()
        val counter = AtomicInteger(0)
        for (i in publishMethods.indices) {
            val publishMethod = publishMethods[i]
            val publishData = KafkaPublisherUtils.parsePublisherType(publishMethod)
            var keyParserName = null as String?
            if (publishData.keyType != null) {
                val keyType = TypeWithTag(publishData.keyType, publishData.keyTag)
                keyParserName = parameters[keyType]
                if (keyParserName == null) {
                    keyParserName = "serializer" + counter.incrementAndGet()
                    val type = serializer.parameterizedBy(keyType.type)
                    b.addProperty(PropertySpec.builder(keyParserName, type, KModifier.PRIVATE, KModifier.FINAL).initializer(keyParserName).build())
                    val parameter = ParameterSpec.builder(keyParserName, type)
                    val tags = keyType.tag
                    if (tags.isNotEmpty()) {
                        parameter.addAnnotation(tags.toTagAnnotation())
                    }
                    constructorBuilder.addParameter(parameter.build())
                    parameters[keyType] = keyParserName
                }
            }
            val valueType = TypeWithTag(publishData.valueType, publishData.valueTag)
            var valueParserName = parameters[valueType]
            if (valueParserName == null) {
                valueParserName = "serializer" + counter.incrementAndGet()
                val type = serializer.parameterizedBy(valueType.type)
                b.addProperty(PropertySpec.builder(valueParserName, type, KModifier.PRIVATE, KModifier.FINAL).initializer(valueParserName).build())
                val parameter = ParameterSpec.builder(valueParserName, type)
                val tags = valueType.tag
                if (tags.isNotEmpty()) {
                    parameter.addAnnotation(tags.toTagAnnotation())
                }
                constructorBuilder.addParameter(parameter.build())
                parameters[valueType] = valueParserName
            }
            val topicVariable = "topic$i"
            val method = generatePublisherExecutableMethod(publishMethod, publishData, topicVariable, keyParserName, valueParserName)
            b.addFunction(method)
        }
        b.primaryConstructor(constructorBuilder.build())
        FileSpec.builder(packageName, implementationName).addType(b.build()).build().writeTo(env.codeGenerator, false)
    }

    private val await = MemberName("kotlinx.coroutines.future", "await")

    private fun generatePublisherExecutableMethod(
        publishMethod: KSFunctionDeclaration,
        publishData: KafkaPublisherUtils.PublisherData,
        topicVariable: String,
        keyParserName: String?,
        valueParserName: String
    ): FunSpec {
        val b = publishMethod.overridingKeepAop(resolver)
        if (publishData.recordVar != null) {
            val record = publishData.recordVar.name?.asString().toString()
            b.addStatement("val _topic = %N.topic()", record)
        } else {
            b.addStatement("val _topic = this.topicConfig.%N.topic()", topicVariable)
        }
        val returnType = publishMethod.returnType!!.toTypeName()
        b.addStatement("val _observation = this.telemetry.observeSend(_topic)")
        b.addCode("return ")
        val observeType = if (publishMethod.isSuspend()) CommonClassNames.completableFuture.parameterizedBy(returnType) else returnType
        b.observe("_observation", observeType) {
            if (publishData.recordVar != null) {
                val record = publishData.recordVar.name?.asString().toString()
                addStatement("_observation.observeData(%N.key(), %N.value())", record, record);
                addStatement("val _headers = %N.headers()", record)
                addStatement("val _key = %N.serialize(%N.topic(), _headers, %N.key())", keyParserName!!, record, record)
                addStatement("val _value = %N.serialize(%N.topic(), _headers, %N.value())", valueParserName, record, record)
                addStatement("val _record = %T(%N.topic(), %N.partition(), %N.timestamp(), _key, _value, _headers)", producerRecord, record, record, record)
            } else {
                require(publishData.valueVar != null)
                if (publishData.keyVar != null) {
                    addStatement("_observation.observeData(%N, %N)", publishData.keyVar.name!!.asString(), publishData.valueVar.name!!.asString())
                } else {
                    addStatement("_observation.observeData(null, %N)", publishData.valueVar.name!!.asString())
                }
                addStatement("val _partition = this.topicConfig.%N.partition()", topicVariable)
                if (publishData.headersVar == null) {
                    addStatement("val _headers = %T()", KafkaClassNames.recordHeaders)
                } else {
                    addStatement("val _headers = %N", publishData.headersVar.name?.asString().toString())
                }
                if (publishData.keyVar == null) {
                    addStatement("val _key: ByteArray? = null")
                } else {
                    addStatement("val _key = %N.serialize(_topic, _headers, %N)", keyParserName!!, publishData.keyVar.name!!.asString())
                }
                addStatement("val _value = %N.serialize(_topic, _headers, %N)", valueParserName, publishData.valueVar.name!!.asString())
                addStatement("val _record = %T(_topic, _partition, null, _key, _value, _headers)", producerRecord)
            }
            addStatement("_observation.observeRecord(_record)")
            if (publishMethod.isFuture() || publishMethod.isCompletionStage() || publishMethod.isSuspend()) {
                addStatement("val _future = %T<%T>()", CommonClassNames.completableFuture, KafkaClassNames.producerRecordMetadata)
            }
            controlFlow("this.delegate!!.send(_record) { _meta, _ex ->") {
                addStatement("_observation.onCompletion(_meta, _ex)")
                if (publishData.callback != null) {
                    addStatement("%N.onCompletion(_meta, _ex)", publishData.callback.name?.asString().toString())
                }
                if (publishMethod.isFuture() || publishMethod.isCompletionStage() || publishMethod.isSuspend()) {
                    controlFlow("if (_ex != null)") {
                        addStatement("_future.completeExceptionally(_ex)")
                        nextControlFlow("else") {
                            addStatement("_future.complete(_meta)")
                        }
                    }
                }
            }
            if (publishMethod.isCompletionStage() || publishMethod.isFuture() || publishMethod.isSuspend()) {
                add("_future\n")
            } else {
                add(".get()\n")
            }
        }
        if (publishMethod.isSuspend()) {
            b.addCode(".%M()", await)
        }
        return b.build()
    }

    fun generateConfig(producer: KSClassDeclaration, publishMethods: List<KSFunctionDeclaration>): ClassName? {
        val packageName = producer.packageName.asString()
        val b = TypeSpec.classBuilder(producer.generatedClassName("TopicConfig"))
            .generated(KafkaPublisherSymbolProcessor::class)
            .addModifiers(KModifier.DATA)
            .addOriginatingKSFile(producer)
        val constructor = FunSpec.constructorBuilder()
        var count = 0
        for ((i, method) in publishMethods.withIndex()) {
            if (method.isAnnotationPresent(kafkaTopicAnnotation)) {
                b.addProperty(PropertySpec.builder("topic$i", KafkaClassNames.publisherTopicConfig).initializer("topic$i").build())
                constructor.addParameter("topic$i", KafkaClassNames.publisherTopicConfig)
                count++
            }
        }
        if (count == 0) {
            return null
        }
        val type = b.primaryConstructor(constructor.build()).build()
        FileSpec.builder(packageName, type.name!!).addType(type).build()
            .writeTo(env.codeGenerator, false)
        return ClassName(packageName, type.name!!)
    }
}
