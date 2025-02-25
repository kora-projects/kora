package ru.tinkoff.kora.json.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.addOriginatingKSFile
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.generatedClass

class GeneratorModuleProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = GeneratorModuleProcessor(environment)
}

class GeneratorModuleProcessor(val env: SymbolProcessorEnvironment) : BaseSymbolProcessor(env) {
    val processor = JsonProcessor(env.codeGenerator)

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val modules = resolver.getSymbolsWithAnnotation(CommonClassNames.generatorModule.canonicalName)
        for (module in modules) {
            try {
                this.generateModule(module as KSClassDeclaration)
            } catch (e: ProcessingErrorException) {
                e.printError(env.logger)
            }
        }
        return listOf()
    }

    private fun generateModule(module: KSClassDeclaration) {
        val generatorModuleAnnotation = module.findAnnotation(CommonClassNames.generatorModule)
        val generatorModuleGenerator = generatorModuleAnnotation?.findValueNoDefault<KSType>("generator")
        if (generatorModuleGenerator == null || generatorModuleGenerator.toTypeName() != JsonTypes.json) {
            return
        }
        val typesToProcess = generatorModuleAnnotation.findValueNoDefault<List<KSType>>("types")
        if (typesToProcess.isNullOrEmpty()) {
            return
        }
        val packageName = module.packageName.asString()
        val builder = TypeSpec.interfaceBuilder(module.generatedClass(CommonClassNames.generatorModule))
            .addOriginatingKSFile(module)
            .addAnnotation(CommonClassNames.module)
            .generated(GeneratorModuleProcessor::class)
        var error = false
        for ((i, jsonType) in typesToProcess.withIndex()) {
            val jsonClassDecl = jsonType.declaration as KSClassDeclaration
            try {
                val readerClassName = ClassName(packageName, module.generatedClass(CommonClassNames.generatorModule) + "_${i}_JsonReader")
                val readerMethod = this.generateMapper(module, "reader$i", readerClassName, jsonClassDecl) { target, jsonElement -> processor.generateReader(target, jsonElement) }
                builder.addFunction(readerMethod)

                val writerClassName = ClassName(packageName, module.generatedClass(CommonClassNames.generatorModule) + "_${i}_JsonWriter")
                val writerMethod = this.generateMapper(module, "writer$i", writerClassName, jsonClassDecl) { target, jsonElement -> processor.generateReader(target, jsonElement) }
                builder.addFunction(writerMethod)
            } catch (e: ProcessingErrorException) {
                e.printError(env.logger)
                env.logger.error(e.errors[0].message, generatorModuleAnnotation.arguments.firstOrNull { it.name?.asString().equals("types") })
                error = true
            }
        }
        if (error) {
            return
        }
        FileSpec.get(packageName, builder.build()).writeTo(env.codeGenerator, false)
    }

    private fun generateMapper(module: KSClassDeclaration, methodName: String, mapperName: ClassName, jsonClassDecl: KSClassDeclaration, generator: (ClassName, KSClassDeclaration) -> TypeSpec): FunSpec {
        val packageName = module.packageName.asString()
        val mapper = generator(mapperName, jsonClassDecl)
        val mapperBuilder = mapper.toBuilder();
        mapperBuilder.originatingElements.clear()
        mapperBuilder.addOriginatingKSFile(module)

        FileSpec.get(packageName, mapperBuilder.build()).writeTo(env.codeGenerator, false)

        return this.mapperMethod(methodName, mapperName, mapper);
    }

    private fun mapperMethod(methodName: String, mapperName: ClassName, mapper: TypeSpec): FunSpec {
        val mapperConstructor = mapper.primaryConstructor!!
        return FunSpec.builder(methodName)
            .addParameters(mapperConstructor.parameters)
            .returns(mapperName)
            .addStatement("return %T(%L)", mapperName, mapperConstructor.parameters.map { CodeBlock.of("%N", it.name) }.joinToCode(", "))
            .build()
    }
}
