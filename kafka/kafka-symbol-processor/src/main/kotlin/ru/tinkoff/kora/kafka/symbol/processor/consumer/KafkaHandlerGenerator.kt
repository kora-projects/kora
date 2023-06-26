package ru.tinkoff.kora.kafka.symbol.processor.consumer

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.recordHandler
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.recordKeyDeserializationException
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.recordValueDeserializationException
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.recordsHandler
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.handlerFunName
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.tagTypeName
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.TagUtils.parseTags
import ru.tinkoff.kora.ksp.common.TagUtils.toTagAnnotation
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

class KafkaHandlerGenerator(private val kspLogger: KSPLogger, resolver: Resolver) {
    val dispatchers = ClassName("kotlinx.coroutines", "Dispatchers")
    val context = ClassName("ru.tinkoff.kora.common", "Context")

    fun generate(functionDeclaration: KSFunctionDeclaration, parameters: List<ConsumerParameter>): HandlerFunction {
        val controller = functionDeclaration.parentDeclaration as KSClassDeclaration
        val tagName = functionDeclaration.tagTypeName()
        val b = FunSpec.builder(functionDeclaration.handlerFunName())
            .addParameter("controller", controller.toClassName())
            .addAnnotation(listOf(tagName).toTagAnnotation())

        val hasRecords = parameters.any { it is ConsumerParameter.Records }
        val hasRecord = parameters.any { it is ConsumerParameter.Record }

        return when {
            hasRecords -> generateRecords(b, functionDeclaration, parameters)
            hasRecord -> generateRecord(b, functionDeclaration, parameters)
            else -> generateKeyValue(b, functionDeclaration, parameters)
        }
    }

    data class HandlerFunction(val funSpec: FunSpec, val keyType: TypeName, val keyTag: Set<String>, val valueType: TypeName, val valueTag: Set<String>)

    fun generateRecord(b: FunSpec.Builder, function: KSFunctionDeclaration, parameters: List<ConsumerParameter>): HandlerFunction {
        val recordParameter = parameters.first { it is ConsumerParameter.Record } as ConsumerParameter.Record
        val recordType = recordParameter.parameter.type.toTypeName() as ParameterizedTypeName
        var keyType = recordType.typeArguments[0].copy(false)
        val valueType = recordType.typeArguments[1].copy(false)
        if (keyType == STAR || keyType == ANY) {
            keyType = BYTE_ARRAY
        } else if (keyType !is ParameterizedTypeName && keyType !is ClassName) {
            val message = "Kafka listener method has invalid key type $keyType"
            throw ProcessingErrorException(message, function)
        }
        if (valueType !is ParameterizedTypeName && valueType !is ClassName) {
            val message = "Kafka listener method has invalid value type $valueType"
            throw ProcessingErrorException(message, function)
        }
        val catchesKeyException = parameters.any { it is ConsumerParameter.KeyDeserializationException || it is ConsumerParameter.Exception }
        val catchesValueException = parameters.any { it is ConsumerParameter.ValueDeserializationException || it is ConsumerParameter.Exception }
        val handlerType = recordHandler.parameterizedBy(keyType, valueType)
        b.returns(handlerType)
        b.controlFlow("return %T { consumer, tctx, record ->", handlerType) {
            if (catchesKeyException || catchesValueException) {
                if (catchesKeyException) addStatement("var keyException: %T? = null", recordKeyDeserializationException)
                if (catchesValueException) addStatement("var valueException: %T? = null", recordValueDeserializationException)
                controlFlow("try") {
                    if (catchesKeyException) {
                        addStatement("record.key()")
                    }
                    if (catchesValueException) {
                        addStatement("record.value()")
                    }
                    if (catchesKeyException) {
                        nextControlFlow("catch (e: %T)", recordKeyDeserializationException)
                        addStatement("keyException = e")
                    }
                    if (catchesValueException) {
                        nextControlFlow("catch (e: %T)", recordValueDeserializationException)
                        addStatement("valueException = e")
                    }
                }
            }

            if (function.modifiers.contains(Modifier.SUSPEND)) {
                b.beginControlFlow("kotlinx.coroutines.runBlocking(%T.Kotlin.inject(%T.Unconfined, %T.current()))", context, dispatchers, context)
            }

            addCode("controller.%N(", function.simpleName.asString())
            for ((i, it) in parameters.withIndex()) {
                if (i > 0) addCode(", ")
                addCode(when (it) {
                    is ConsumerParameter.Consumer -> "consumer"
                    is ConsumerParameter.Record -> "record"
                    is ConsumerParameter.KeyDeserializationException -> "keyException"
                    is ConsumerParameter.ValueDeserializationException -> "valueException"
                    is ConsumerParameter.Exception -> "keyException ?: valueException"
                    else -> throw ProcessingErrorException(
                        "Record listener can't have parameter of type ${it.parameter.type}, only consumer, record, RecordKeyDeserializationException, RecordValueDeserializationException and Exception are allowed",
                        it.parameter
                    )
                })
            }
            addCode(")\n")
            if (function.modifiers.contains(Modifier.SUSPEND)) {
                b.endControlFlow()
            }
        }
        val keyTag = recordParameter.key.parseTags()
        val valueTag = recordParameter.key.parseTags()

        return HandlerFunction(b.build(), keyType, keyTag, valueType, valueTag)
    }

