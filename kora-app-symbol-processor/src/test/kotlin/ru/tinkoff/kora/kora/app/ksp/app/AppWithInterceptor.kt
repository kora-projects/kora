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
import reactor.core.publisher.Mono
import ru.tinkoff.kora.application.graph.GraphInterceptor
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionFactory
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import java.io.IOException

@KoraApp
interface AppWithInterceptor {
    fun class1(): Class1 {
        return Class1()
    }

    fun interceptor(): Interceptor {
        return Interceptor()
    }

    @Root
    fun lifecycle(o: Interface1): Any {
        return Any()
    }

    class Class1
    class Interceptor : GraphInterceptor<Class1> {
        override fun init(value: Class1): Mono<Class1> {
            return Mono.just(value)
        }

        override fun release(value: Class1): Mono<Class1> {
            return Mono.just(value)
        }
    }

    interface Interface1

    class TestExtension(val resolver: Resolver, val codeGenerator: CodeGenerator) : KoraExtension {
        private val interfaceDeclaration = resolver.getClassDeclarationByName(Interface1::class.qualifiedName!!)!!
        private val interfaceType = interfaceDeclaration.asStarProjectedType()

        override fun getDependencyGenerator(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
            if (!(type == interfaceType || type == interfaceType.makeNullable())) {
                return null
            }
            return ret@{
                val packageName = interfaceDeclaration.packageName.asString()
                val typeName = "AppWithInterceptorInterface1Impl"
                val maybeGenerated = resolver.getClassDeclarationByName("$packageName.$typeName")
                if (maybeGenerated != null) {
                    val constructor = maybeGenerated.getConstructors().first()
                    return@ret ExtensionResult.fromConstructor(constructor, maybeGenerated)
                }
                val type = TypeSpec.classBuilder(typeName)
                    .addSuperinterface(Interface1::class)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("value", Class1::class)
                            .build()
                    )
                    .build()
                val fileSpec = FileSpec.builder(packageName, typeName).addType(type).build()
                try {
                    fileSpec.writeTo(codeGenerator, false)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
                return@ret ExtensionResult.RequiresCompilingResult
            }
        }
    }

    class TestExtensionExtensionFactory : ExtensionFactory {
        override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension {
            return TestExtension(resolver, codeGenerator)
        }
    }


}
