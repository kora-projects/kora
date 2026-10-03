package io.koraframework.nats.symbol.processor

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.*
import io.koraframework.ksp.common.CommonAopUtils.extendsKeepAop
import io.koraframework.ksp.common.CommonAopUtils.overridingKeepAop
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.TagUtils.addTag
import io.koraframework.ksp.common.TagUtils.parseTag

class NatsSymbolProcessor(private val environment: SymbolProcessorEnvironment) : BaseSymbolProcessor(environment) {
    private val generated = hashSetOf<String>()
    private val root = "io.koraframework.nats.common."
    private fun type(name: String) = ClassName.bestGuess(root + name)
    private val publisher = type("annotation.NatsPublisher")
    private val subject = publisher.nestedClass("Subject")
    private val request = publisher.nestedClass("Request")
    private val listener = type("annotation.NatsListener")
    private val client = type("NatsClient")
    private val publisherConfig = type("producer.NatsPublisherConfig")
    private val atomicPublisher = type("producer.AtomicBatchPublisher")
    private val atomicConfig = type("producer.NatsAtomicBatchConfig")
    private val batchSink = type("producer.NatsAtomicBatchSink")
    private val function = ClassName("java.util.function", "Function")
    private val subjectConfig = publisherConfig.nestedClass("SubjectConfig")
    private val listenerConfig = type("consumer.NatsListenerConfig")
    private val serializer = type("producer.serializer.NatsSerializer")
    private val deserializer = type("consumer.deserializer.NatsDeserializer")
    private val consumerMessage = type("consumer.NatsMessage")
    private val consumerMessages = type("consumer.NatsMessages")
    private val producerMessage = type("producer.NatsProducerMessage")
    private val publisherTelemetry = type("producer.telemetry.NatsPublisherTelemetry")
    private val publisherTelemetryFactory = type("producer.telemetry.NatsPublisherTelemetryFactory")
    private val consumerTelemetry = type("consumer.telemetry.NatsConsumerTelemetry")
    private val consumerTelemetryFactory = type("consumer.telemetry.NatsConsumerTelemetryFactory")
    private val pollObservation = type("consumer.telemetry.NatsConsumerPollObservation")
    private val serializationException = type("exceptions.NatsSerializationException")
    private val configurer = ClassName("io.koraframework.common", "Configurer")
    private val optionsBuilder = ClassName("io.nats.client", "Options").nestedClass("Builder")
    private val consumerBuilder = ClassName("io.nats.client.api", "ConsumerConfiguration").nestedClass("Builder")
    private val container = type("consumer.NatsConsumerContainer")
    private val message = ClassName("io.nats.client", "Message")
    private val connection = ClassName("io.nats.client", "Connection")
    private val headers = ClassName("io.nats.client.impl", "Headers")
    private val options = ClassName("io.nats.client", "PublishOptions")
    private val callback = type("producer.NatsPublishCallback")
    private val ack = ClassName("io.nats.client.api", "PublishAck")
    private val future = ClassName("java.util.concurrent", "CompletableFuture")
    private val stage = ClassName("java.util.concurrent", "CompletionStage")
    private val duration = ClassName("java.time", "Duration")

    private class ContractError(val node: KSNode, message: String) : RuntimeException(message)

    private fun requireContract(condition: Boolean, node: KSNode, message: String) {
        if (!condition) {
            throw ContractError(node, message)
        }
    }

