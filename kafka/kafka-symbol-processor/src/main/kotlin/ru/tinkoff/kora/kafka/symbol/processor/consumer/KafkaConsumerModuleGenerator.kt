package ru.tinkoff.kora.kafka.symbol.processor.consumer

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated

class KafkaConsumerModuleGenerator(
    private val kafkaHandlerGenerator: KafkaHandlerGenerator,
    private val kafkaConfigGenerator: KafkaConsumerConfigGenerator,
    private val kafkaContainerGenerator: KafkaContainerGenerator
) {
    fun generateModule(declaration: KSClassDeclaration): FileSpec {
        val classBuilder = TypeSpec.interfaceBuilder(declaration.simpleName.asString() + "Module")
            .addOriginatingKSFile(declaration.containingFile!!)
            .generated(KafkaConsumerModuleGenerator::class)
        classBuilder.addAnnotation(AnnotationSpec.builder(CommonClassNames.module).build())
        for (function in declaration.getDeclaredFunctions()) {
            val kafkaListener = function.findAnnotation(KafkaClassNames.kafkaListener)
            if (kafkaListener == null) {
                continue
            }
            val configTagData = kafkaConfigGenerator.generate(function, kafkaListener)
            classBuilder.addFunction(configTagData.configFunction)
            classBuilder.addType(configTagData.tag)

            val parameters = ConsumerParameter.parseParameters(function)

            val handler = kafkaHandlerGenerator.generate(function, parameters)
            classBuilder.addFunction(handler.funSpec)

            val container = kafkaContainerGenerator.generate(declaration, function, handler, parameters)
            classBuilder.addFunction(container)
        }
        val packageName = declaration.packageName.asString()
        return FileSpec.get(packageName, classBuilder.build())
    }
}
