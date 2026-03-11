package io.koraframework.kafka.symbol.processor.consumer

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import io.koraframework.kafka.symbol.processor.KafkaClassNames
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated

class KafkaConsumerModuleGenerator(
    private val kafkaHandlerGenerator: KafkaHandlerGenerator,
    private val kafkaConfigGenerator: KafkaConsumerConfigGenerator,
    private val kafkaContainerGenerator: KafkaContainerGenerator
) {
    fun generateModule(declaration: KSClassDeclaration): FileSpec {
        val classBuilder = TypeSpec.interfaceBuilder(declaration.simpleName.asString() + "Module")
            .addOriginatingKSFile(declaration)
            .generated(KafkaConsumerModuleGenerator::class)

        classBuilder.addAnnotation(AnnotationSpec.builder(CommonClassNames.module).build())
        for (function in declaration.getDeclaredFunctions()) {
            val kafkaListener = function.findAnnotation(KafkaClassNames.kafkaListener)
            if (kafkaListener == null) {
                continue
            }

            val configTagData = kafkaConfigGenerator.generate(function, kafkaListener)
            classBuilder.addFunction(configTagData.configFunction)
            if (configTagData.tag != null) {
                classBuilder.addType(configTagData.tag)
            }

            val parameters = ConsumerParameter.parseParameters(function)

            val handler = kafkaHandlerGenerator.generate(function, parameters)
            classBuilder.addFunction(handler.funSpec)

            val container = kafkaContainerGenerator.generate(function, kafkaListener, handler, parameters)
            classBuilder.addFunction(container)
        }
        val packageName = declaration.packageName.asString()
        return FileSpec.get(packageName, classBuilder.build())
    }
}