    fun generateRecords(b: FunSpec.Builder, function: KSFunctionDeclaration, parameters: List<ConsumerParameter>): HandlerFunction {
        val recordsParameter = parameters.first { it is ConsumerParameter.Records } as ConsumerParameter.Records

        var keyTypeName = recordsParameter.key.toTypeName().copy(false)
        val valueTypeName = recordsParameter.value.toTypeName().copy(false)

        if (keyTypeName == STAR || keyTypeName == ANY) {
            keyTypeName = BYTE_ARRAY
        } else if (keyTypeName !is ParameterizedTypeName && keyTypeName !is ClassName) {
            val message = "Kafka listener method has invalid key type $keyTypeName"
            throw ProcessingErrorException(message, function)
        }
        if (valueTypeName !is ParameterizedTypeName && valueTypeName !is ClassName) {
            val message = "Kafka listener method has invalid value type $valueTypeName"
            throw ProcessingErrorException(message, function)
        }
        val handlerType = recordsHandler.parameterizedBy(keyTypeName, valueTypeName)
        b.returns(handlerType)
        b.controlFlow("return %T { consumer, tctx, records ->", handlerType) {
            if (function.modifiers.contains(Modifier.SUSPEND)) {
                b.beginControlFlow("kotlinx.coroutines.runBlocking(%T.Kotlin.inject(%T.Unconfined, %T.current()))", context, dispatchers, context)
            }
            addCode("controller.%N(", function.simpleName.asString())
            for ((i, it) in parameters.withIndex()) {
                if (i > 0) addCode(", ")
                addCode(when (it) {
                    is ConsumerParameter.Consumer -> "consumer"
                    is ConsumerParameter.RecordsTelemetry -> "tctx"
                    is ConsumerParameter.Records -> "records"
                    else -> throw ProcessingErrorException(
                        "Records listener can't have parameter of type ${it.parameter.type}, only consumer, records and records telemetry are allowed",
                        it.parameter
                    )
                })
            }
            addCode(")\n")
            if (function.modifiers.contains(Modifier.SUSPEND)) {
                b.endControlFlow()
            }
        }
        return HandlerFunction(b.build(), keyTypeName, setOf(), valueTypeName, setOf())
    }

