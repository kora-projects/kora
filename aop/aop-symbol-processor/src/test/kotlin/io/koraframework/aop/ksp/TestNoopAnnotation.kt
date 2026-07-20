package io.koraframework.aop.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.asClassName
import io.koraframework.aop.symbol.processor.KoraAspect
import io.koraframework.aop.symbol.processor.KoraAspectFactory
import io.koraframework.common.annotation.AopAnnotation

@AopAnnotation
annotation class TestNoopAnnotation {
    class TestNoopAspect : KoraAspect {
        override fun getSupportedAnnotationTypes(): Set<String> = setOf(TestNoopAnnotation::class.asClassName().canonicalName)

        override fun apply(
            ksFunction: KSFunctionDeclaration,
            superCall: String,
            aspectContext: KoraAspect.AspectContext
        ): KoraAspect.ApplyResult = KoraAspect.ApplyResult.Noop.INSTANCE
    }

    class TestNoopAspectFactory : KoraAspectFactory {
        override fun create(resolver: Resolver) = TestNoopAspect()
    }
}
