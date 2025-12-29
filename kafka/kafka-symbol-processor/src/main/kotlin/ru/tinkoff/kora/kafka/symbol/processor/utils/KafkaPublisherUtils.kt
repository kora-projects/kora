package ru.tinkoff.kora.kafka.symbol.processor.utils

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.TagUtils
import ru.tinkoff.kora.ksp.common.TagUtils.parseTag
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix
import java.util.*


object KafkaPublisherUtils {


    data class PublisherData(
        val keyType: TypeName?,
        val keyTag: String?,
        val valueType: TypeName,
        val valueTag: String?,
        val keyVar: KSValueParameter?,
        val valueVar: KSValueParameter?,
        val headersVar: KSValueParameter?,
        val recordVar: KSValueParameter?,
        val callback: KSValueParameter?
    )


    fun parsePublisherType(method: KSFunctionDeclaration): PublisherData {
        var key = null as KSValueParameter?
        var value = null as KSValueParameter?
        var headers = null as KSValueParameter?
        var record = null as KSValueParameter?
        var producerCallback = null as KSValueParameter?
        for (parameter in method.parameters) {
            val type = parameter.type.toTypeName()
            if (type.isProducerCallback()) {
                if (producerCallback != null) {
                    throw ProcessingErrorException("Invalid publisher signature: only one Callback parameter is allowed", parameter);
                }
                producerCallback = parameter;
                continue;
            }
            if (type.isHeaders()) {
                if (record != null) {
                    throw ProcessingErrorException("Invalid publisher signature: Headers parameter can't be used with record parameter", parameter);
                }
                if (headers != null) {
                    throw ProcessingErrorException("Invalid publisher signature: only one Headers parameter is allowed", parameter);
                }
                headers = parameter;
                continue;
            }
            if (type.isProducerRecord()) {
                if (value != null || headers != null) {
                    throw ProcessingErrorException("Invalid publisher signature: Record parameter can't be combined with other parameters", parameter);
                }
                if (method.isAnnotationPresent(KafkaClassNames.kafkaTopicAnnotation)) {
                    throw ProcessingErrorException("Invalid publisher signature: Record parameter can't be combined @Topic annotation", parameter);
                }
                record = parameter;
                continue;
            }
            if (record != null) {
                throw ProcessingErrorException("Invalid publisher signature: Record parameter can't be combined with key or value parameters", parameter);
            }
            if (key != null) {
                throw ProcessingErrorException("Invalid publisher signature: only ProducerRecord or Headers, key and value parameters are allowed", parameter);
            }
            if (value != null) {
                key = value;
            }
            value = parameter;
        }
        if (record != null) {
            val recordType = record.type.resolve()
            val recordTypeName = recordType.toTypeName().copy(false, listOf()) as ParameterizedTypeName
            val keyType = recordTypeName.typeArguments[0]
            val valueType = recordTypeName.typeArguments[1]
            val keyTag = TagUtils.parseTagValue(recordType.arguments[0])
            val valueTag = TagUtils.parseTagValue(recordType.arguments[1])
            return PublisherData(keyType, keyTag, valueType, valueTag, key, value, headers, record, producerCallback)
        }
        if (!method.isAnnotationPresent(KafkaClassNames.kafkaTopicAnnotation)) {
            throw ProcessingErrorException("Invalid publisher signature: key/value/headers signature requires @Topic annotation", method);
        }
        requireNotNull(value)
        val valueType = value.type.resolve().toTypeName().copy(false, listOf())
        val valueTag = value.parseTag()
        if (key == null) {
            return PublisherData(null, null, valueType, valueTag, key, value, headers, record, producerCallback);
        }
        val keyType = key.type.resolve().toTypeName().copy(false, listOf())
        val keyTag = key.parseTag()
        return PublisherData(keyType, keyTag, valueType, valueTag, key, value, headers, record, producerCallback)
    }

    private fun TypeName.isProducerCallback() = this == KafkaClassNames.producerCallback
    private fun TypeName.isHeaders() = this == KafkaClassNames.headers
    private fun TypeName.isProducerRecord() = this is ParameterizedTypeName && this.rawType == KafkaClassNames.producerRecord

}


data class TransactionalPublisher(val declaration: KSClassDeclaration, val publisher: Publisher) {

    fun config(): String = declaration.annotations
        .flatMap { a -> a.arguments }
        .filter { a -> a.name!!.getShortName() == "value" }
        .map { a -> a.value.toString() }
        .first()
}

data class Publisher(
    val declaration: KSClassDeclaration,
    val superDeclaration: KSType,
    val key: RecordParameter,
    val value: RecordParameter,
    val methods: List<PublisherMethod>
) {

    fun delegate(): Delegate {
        return Delegate(ByteArray::class.asTypeName())
    }

    fun config(): String = declaration.annotations
        .filter { a -> a.annotationType.resolve().declaration.let { it as KSClassDeclaration }.toClassName() == KafkaClassNames.kafkaPublisherAnnotation }
        .flatMap { a -> a.arguments }
        .filter { a -> a.name!!.getShortName() == "value" }
        .map { a -> a.value.toString() }
        .first()
}

