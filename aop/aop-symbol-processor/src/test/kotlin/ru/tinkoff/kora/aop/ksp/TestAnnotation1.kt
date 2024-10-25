package ru.tinkoff.kora.aop.ksp

import com.google.devtools.ksp.processing.Resolver
import com.squareup.kotlinpoet.asClassName
import ru.tinkoff.kora.aop.symbol.processor.KoraAspectFactory
import ru.tinkoff.kora.common.AopAnnotation

@AopAnnotation
annotation class TestAnnotation1(val value: String) {
    class TestAnnotationAspect : AbstractTestAnnotationAspect() {
        override fun testAnnotation() = TestAnnotation1::class.asClassName()
    }

    class TestAnnotationAspectFactory : KoraAspectFactory {
        override fun create(resolver: Resolver) = TestAnnotationAspect()
    }
}

