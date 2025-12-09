package ru.tinkoff.kora.kafka.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.THROWABLE
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.consumer
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.consumerRecord
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.consumerRecords
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.recordKeyDeserializationException
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.recordValueDeserializationException
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault

object KafkaUtils {
    fun KSFunctionDeclaration.moduleName(suffix: String): String {
        val classDecl = this.parentDeclaration!!
        val prefix = classDecl.simpleName.asString().replaceFirstChar { it.uppercaseChar() }
        val function = this.simpleName.asString().replaceFirstChar { it.uppercaseChar() }

        return "${prefix}${function}${suffix}"
    }

    fun KSFunctionDeclaration.findConsumerUserTag(): TypeName? {
        val listener = findAnnotation(KafkaClassNames.kafkaListener) ?: return null
        val tag = listener.findValueNoDefault<KSType>("tag")
        if (tag == null) {
            return null
        }

        return tag.toTypeName()
    }

    fun KSFunctionDeclaration.consumerTag(): TypeName {
        val userTags = findConsumerUserTag()
        return userTags ?: tagType()
    }

    fun KSFunctionDeclaration.tagType() = ClassName(packageName.asString(), parentDeclaration!!.simpleName.asString() + "Module", tagTypeName())
    fun KSFunctionDeclaration.tagTypeName() = moduleName("Tag")
    fun KSFunctionDeclaration.containerFunName() = moduleName("Container").replaceFirstChar { it.lowercaseChar() }
    fun KSFunctionDeclaration.handlerFunName() = moduleName("Handler").replaceFirstChar { it.lowercaseChar() }
    fun KSFunctionDeclaration.configFunName() = moduleName("Config").replaceFirstChar { it.lowercaseChar() }

    fun KSType.isConsumerRecord() = declaration.let { it is KSClassDeclaration && it.toClassName() == consumerRecord }

    fun KSType.isConsumerRecords() = declaration.let { it is KSClassDeclaration && it.toClassName() == consumerRecords }

    fun KSType.isKeyDeserializationException() = declaration.let { it is KSClassDeclaration && it.toClassName() == recordKeyDeserializationException }

    fun KSType.isValueDeserializationException() = declaration.let { it is KSClassDeclaration && it.toClassName() == recordValueDeserializationException }

    fun KSType.isAnyException() = toTypeName().copy(false).let {
        it is ClassName && (it == THROWABLE || it.toString() == "kotlin.Exception" || it.toString() == "java.lang.Exception" || it.toString() == "java.lang.Throwable")
    }

    fun KSType.isConsumer() = declaration.let { it is KSClassDeclaration && it.toClassName() == consumer }
}


