package ru.tinkoff.kora.kora.app.ksp.app

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionFactory
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.visitClass

class AppWithProcessorExtensionProcessors {
    class TestExtensionExtensionFactory : ExtensionFactory {
        override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension {
            return TestExtension(resolver, codeGenerator)
        }
    }

    class TestExtension(val resolver: Resolver, val codeGenerator: CodeGenerator) : KoraExtension {
        private val interfaceDeclaration = resolver.getClassDeclarationByName(AppWithProcessorExtension.Interface1::class.qualifiedName!!)!!
        private val interfaceType = interfaceDeclaration.asStarProjectedType()
        override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)? {
            if (type != interfaceType) {
                return null
            }
            val packageName = interfaceDeclaration.packageName.asString()
            val typeName = "AppWithExtensionInterface1Impl"
            return generatedByProcessorWithName(resolver, interfaceDeclaration, "AppWithExtensionInterface1Impl")
        }
    }

    class TestProcessorProvider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return TestProcessor(environment.codeGenerator)
        }
    }

    class TestProcessor(val codeGenerator: CodeGenerator) : SymbolProcessor {
        private lateinit var interfaceDeclaration: KSClassDeclaration

        override fun process(resolver: Resolver): List<KSAnnotated> {
            interfaceDeclaration = resolver.getClassDeclarationByName(AppWithProcessorExtension.Interface1::class.qualifiedName!!)!!
            val symbols = resolver.getSymbolsWithAnnotation(AppWithProcessorExtension.TestAnnotation::class.qualifiedName!!).toList()
            symbols.forEach {
                it.visitClass { declaration ->
                    val packageName = interfaceDeclaration.packageName.asString()
                    val typeName = "AppWithExtensionInterface1Impl"
                    val type = TypeSpec.classBuilder(typeName)
                        .generated(TestProcessor::class)
                        .addModifiers(KModifier.PUBLIC)
                        .addSuperinterface(AppWithProcessorExtension.Interface1::class)
                        .build()
                    val fileSpec = FileSpec.builder(packageName, typeName).addType(type).build()
                    fileSpec.writeTo(codeGenerator, false)
                }
            }
            return emptyList()
        }
    }



}

