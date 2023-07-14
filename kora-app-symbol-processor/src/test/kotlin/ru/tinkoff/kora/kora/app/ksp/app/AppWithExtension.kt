package ru.tinkoff.kora.kora.app.ksp.app

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getFunctionDeclarationsByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionFactory
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.KspCommonUtils.parametrized

@KoraApp
interface AppWithExtension {
    // factory: generic component, accepts its genetic TypeRef as arguments
    @Root
    fun test1(class1: Interface1, class1Optional: Array<out Interface1>): Class1 {
        return Class1()
    }

    fun test2(): Class2 {
        return Class2()
    }

    interface Interface1
    open class Class1
    open class Class2
    class TestExtension1ExtensionFactory : ExtensionFactory {
        override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension {
            return TestExtension1(resolver, codeGenerator)
        }
    }

    class TestExtension2ExtensionFactory : ExtensionFactory {
        override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension {
            return TestExtension2(resolver, codeGenerator)
        }
    }

    class TestExtension1(val resolver: Resolver, val codeGenerator: CodeGenerator) : KoraExtension {
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

    class TestExtension2(val resolver: Resolver, val codeGenerator: CodeGenerator) : KoraExtension {
        private val interfaceDeclaration = resolver.getClassDeclarationByName(Interface1::class.qualifiedName!!)!!
        private val interfaceType = interfaceDeclaration.asStarProjectedType()
        private val optionalDeclaration = resolver.getClassDeclarationByName(Array::class.qualifiedName!!)!!
        private val optionalType = optionalDeclaration.asType(listOf(
            resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(interfaceType), Variance.COVARIANT)
        ))

        override fun getDependencyGenerator(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
            if (type != optionalType) {
                return null
            }
            return lambda@{
                val ofFunctionDeclaration = resolver.getFunctionDeclarationsByName("kotlin.arrayOf", true).first()
                val parameterTypes = listOf(type.arguments[0].type!!.resolve())
                val ofFunctionType = ofFunctionDeclaration.parametrized(type, parameterTypes)
                return@lambda ExtensionResult.fromExecutable(ofFunctionDeclaration, ofFunctionType);
            }
        }
    }
}