    private fun generateKeyValue(b: FunSpec.Builder, functionDeclaration: KSFunctionDeclaration, parameters: List<ConsumerParameter>): HandlerFunction {
        var keyParameter: ConsumerParameter.Unknown? = null
        var valueParameter: ConsumerParameter.Unknown? = null
        for (parameter in parameters) {
            if (parameter is ConsumerParameter.Unknown) {
                if (valueParameter == null) {
                    valueParameter = parameter
                } else if (keyParameter == null) {
                    keyParameter = valueParameter
                    valueParameter = parameter
                } else {
                    val message = "Kafka listener method has unknown type parameter '${parameter.parameter.name?.asString()}'. " +
                        "Previous unknown type parameters are: '${keyParameter.parameter.name?.asString()}'(detected as key), " +
                        "'${valueParameter.parameter.name?.asString()}'(detected as value)"
                    throw ProcessingErrorException(message, parameter.parameter)
                }
            }
        }
        if (valueParameter == null) {
            val message = "Kafka listener method should have one of ConsumerRecord, ConsumerRecords or non service type parameters"
            throw ProcessingErrorException(message, functionDeclaration)
        }
        var keyType = keyParameter?.parameter?.type?.toTypeName()?.copy(false)
        if (keyType != null && keyType !is ClassName && keyType !is ParameterizedTypeName) {
            val message = "Kafka listener method has invalid key type $keyType"
            throw ProcessingErrorException(message, functionDeclaration)
        }
        val valueType = valueParameter.parameter.type.toTypeName().copy(false)
        if (valueType !is ClassName && valueType !is ParameterizedTypeName) {
            val message = "Kafka listener method has invalid value type $valueType"
            throw ProcessingErrorException(message, functionDeclaration)
        }
        if (keyType == null) {
            keyType = BYTE_ARRAY
        }


        val catchesKeyException = parameters.any { it is ConsumerParameter.KeyDeserializationException || it is ConsumerParameter.Exception }
        val catchesValueException = parameters.any { it is ConsumerParameter.ValueDeserializationException || it is ConsumerParameter.Exception }

        val handlerType = recordHandler.parameterizedBy(keyType, valueType)
        b.returns(handlerType)
        b.addCode(CodeBlock.builder().controlFlow("return %T { consumer, tctx, record ->", handlerType) {
            if (catchesKeyException) addStatement("var keyException: %T? = null", recordKeyDeserializationException)
            if (catchesValueException) addStatement("var valueException: %T? = null", recordValueDeserializationException)
            if (keyParameter != null) addStatement("var key: %T? = null", keyType)
            addStatement("var value: %T? = null", valueType)
            if (catchesKeyException || catchesValueException) {
                beginControlFlow("try")
            }
            if (keyParameter != null) {
                addStatement("key = record.key()")
            } else if (catchesKeyException) {
                addStatement("record.key()")
            }
            addStatement("value = record.value()")
            if (catchesKeyException) {
                nextControlFlow("catch (e: %T)", recordKeyDeserializationException)
                addStatement("keyException = e")
            }
            if (catchesValueException) {
                nextControlFlow("catch (e: %T)", recordValueDeserializationException)
                addStatement("valueException = e")
            }
            if (catchesKeyException || catchesValueException) {
                endControlFlow()
            }
            if (functionDeclaration.modifiers.contains(Modifier.SUSPEND)) {
                beginControlFlow("kotlinx.coroutines.runBlocking(%T.Kotlin.inject(%T.Unconfined, %T.current()))", context, dispatchers, context)
            }

            add("controller.%N(", functionDeclaration.simpleName.asString())
            var keySeen = false
            for ((i, parameter) in parameters.withIndex()) {
                if (i > 0) add(", ")
                when (parameter) {
                    is ConsumerParameter.Consumer -> add("consumer")
                    is ConsumerParameter.Exception -> add("keyException ?: valueException")
                    is ConsumerParameter.KeyDeserializationException -> add("keyException")
                    is ConsumerParameter.ValueDeserializationException -> add("valueException")
                    is ConsumerParameter.Unknown -> if (keyParameter == null || keySeen) {
                        add("value")
                    } else {
                        keySeen = true
                        add("key")
                    }

                    else -> {
                        val msg = "Record listener can't have parameter of type ${parameter.parameter.type}, only consumer, record, record key, record value, exception and record telemetry are allowed"
                        throw ProcessingErrorException(msg, parameter.parameter)
                    }
                }
            }

            add(")\n")
            if (functionDeclaration.modifiers.contains(Modifier.SUSPEND)) {
                endControlFlow()
            }
        }.build())
        val keyTag = keyParameter?.parameter?.parseTags() ?: setOf()
        val valueTag = valueParameter.parameter.parseTags()

        return HandlerFunction(b.build(), keyType, keyTag, valueType, valueTag)
    }

}
