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

@AopTarget2.TestAnnotation21("TestAnnotation21")
open class AopTarget2 {

    interface ProxyListener2 {
        fun call(annotationValue: String?)
    }

    @AopAnnotation
    annotation class TestAnnotation21(val value: String)

    @AopAnnotation
    annotation class TestAnnotation22(val value: String)

    @TestAnnotation22("TestAnnotation22")
    open fun testMethod1() {
    }

    @TestAnnotation21("TestAnnotation21Method")
    @TestAnnotation22("TestAnnotation22")
    open fun testMethod2() {
    }

    @TestAnnotation22("TestAnnotation22")
    @TestAnnotation21("TestAnnotation21Method")
    open fun testMethod3() {
    }

    open fun testMethod4(@TestAnnotation22("TestAnnotation22Param") param: String?) {}

    @KspExperimental
    class Aspect21Factory : KoraAspectFactory {
        override fun create(resolver: Resolver): KoraAspect {
            return Aspect21(resolver)
        }
    }

    @KspExperimental
    class Aspect21(private val resolver: Resolver) : KoraAspect {

        override fun getSupportedAnnotationTypes(): Set<String> {
            return setOf(TestAnnotation21::class.java.canonicalName)
        }

        override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
            var annotation = ksFunction.getAnnotationsByType(TestAnnotation21::class).firstOrNull()
            if (annotation == null) {
                annotation = ksFunction.parentDeclaration!!.getAnnotationsByType(TestAnnotation21::class).first()
            }
            val field: String = aspectContext.fieldFactory.constructorParam(
                resolver.getClassDeclarationByName(ProxyListener2::class.qualifiedName.toString())!!.asType(listOf()),
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

    @KspExperimental
    class Aspect22Factory : KoraAspectFactory {
        override fun create(resolver: Resolver): KoraAspect {
            return Aspect22(resolver)
        }
    }

    @KspExperimental
    class Aspect22(private val resolver: Resolver) : KoraAspect {

        override fun getSupportedAnnotationTypes(): Set<String> {
            return setOf(TestAnnotation22::class.qualifiedName.toString())
        }

        override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
            var annotation = ksFunction.getAnnotationsByType(TestAnnotation22::class).firstOrNull()
            if (annotation == null) {
                for (parameter in ksFunction.parameters) {
                    annotation = parameter.getAnnotationsByType(TestAnnotation22::class).firstOrNull()
                    if (annotation != null) {
                        break
                    }
                }
            }
            val field: String = aspectContext.fieldFactory.constructorParam(
                resolver.getClassDeclarationByName(ProxyListener2::class.java.canonicalName)!!.asType(listOf()),
                listOf()
            )
            val b = CodeBlock.builder()
                .add("this.%L.call(%S)\n", field, annotation!!.value)
            if (ksFunction.returnType != resolver.builtIns.unitType) {
                b.add("return ")
            }
            b.add("%L(", superCall)
            for (i in ksFunction.parameters.indices) {
                if (i > 0) {
                    b.add(", ")
                }
                val parameter = ksFunction.parameters[i]
                b.add("%L", parameter)
            }
            b.add(")\n")
            return KoraAspect.ApplyResult.MethodBody(b.build())
        }
    }
}
