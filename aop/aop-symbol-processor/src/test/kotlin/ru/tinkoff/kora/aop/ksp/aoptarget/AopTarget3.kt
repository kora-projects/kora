package ru.tinkoff.kora.aop.ksp.aoptarget

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.aop.symbol.processor.KoraAspectFactory
import ru.tinkoff.kora.common.AopAnnotation

open class AopTarget3 {
    @AopAnnotation
    annotation class TestAnnotation3

    @TestAnnotation3
    open fun testMethod1(): String? {
        return "test"
    }

    @TestAnnotation3
    open fun testMethod2(): String? {
        return "test"
    }

    class Aspect3Factory : KoraAspectFactory {
        override fun create(resolver: Resolver): KoraAspect {
            return Aspect3(resolver)
        }
    }

    class Aspect3(private val resolver: Resolver) : KoraAspect {

        override fun getSupportedAnnotationTypes(): Set<String> {
            String
            return setOf(TestAnnotation3::class.java.canonicalName)
        }

        override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
            val field: String = aspectContext.fieldFactory.constructorInitialized(
                resolver.getClassDeclarationByName("kotlin.String")!!.asType(listOf()),
                CodeBlock.of("%S", ksFunction.simpleName.asString())
            )
            val b = CodeBlock.builder()
                .add("return %S + \"/\" + this.%L", field, field)
            return KoraAspect.ApplyResult.MethodBody(b.build())
        }
    }
}
