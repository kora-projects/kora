package ru.tinkoff.kora.kora.app.ksp.app

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionFactory
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension

@KoraApp
interface AppWithExtension {
    // factory: generic component, accepts its genetic TypeRef as arguments
    fun test1(class1: Interface1): Class1 {
        return object : Class1() {}
    }

    fun test2(): Class2 {
        return object : Class2() {}
    }

    interface Interface1 : MockLifecycle
    open class Class1 : MockLifecycle
    open class Class2 : MockLifecycle
    class TestExtensionExtensionFactory : ExtensionFactory {
        override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension {
            return TestExtension(resolver, codeGenerator)
        }
    }

    class TestExtension(val resolver: Resolver, val codeGenerator: CodeGenerator) : KoraExtension {
        private val interfaceDeclaration = resolver.getClassDeclarationByName(Interface1::class.qualifiedName!!)!!
        private val interfaceType = interfaceDeclaration.asStarProjectedType()
        override fun getDependencyGenerator(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
            if (type != interfaceType && type != interfaceType.makeNullable()) {
                return null
            }
            val packageName = interfaceDeclaration.packageName.asString()
            val typeName = "AppWithExtensionInterface1Impl"
            return lambda@{
                val maybeGenerated = resolver.getClassDeclarationByName("$packageName.$typeName")
                if (maybeGenerated != null) {
                    val constructor = maybeGenerated.getConstructors().first()
                    return@lambda ExtensionResult.fromConstructor(constructor, maybeGenerated)
                }
                val type = TypeSpec.classBuilder(typeName)
                    .addSuperinterface(Interface1::class)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("string", Class2::class)
                            .build()
                    )
                    .build()
                val fileSpec = FileSpec.builder(packageName, typeName).addType(type).build()
                fileSpec.writeTo(codeGenerator, false)
                return@lambda ExtensionResult.RequiresCompilingResult
            }
        }
    }
}
