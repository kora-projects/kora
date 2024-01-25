package ru.tinkoff.kora.aop.ksp.aoptarget

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.aop.symbol.processor.KoraAspectFactory
import ru.tinkoff.kora.common.AopAnnotation
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation

private typealias AA = AopAliasTarget.AliasedAnnotation

open class AopAliasTarget(val argument: String?, @Tag(String::class) val tagged: Int) {
    interface ProxyListener1 {
        fun call(annotationValue: String?)
    }

    @AopAnnotation
    annotation class AliasedAnnotation(val value: String)

    fun shouldNotBeProxied1() {}

    fun shouldNotBeProxied2() {}

    @AA("testMethod1")
    open fun testMethod1(): String? {
        return "test"
    }

    @AA("testMethod2")
    open fun testMethod2(param: String?) {
    }


    @KspExperimental
    class AspectAliasesFactory : KoraAspectFactory {
        override fun create(resolver: Resolver): KoraAspect {
            return AspectAliases(resolver)
        }
    }

    @KspExperimental
    class AspectAliases(private val resolver: Resolver) : KoraAspect {

        override fun getSupportedAnnotationTypes(): Set<String> {
            return setOf(AliasedAnnotation::class.java.canonicalName)
        }

        override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {

            // get
            val annotation = ksFunction.findAnnotation(AliasedAnnotation::class)!!

            val field: String = aspectContext.fieldFactory.constructorParam(
                resolver.getClassDeclarationByName(ProxyListener1::class.java.canonicalName)!!.asType(listOf()),
                listOf()
            )
            val b = CodeBlock.builder()
                .add("this.%L.call(%S)\n", field, annotation.arguments.first().value as String)
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