class Delegate(val type: TypeName, val serializer: Serializer) {
    constructor(argument: TypeName) : this(
        KafkaClassNames.kafkaProducer.parameterizedBy(argument, argument),
        Serializer(argument)
    )
}

interface RecordParameter {
    fun type(): TypeName
    fun tag(): String?
    fun serializer(): Serializer
}

data class RecordType(val type: TypeName, val tag: String?) : RecordParameter {
    constructor(value: KSTypeArgument) : this(value.toTypeName(), TagUtils.parseTagValue(value))

    override fun type(): TypeName = type

    override fun tag() = tag

    override fun serializer(): Serializer {
        return Serializer(type(), tag())
    }
}

data class Serializer(val type: TypeName, val argument: TypeName, val tag: String?) {

    constructor(argument: TypeName) : this(KafkaClassNames.serializer.parameterizedBy(argument), argument, null)

    constructor(
        argument: TypeName,
        tags: String?
    ) : this(KafkaClassNames.serializer.parameterizedBy(argument), argument, tags)
}

data class RecordElement(val element: KSValueParameter, val type: TypeName, val tag: String?) : RecordParameter {

    constructor(element: KSValueParameter) : this(element, element.type.toTypeName(), element.parseTag() ?: element.type.parseTag())

    override fun type(): TypeName = type

    override fun tag() = tag

    override fun serializer(): Serializer {
        return Serializer(type, tag)
    }
}

class PublisherMethod(val element: KSFunctionDeclaration, val topicConfig: String, val parameters: Parameters) {
    class Parameters(val key: RecordElement?, val value: RecordElement, val headers: KSValueParameter?)

    fun topicConfigTag(): ClassName {
        val packageElement = element.packageName
        val topicTagSuffix = topicConfig
            .replace(".", "_")
            .replace("-", "_")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        return ClassName(
            packageElement.toString(),
            element.getOuterClassesAsPrefix() + "PublisherModule",
            "TopicConfigTag_$topicTagSuffix"
        )
    }
}

fun parseTransactionalPublisher(type: KSClassDeclaration): TransactionalPublisher {
    val publisher = parsePublisher(type.superTypes.first().resolve().arguments.first().type!!.resolve().declaration as KSClassDeclaration)
    return TransactionalPublisher(type, publisher)
}

fun parsePublisher(type: KSClassDeclaration): Publisher {
    val supertypes = type.superTypes.toList()
    val supertype = supertypes[0].resolve()

    val publisherMethods = parseMethods(type)
    val key = supertype.arguments[0]
    val value = supertype.arguments[1]
    return Publisher(type, supertype, RecordType(key), RecordType(value), publisherMethods)
}

private fun parseMethods(type: KSClassDeclaration): List<PublisherMethod> {
    val methods = ArrayList<PublisherMethod>()
    for (element in type.getDeclaredFunctions()) {
        val topicAnnotation = element.annotations.firstOrNull { it.annotationType.resolve().declaration.let { it as KSClassDeclaration }.toClassName() == KafkaClassNames.kafkaTopicAnnotation }
        if (topicAnnotation != null) {
            val pathToTopicConfig = topicAnnotation.arguments
                .filter { it.name!!.getShortName() == "value" }
                .map { it.value.toString() }
                .first()

            val parameters: PublisherMethod.Parameters = parseParameters(element)
            methods.add(PublisherMethod(element, pathToTopicConfig, parameters))
        }
    }
    return methods
}

private fun parseParameters(method: KSFunctionDeclaration): PublisherMethod.Parameters {
    var keyParameter: RecordElement? = null
    var valueParameter: RecordElement? = null
    var headersParameter: KSValueParameter? = null
    for (parameter in method.parameters) {
        if (parameter.type.resolve().declaration.let { it as KSClassDeclaration }.toClassName() == KafkaClassNames.headers) {
            headersParameter = parameter
        } else if (valueParameter == null) {
            valueParameter = RecordElement(parameter)
        } else if (keyParameter == null) {
            keyParameter = valueParameter
            valueParameter = RecordElement(parameter)
        } else {
            val message =
                "@KafkaPublisher method has unknown type parameter '${parameter.name!!.getQualifier()}'. Previous unknown type parameters are: '${keyParameter.element.name!!.getQualifier()}'(detected as key), '${valueParameter.element.name!!.getQualifier()}'(detected as value)"
            throw ProcessingErrorException(message, parameter)
        }
    }
    return PublisherMethod.Parameters(keyParameter, valueParameter!!, headersParameter)
}
