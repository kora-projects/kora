package ru.tinkoff.kora.aop.ksp.aoptarget

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.aop.symbol.processor.KoraAspectFactory
import ru.tinkoff.kora.common.AopAnnotation
import ru.tinkoff.kora.common.Tag

open class AopTarget1(val argument: String?, @Tag(String::class) val tagged: Int) {
    interface ProxyListener1 {
        fun call(annotationValue: String?)
    }

    @AopAnnotation
    annotation class TestAnnotation1(val value: String)

    fun shouldNotBeProxied1() {}

    fun shouldNotBeProxied2() {}

    @TestAnnotation1("testMethod1")
    open fun testMethod1(): String? {
        return "test"
    }

    @TestAnnotation1("testMethod2")
    open fun testMethod2(param: String?) {
    }

    @KspExperimental
    class Aspect1Factory : KoraAspectFactory {
        override fun create(resolver: Resolver): KoraAspect {
            return Aspect1(resolver)
        }
    }

    @KspExperimental
    class Aspect1(private val resolver: Resolver) : KoraAspect {

        override fun getSupportedAnnotationTypes(): Set<String> {
            return setOf(TestAnnotation1::class.java.canonicalName)
        }

        override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
            val annotation = ksFunction.getAnnotationsByType(TestAnnotation1::class).first()
            val field: String = aspectContext.fieldFactory.constructorParam(
                resolver.getClassDeclarationByName(ProxyListener1::class.java.canonicalName)!!.asType(listOf()),
                listOf()
            )
            val b = CodeBlock.builder()
                .add("this.%L.call(%S)\n", field, annotation.value)
            if (ksFunction.returnType != resolver.builtIns.unitType) {
                b.add("return ")
            }
            b.add(ksFunction.parameters
                .map { p -> CodeBlock.of("%L", p) }
                .joinToString(", ", "$superCall(", ")\n")
            )
            return KoraAspect.ApplyResult.MethodBody(b.build())
        }
    }
}
