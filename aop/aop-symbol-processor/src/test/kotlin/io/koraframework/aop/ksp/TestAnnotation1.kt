package io.koraframework.aop.ksp

import com.google.devtools.ksp.processing.Resolver
import com.squareup.kotlinpoet.asClassName
import io.koraframework.aop.symbol.processor.KoraAspectFactory
import io.koraframework.common.AopAnnotation

@AopAnnotation
annotation class TestAnnotation1(val value: String) {
    class TestAnnotationAspect : AbstractTestAnnotationAspect() {
        override fun testAnnotation() = TestAnnotation1::class.asClassName()
    }

    class TestAnnotationAspectFactory : KoraAspectFactory {
        override fun create(resolver: Resolver) = TestAnnotationAspect()
    }
}

