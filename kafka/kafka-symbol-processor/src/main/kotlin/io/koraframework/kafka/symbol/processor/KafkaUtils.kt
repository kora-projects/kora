package io.koraframework.kafka.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.THROWABLE
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.kafka.symbol.processor.KafkaClassNames.consumer
import io.koraframework.kafka.symbol.processor.KafkaClassNames.consumerRecord
import io.koraframework.kafka.symbol.processor.KafkaClassNames.consumerRecords
import io.koraframework.kafka.symbol.processor.KafkaClassNames.recordKeyDeserializationException
import io.koraframework.kafka.symbol.processor.KafkaClassNames.recordValueDeserializationException
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault

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

    // A parameter type that is not resolvable in the current round has no qualified name, and
    // toClassName() fails its own precondition on it ("Required value was null"), taking KSP down
    // before the listener can be deferred. Comparing the qualified name keeps the check total.
    private fun KSType.isClass(className: ClassName) =
        declaration.let { it is KSClassDeclaration && it.qualifiedName?.asString() == className.canonicalName }

    fun KSType.isConsumerRecord() = isClass(consumerRecord)

    fun KSType.isConsumerRecords() = isClass(consumerRecords)

    fun KSType.isKeyDeserializationException() = isClass(recordKeyDeserializationException)

    fun KSType.isValueDeserializationException() = isClass(recordValueDeserializationException)

    fun KSType.isAnyException() = toTypeName().copy(false).let {
        it is ClassName && (it == THROWABLE || it.toString() == "kotlin.Exception" || it.toString() == "java.lang.Exception" || it.toString() == "java.lang.Throwable")
    }

    fun KSType.isConsumer() = isClass(consumer)
}