    private fun KSAnnotated.annotation(name: ClassName) = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == name.canonicalName
    }

    private fun KSAnnotation.value(name: String): Any? = arguments.firstOrNull { it.name?.asString() == name }?.value
    private fun KSType.isType(name: ClassName) = declaration.qualifiedName?.asString() == name.canonicalName
    private fun KSType.isUnit() = declaration.qualifiedName?.asString() in setOf("kotlin.Unit", "java.lang.Void")
    private fun KSType.isDeserializationException() = declaration.qualifiedName?.asString() in
        setOf(serializationException.canonicalName, "java.lang.Exception", "kotlin.Exception")

    private fun KSType.argument(node: KSNode): KSType {
        val result = arguments.singleOrNull()?.type?.resolve()
        requireContract(result != null && result.declaration !is KSTypeParameter, node, "NATS record/future requires one concrete type argument")
        return result!!
    }

    private fun KSType.payloadName(): TypeName = toTypeName().copy(annotations = emptyList())
    private fun tagType(tag: String): ClassName {
        val parts = tag.split('.')
        val first = parts.indexOfFirst { it.startsWith('$') || it.first().isUpperCase() }
        return ClassName(parts.take(first).joinToString("."), parts[first], *parts.drop(first + 1).toTypedArray())
    }

    private fun parameter(type: TypeName, name: String, tag: String? = null) =
        ParameterSpec.builder(name, type).addTag(tag?.let { tagType(it) }).build()

    private fun mapperType(source: KSAnnotated, kind: ClassName, default: TypeName): TypeName =
        source.parseMappingData().getMapping(kind)?.mapper?.toTypeName() ?: default

    private fun module(name: String) = TypeSpec.interfaceBuilder(name)
        .addAnnotation(CommonClassNames.module).generated(NatsSymbolProcessor::class)

    private fun configFunction(name: String, config: ClassName, tag: ClassName, path: String) =
        FunSpec.builder(name).returns(config).addTag(tag)
            .addParameter("config", CommonClassNames.config)
            .addParameter("mapper", CommonClassNames.configValueMapper.parameterizedBy(config))
            .addStatement("return mapper.mapOrThrow(config.get(%S))!!", path).build()

    private fun write(owner: KSClassDeclaration, spec: TypeSpec) {
        FileSpec.builder(owner.packageName.asString(), spec.name!!).addType(spec).build()
            .writeTo(environment.codeGenerator, Dependencies(false, owner.containingFile!!))
    }

    private fun providerPrefix(owner: KSClassDeclaration) = owner.generatedClass("")
        .removePrefix("$").removeSuffix("_").replaceFirstChar { it.lowercaseChar() }

    private fun connectionProviders(
        module: TypeSpec.Builder, prefix: String, config: ClassName, tag: ClassName,
        path: String, canonicalName: String, telemetry: ClassName, factory: ClassName
    ) {
        module.addFunction(
            FunSpec.builder(prefix + "Telemetry").returns(telemetry).addTag(tag)
                .addParameter(parameter(config, "_config", tag.canonicalName)).addParameter("_factory", factory)
                .addStatement("return _factory.get(%S, %S, _config.telemetry(), _config.driverProperties())", path, canonicalName).build()
        )
        module.addFunction(
            FunSpec.builder(prefix + "Client").returns(client).addTag(tag)
                .addParameter(parameter(config, "_config", tag.canonicalName))
                .addParameter(parameter(configurer.parameterizedBy(optionsBuilder).copy(nullable = true), "_configurer", tag.canonicalName))
                .addStatement(
                    if (config == listenerConfig) {
                        "return %T(_config, _configurer, _config.threads() > 0, true)"
                    } else {
                        "return %T(_config, _configurer)"
                    }, client
                ).build()
        )
    }

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        val publishers = resolver.getSymbolsWithAnnotation(publisher.canonicalName).toList()
        for (symbol in publishers) {
            if (!symbol.validate()) {
                deferred.add(symbol)
                continue
            }
            try {
                requireContract(symbol is KSClassDeclaration, symbol, "@NatsPublisher requires an interface")
                generatePublisher(symbol as KSClassDeclaration, resolver)
            } catch (e: ContractError) {
                kspLogger.error(e.message!!, e.node)
            }
        }
        for (symbol in resolver.getSymbolsWithAnnotation(CommonClassNames.aopProxy.canonicalName)) {
            val proxy = symbol as? KSClassDeclaration ?: continue
            val parent = proxy.superTypes.firstOrNull()?.resolve()?.declaration as? KSClassDeclaration ?: continue
            for (contract in parent.superTypes) {
                val owner = contract.resolve().declaration as? KSClassDeclaration ?: continue
                if (owner.annotation(publisher) == null) {
                    continue
                }
                try {
                    generatePublisher(owner, resolver, proxy)
                } catch (e: ContractError) {
                    kspLogger.error(e.message!!, e.node)
                }
            }
        }
        val controllers = linkedSetOf<KSClassDeclaration>()
        for (symbol in resolver.getSymbolsWithAnnotation(listener.canonicalName)) {
            if (!symbol.validate()) {
                deferred.add(symbol)
                continue
            }
            val controller = (symbol as? KSFunctionDeclaration)?.parentDeclaration as? KSClassDeclaration
            if (controller == null) {
                kspLogger.error("@NatsListener requires a controller method", symbol)
            } else {
                controllers.add(controller)
            }
        }
        for (controller in controllers) {
            if (!controller.validate()) {
                deferred.add(controller)
                continue
            }
            try {
                generateListeners(controller)
            } catch (e: ContractError) {
                kspLogger.error(e.message!!, e.node)
            }
        }
        return deferred
    }

    private fun generatePublisher(owner: KSClassDeclaration, resolver: Resolver, proxy: KSClassDeclaration? = null) {
        requireContract(
            owner.classKind == ClassKind.INTERFACE && owner.typeParameters.isEmpty(), owner,
            "@NatsPublisher requires a non-generic interface"
        )
        val parents = owner.superTypes.map { it.resolve() }.filter { it.declaration.qualifiedName?.asString() != "kotlin.Any" }.toList()
        if (parents.size == 1 && parents.single().isType(atomicPublisher)) {
            generateAtomicPublisher(owner, parents.single(), resolver, proxy)
            return
        }
        requireContract(
            owner.superTypes.all { it.resolve().declaration.qualifiedName?.asString() == "kotlin.Any" }, owner,
            "@NatsPublisher does not support parent interfaces"
        )
        val first = generated.add("publisher:" + owner.qualifiedName!!.asString())
        if (!first && proxy == null) {
            return
        }
        val ownerType = owner.toClassName()
        val implType = ClassName(owner.packageName.asString(), owner.generatedClass("Impl"))
        // Tags are rendered as qualified class literals by graph generation; keep marker names legal in Kotlin.
        val moduleType = ClassName(owner.packageName.asString(), owner.generatedClass("PublisherModule").removePrefix("$"))
        val path = owner.annotation(publisher)!!.value("value") as String
        val prefix = providerPrefix(owner)
        val impl = owner.extendsKeepAop(implType.simpleName, resolver)
            .superclass(type("producer.AbstractNatsPublisher")).generated(NatsSymbolProcessor::class)
            .addSuperclassConstructorParameter("%S", path)
            .addSuperclassConstructorParameter("_client").addSuperclassConstructorParameter("_config")
            .addSuperclassConstructorParameter("_telemetry").addSuperclassConstructorParameter("_batch")
        val ctor = FunSpec.constructorBuilder().addParameter("_client", client)
            .addParameter("_config", publisherConfig).addParameter("_telemetry", publisherTelemetry)
        val module = module(moduleType.simpleName)
            .addFunction(configFunction(prefix + "_PublisherConfig", publisherConfig, ownerType, path))
        connectionProviders(
            module, prefix + "_Publisher", publisherConfig, ownerType, path,
            owner.qualifiedName!!.asString(), publisherTelemetry, publisherTelemetryFactory
        )
        val factory = FunSpec.builder(prefix + "_PublisherImpl").returns(ownerType)
            .addParameter(parameter(client, "_client", ownerType.canonicalName)).addParameter(parameter(publisherConfig, "_config", ownerType.canonicalName))
            .addParameter(parameter(publisherTelemetry, "_telemetry", ownerType.canonicalName))
        val arguments = CodeBlock.builder().add("_client, _config, _telemetry")
        fun addDependency(dependencyType: TypeName, name: String, tag: String?, factoryType: TypeName = dependencyType) {
            impl.addProperty(PropertySpec.builder(name, dependencyType, KModifier.PRIVATE).initializer(name).build())
            ctor.addParameter(name, dependencyType)
            factory.addParameter(parameter(factoryType, name, tag))
            arguments.add(", %N", name)
        }

        var index = 0
        for (method in owner.getDeclaredFunctions()) {
            if (!method.isAbstract) {
                continue
            }
            requireContract(
                method.typeParameters.isEmpty() && Modifier.SUSPEND !in method.modifiers, method,
                "NATS publisher methods must be non-generic; use CompletableFuture for async publishing"
            )
            val stem = "_method" + index++
            val isRequest = method.annotation(request) != null
            var data: KSValueParameter? = null
            var headersParam: KSValueParameter? = null
            var optionsParam: KSValueParameter? = null
            var callbackParam: KSValueParameter? = null
            var timeoutParam: KSValueParameter? = null
            for (param in method.parameters) {
                val paramType = param.type.resolve()
                when {
                    paramType.isType(headers) -> {
                        requireContract(headersParam == null, param, "Duplicate NATS Headers parameter")
                        headersParam = param
                    }

                    paramType.isType(options) -> {
                        requireContract(optionsParam == null, param, "Duplicate PublishOptions parameter")
                        optionsParam = param
                    }

                    paramType.isType(callback) -> {
                        requireContract(callbackParam == null, param, "Duplicate NATS callback parameter")
                        callbackParam = param
                    }

                    isRequest && paramType.isType(duration) -> {
                        requireContract(timeoutParam == null, param, "Duplicate request timeout")
                        timeoutParam = param
                    }

                    else -> {
                        requireContract(data == null, param, "NATS publisher expects one payload or record parameter")
                        data = param
                    }
                }
            }
            requireContract(data != null, method, "NATS publisher requires a payload, NatsProducerMessage<T> or Message")
            val payloadParam = data!!
            val payloadType = payloadParam.type.resolve()
            val raw = payloadType.isType(message)
            val dynamic = payloadType.isType(producerMessage)
            requireContract(payloadType.nullability != Nullability.NULLABLE || (!raw && !dynamic), payloadParam, "NATS message/record cannot be nullable")
            val subjectAnnotation = method.annotation(subject)
            requireContract(raw || dynamic || subjectAnnotation != null, method, "Typed NATS payload requires @NatsPublisher.Subject")
            requireContract(
                !(raw || dynamic) || (subjectAnnotation == null && headersParam == null), method,
                "NATS records carry their own subject and headers"
            )
            requireContract(!isRequest || (optionsParam == null && callbackParam == null), method, "NATS request cannot use PublishOptions or publish callback")
            val generatedMethod = method.overridingKeepAop(resolver)
            val messageExpression: CodeBlock
            if (raw) {
                generatedMethod.addStatement(
                    "val _observation = %L(%N.subject)", if (isRequest) {
                        "telemetry().observeRequest"
                    } else {
                        "observeSend"
                    }, payloadParam.name!!.asString()
                )
                    .beginControlFlow("try")
                messageExpression = CodeBlock.of("%N", payloadParam.name!!.asString())
            } else {
                val payload = if (dynamic) {
                    payloadType.argument(payloadParam)
                } else {
                    payloadType
                }
                val serializerName = stem + "Serializer"
                val serializerType = serializer.parameterizedBy(payload.payloadName())
                addDependency(serializerType, serializerName, payloadParam.parseTag() ?: payload.parseTag(), mapperType(payloadParam, serializer, serializerType))
                val subjectExpression: CodeBlock
                val bodyExpression: CodeBlock
                val replyExpression: CodeBlock
                if (dynamic) {
                    val dataName = payloadParam.name!!.asString()
                    subjectExpression = CodeBlock.of("%N.subject()", dataName)
                    bodyExpression = CodeBlock.of("%N.value()", dataName)
                    replyExpression = CodeBlock.of("%N.replyTo()", dataName)
                    generatedMethod.addStatement("val _headers = %N.headers()?.let { %T(it) } ?: %T()", dataName, headers, headers)
                } else {
                    val subjectTag = moduleType.nestedClass("Subject" + (index - 1))
                    module.addType(TypeSpec.classBuilder(subjectTag.simpleName).build())
                    val subjectPath = subjectAnnotation!!.value("value") as String
                    module.addFunction(
                        configFunction(
                            prefix + stem + "SubjectConfig", subjectConfig, subjectTag, if (subjectPath.startsWith(".")) {
                                path + subjectPath
                            } else {
                                subjectPath
                            }
                        )
                    )
                    val subjectName = stem + "Subject"
                    addDependency(subjectConfig, subjectName, subjectTag.canonicalName)
                    subjectExpression = CodeBlock.of("this.%N.subject()", subjectName)
                    bodyExpression = CodeBlock.of("%N", payloadParam.name!!.asString())
                    replyExpression = CodeBlock.of("null")
                    if (headersParam == null) {
                        generatedMethod.addStatement("val _headers = %T()", headers)
                    } else {
                        generatedMethod.addStatement("val _headers = %N?.let { %T(it) } ?: %T()", headersParam.name!!.asString(), headers, headers)
                    }
                }
                generatedMethod.addStatement(
                    "val _observation = %L(%L)", if (isRequest) {
                        "telemetry().observeRequest"
                    } else {
                        "observeSend"
                    }, subjectExpression
                )
                    .beginControlFlow("try").addStatement("_observation.observeData(%L)", bodyExpression)
                generatedMethod.addStatement(
                    "val _message = serializeMessage(%L, %L, _headers, %L, this.%N, _observation)",
                    subjectExpression, replyExpression, bodyExpression, serializerName
                )
                messageExpression = CodeBlock.of("_message")
            }
            val returnType = method.returnType!!.resolve()
            val async = returnType.isType(future) || returnType.isType(stage)
            val resultType = if (async) {
                returnType.argument(method)
            } else {
                returnType
            }
            if (isRequest) {
                requireContract(!resultType.isUnit(), method, "NATS request requires a response return type")
                val decoder: CodeBlock
                if (resultType.isType(message)) {
                    decoder = CodeBlock.of("{ _reply -> _reply }")
                } else {
                    val decoderName = stem + "Deserializer"
                    val decoderType = deserializer.parameterizedBy(resultType.payloadName())
                    addDependency(decoderType, decoderName, method.parseTag() ?: resultType.parseTag(), mapperType(method, deserializer, decoderType))
                    decoder = CodeBlock.of("this.%N", decoderName)
                }
                generatedMethod.addStatement(
                    "return %L(%L, %L, %L, _observation)", if (async) {
                    "requestAsync"
                } else {
                    "request"
                },
                    messageExpression, decoder, timeoutParam?.let { CodeBlock.of("%N", it.name!!.asString()) } ?: CodeBlock.of("null"))
            } else {
                val publishOptions = optionsParam?.let { CodeBlock.of("%N", it.name!!.asString()) } ?: CodeBlock.of("null")
                if (callbackParam != null) {
                    requireContract(returnType.isUnit(), method, "NATS callback publisher must return Unit")
                    generatedMethod.addStatement("publishCallback(%L, %L, %N, _observation)", messageExpression, publishOptions, callbackParam.name!!.asString())
                } else if (async) {
                    requireContract(resultType.isType(ack) || resultType.isUnit(), method, "Async NATS publish requires PublishAck or Void result")
                    generatedMethod.addStatement(
                        "return %L(%L, %L, _observation)", if (resultType.isType(ack)) {
                            "publishAsync"
                        } else {
                            "publishCompletionAsync"
                        }, messageExpression, publishOptions
                    )
                } else if (returnType.isType(ack)) {
                    generatedMethod.addStatement("return publishAcknowledged(%L, %L, _observation)", messageExpression, publishOptions)
                } else {
                    requireContract(returnType.isUnit(), method, "NATS publish returns Unit, PublishAck or a future; use @Request for reply")
                    generatedMethod.addStatement("publish(%L, %L, _observation)", messageExpression, publishOptions)
                }
            }
            generatedMethod.nextControlFlow("catch (_error: Throwable)").addStatement("_observation.end(_error)")
                .addStatement("throw _error").endControlFlow()
            impl.addFunction(generatedMethod.build())
        }
        ctor.addParameter("_batch", batchSink.copy(nullable = true))
        impl.primaryConstructor(ctor.build())
        val batchArguments = CodeBlock.builder().add("%L, _batch", arguments.build())
        arguments.add(", null")
        if (proxy != null) {
            val proxyConstructor = proxy.getConstructors().singleOrNull()
            requireContract(proxyConstructor != null, proxy, "NATS publisher AOP proxy requires one constructor")
            proxyConstructor!!.parameters.drop(ctor.build().parameters.size).forEach { param ->
                val paramName = param.name!!.asString()
                val spec = ParameterSpec.builder(paramName, param.type.toTypeName())
                param.annotations.forEach { spec.addAnnotation(it.toAnnotationSpec()) }
                factory.addParameter(spec.build())
                arguments.add(", %N", paramName)
                batchArguments.add(", %N", paramName)
            }
        }
        factory.addStatement("return %T(%L)", proxy?.toClassName() ?: implType, arguments.build())
        module.addFunction(factory.build())
        module.addFunction(
            FunSpec.builder(prefix + "_AtomicPublisherFactory")
                .returns(function.parameterizedBy(batchSink, ownerType)).addParameters(factory.build().parameters)
                .addStatement("return %T { _batch -> %T(%L) }", function, proxy?.toClassName() ?: implType, batchArguments.build()).build()
        )
        if (first) {
            write(owner, impl.build())
        }
        if (proxy != null || !CommonAopUtils.hasAopAnnotations(owner)) {
            if (generated.add("publisher-module:" + owner.qualifiedName!!.asString())) {
                write(owner, module.build())
            }
        }
    }

    private fun generateAtomicPublisher(owner: KSClassDeclaration, parent: KSType, resolver: Resolver, proxy: KSClassDeclaration?) {
        val payload = parent.argument(owner)
        val target = payload.declaration as? KSClassDeclaration
        requireContract(
            target != null && payload.nullability != Nullability.NULLABLE && target.annotation(publisher) != null
                && target.superTypes.all { it.resolve().declaration.qualifiedName?.asString() == "kotlin.Any" }, owner,
            "AtomicBatchPublisher requires a regular @NatsPublisher interface"
        )
        owner.getDeclaredFunctions().forEach { method ->
            requireContract(
                !method.isAbstract, method,
                "NATS atomic publisher must inherit the AtomicBatchPublisher contract without additional abstract methods"
            )
        }
        target!!.getDeclaredFunctions().filter { it.isAbstract }.forEach { method ->
            requireContract(
                method.returnType!!.resolve().isUnit() && method.annotation(request) == null
                    && method.parameters.none { it.type.resolve().isType(callback) || it.type.resolve().isType(options) }, method,
                "Atomic batch publisher methods must return Unit without @Request, callbacks or PublishOptions; use Headers for constraints"
            )
        }
        val first = generated.add("publisher:" + owner.qualifiedName!!.asString())
        if (!first && proxy == null) {
            return
        }
        val ownerType = owner.toClassName()
        val targetType = target.toClassName()
        val implType = ClassName(owner.packageName.asString(), owner.generatedClass("Impl"))
        val factoryType = function.parameterizedBy(batchSink, targetType)
        val ctor = FunSpec.constructorBuilder().addParameter("_client", client).addParameter("_publisherConfig", publisherConfig)
            .addParameter("_config", atomicConfig).addParameter("_telemetry", publisherTelemetry).addParameter("_factory", factoryType)
        val impl = owner.extendsKeepAop(implType.simpleName, resolver).generated(NatsSymbolProcessor::class)
            .superclass(type("producer.AtomicBatchPublisherImpl").parameterizedBy(targetType))
            .addSuperclassConstructorParameter("_client").addSuperclassConstructorParameter("_publisherConfig")
            .addSuperclassConstructorParameter("_config").addSuperclassConstructorParameter("_telemetry")
            .addSuperclassConstructorParameter("_factory").primaryConstructor(ctor.build())
        val prefix = providerPrefix(owner)
        val module = module(owner.generatedClass("PublisherModule").removePrefix("$"))
            .addFunction(configFunction(prefix + "_AtomicBatchConfig", atomicConfig, ownerType, owner.annotation(publisher)!!.value("value") as String))
        val factory = FunSpec.builder(prefix + "_AtomicPublisher").returns(ownerType)
            .addParameter(parameter(client, "_client", targetType.canonicalName))
            .addParameter(parameter(publisherConfig, "_publisherConfig", targetType.canonicalName))
            .addParameter(parameter(atomicConfig, "_config", ownerType.canonicalName))
            .addParameter(parameter(publisherTelemetry, "_telemetry", targetType.canonicalName)).addParameter("_factory", factoryType)
        val args = CodeBlock.builder().add("_client, _publisherConfig, _config, _telemetry, _factory")
        if (proxy != null) {
            val proxyConstructor = proxy.getConstructors().singleOrNull()
            requireContract(proxyConstructor != null, proxy, "NATS atomic publisher AOP proxy requires one constructor")
            proxyConstructor!!.parameters.drop(ctor.build().parameters.size).forEach { param ->
                val name = param.name!!.asString()
                val spec = ParameterSpec.builder(name, param.type.toTypeName())
                param.annotations.forEach { spec.addAnnotation(it.toAnnotationSpec()) }
                factory.addParameter(spec.build())
                args.add(", %N", name)
            }
        }
        module.addFunction(factory.addStatement("return %T(%L)", proxy?.toClassName() ?: implType, args.build()).build())
        if (first) {
            write(owner, impl.build())
        }
        if (proxy != null || !CommonAopUtils.hasAopAnnotations(owner)) {
            if (generated.add("publisher-module:" + owner.qualifiedName!!.asString())) {
                write(owner, module.build())
            }
        }
    }

    private fun generateListeners(owner: KSClassDeclaration) {
        requireContract(owner.typeParameters.isEmpty(), owner, "NATS listener controller must not be generic")
        if (!generated.add("listener:" + owner.qualifiedName!!.asString())) {
            return
        }
        val moduleType = ClassName(owner.packageName.asString(), owner.generatedClass("NatsListenerModule").removePrefix("$"))
        val module = module(moduleType.simpleName)
        val prefix = providerPrefix(owner) + "_"
        val names = hashSetOf<String>()
        for (method in owner.getDeclaredFunctions()) {
            val annotation = method.annotation(listener) ?: continue
            val name = method.simpleName.asString()
            requireContract(names.add(name), method, "Overloaded @NatsListener methods require distinct names")
            requireContract(
                Modifier.PRIVATE !in method.modifiers && Modifier.SUSPEND !in method.modifiers
                    && method.typeParameters.isEmpty(), method, "NATS listeners must be accessible, non-generic, synchronous methods"
            )
            val userTag = annotation.value("tag") as? KSType
            val listenerTag = if (userTag == null || userTag.isType(CommonClassNames.tag)) {
                val generatedTag = moduleType.nestedClass(name.replaceFirstChar { it.uppercaseChar() })
                module.addType(TypeSpec.classBuilder(generatedTag.simpleName).build())
                generatedTag
            } else {
                userTag.toClassName()
            }
            module.addFunction(configFunction(prefix + name + "Config", listenerConfig, listenerTag, annotation.value("value") as String))
            connectionProviders(
                module, prefix + name, listenerConfig, listenerTag, annotation.value("value") as String,
                owner.qualifiedName!!.asString() + "." + name, consumerTelemetry, consumerTelemetryFactory
            )
            var data: KSValueParameter? = null
            var batch = false
            var wrapped = false
            var catchesDeserialization = false
            for (param in method.parameters) {
                val paramType = param.type.resolve()
                if (paramType.isDeserializationException()) {
                    requireContract(
                        paramType.nullability == Nullability.NULLABLE, param,
                        "NATS listener deserialization exception parameter must be nullable"
                    )
                    catchesDeserialization = true
                    continue
                }
                if (paramType.isType(message) || paramType.isType(headers) || paramType.isType(connection)
                    || paramType.isType(pollObservation)
                ) {
                    continue
                }
                requireContract(data == null, param, "NATS listener accepts one payload, NatsMessage<T> or NatsMessages<T>")
                data = param
                batch = paramType.isType(consumerMessages)
                wrapped = paramType.isType(consumerMessage)
            }
            if (batch) {
                method.parameters.forEach { param ->
                    requireContract(
                        param == data || param.type.resolve().isType(connection) || param.type.resolve().isType(pollObservation),
                        param, "Batch NATS listener accepts NatsMessages<T>, optional Connection and NatsConsumerPollObservation"
                    )
                }
            }
            val dataType = data?.type?.resolve()
            val payloadType = dataType?.let {
                if (batch || wrapped) {
                    it.argument(data!!)
                } else {
                    it
                }
            }
            requireContract(
                !catchesDeserialization || data == null || wrapped || payloadType!!.nullability == Nullability.NULLABLE,
                method, "NATS listener payload must be nullable when binding a deserialization exception"
            )
            val payload = payloadType?.payloadName()?.let {
                if (!batch && !wrapped) {
                    it.copy(nullable = false)
                } else {
                    it
                }
            } ?: BYTE_ARRAY
            val factory = FunSpec.builder(prefix + name + "Container").returns(container.parameterizedBy(payload)).addTag(listenerTag)
                .addAnnotation(CommonClassNames.root)
                .addParameter("_controller", owner.toClassName()).addParameter(parameter(client, "_client", listenerTag.canonicalName))
                .addParameter(parameter(listenerConfig, "_config", listenerTag.canonicalName))
                .addParameter(parameter(consumerTelemetry, "_telemetry", listenerTag.canonicalName))
                .addParameter(parameter(configurer.parameterizedBy(consumerBuilder).copy(nullable = true), "_customizer", listenerTag.canonicalName))
            if (data != null) {
                factory.addParameter(parameter(mapperType(data, deserializer, deserializer.parameterizedBy(payload)), "_deserializer", data.parseTag() ?: payloadType!!.parseTag()))
            }
            val args = CodeBlock.builder()
            method.parameters.forEachIndexed { i, param ->
                if (i > 0) {
                    args.add(", ")
                }
                val paramType = param.type.resolve()
                when {
                    param == data -> args.add(
                        if (batch || wrapped) {
                            "_record"
                        } else if (catchesDeserialization) {
                            "_value"
                        } else {
                            "_record.value()"
                        }
                    )

                    paramType.isType(message) -> args.add("_record.message()")
                    paramType.isType(headers) -> args.add("_record.headers()")
                    paramType.isType(pollObservation) -> args.add("_poll")
                    paramType.isDeserializationException() -> args.add("_error")
                    else -> args.add("_client.connection()")
                }
            }
            val handlerType = type(
                if (batch) {
                    "consumer.NatsMessagesHandler"
                } else {
                    "consumer.NatsMessageHandler"
                }
            ).parameterizedBy(payload)
            val handler = CodeBlock.builder().add("%T { _poll, _record ->\n", handlerType)
            if (catchesDeserialization) {
                handler.add("  var _error: %T? = null\n", serializationException)
                if (data != null && !wrapped) {
                    handler.add("  var _value: %T = null\n", payload.copy(nullable = true))
                }
                handler.add("  try {\n")
                if (data != null && !wrapped) {
                    handler.add("    _value = _record.value()\n")
                } else {
                    handler.add("    _record.value()\n")
                }
                handler.add("  } catch (_failure: %T) {\n    _error = _failure\n  }\n", serializationException)
            }
            val returnType = method.returnType!!.resolve()
            if (returnType.isUnit()) {
                handler.add("  _controller.%N(%L)\n", name, args.build())
            } else {
                requireContract(
                    !batch && !returnType.isType(future) && !returnType.isType(stage), method,
                    "NATS listener replies require synchronous per-record return type"
                )
                factory.addParameter(parameter(mapperType(method, serializer, serializer.parameterizedBy(returnType.payloadName())), "_serializer", method.parseTag() ?: returnType.parseTag()))
                handler.add("  val _response = _controller.%N(%L)\n", name, args.build())
                    .add("  %T.reply(_record, _client, _response, _serializer)\n", type("consumer.NatsReplies"))
            }
            handler.add("}")
            factory.addStatement(
                "return %T(%S, _client, _config, %L, %L, _customizer, _telemetry)", container.parameterizedBy(payload),
                annotation.value("value") as String, if (data == null) {
                    CodeBlock.of("{ _message -> _message.data }")
                } else {
                    CodeBlock.of("_deserializer")
                }, handler.build()
            )
            module.addFunction(factory.build())
        }
        write(owner, module.build())
    }
}
