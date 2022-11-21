package ru.tinkoff.kora.kafka.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.Module

@KspExperimental
class KafkaModuleGenerator(
    private val kafkaConsumerGenerator: KafkaConsumerGenerator,
    private val kafkaConfigGenerator: KafkaConfigGenerator
) {
    fun generateModule(declaration: KSClassDeclaration): FileSpec {
        val classBuilder = TypeSpec.interfaceBuilder(declaration.simpleName.asString() + "Module")
            .addOriginatingKSFile(declaration.containingFile!!)
        if (declaration.isAnnotationPresent(Component::class)) {
            classBuilder.addAnnotation(AnnotationSpec.builder(Module::class).build())
        }
        val functions = declaration.getDeclaredFunctions()
        functions.forEach { function ->
            val configTagData = kafkaConfigGenerator.generate(function)
            configTagData?.configFunction?.let { classBuilder.addFunction(it) }
            configTagData?.tag?.let { classBuilder.addType(it) }
            kafkaConsumerGenerator.generate(function)?.let { classBuilder.addFunction(it) }
        }
        val packageName = declaration.packageName.asString()
        return FileSpec.get(packageName, classBuilder.build())
    }
}
