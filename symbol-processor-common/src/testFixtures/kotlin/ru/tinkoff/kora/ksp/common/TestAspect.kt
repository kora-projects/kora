package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.aop.symbol.processor.KoraAspectFactory
import ru.tinkoff.kora.common.AopAnnotation
import java.util.*

@AopAnnotation
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TestAspect

class TestAspectKoraAspect : KoraAspect {

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(TestAspect::class.qualifiedName!!)

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult = KoraAspect.ApplyResult.Noop.INSTANCE
}

class TestAspectKoraAspectFactory : KoraAspectFactory {
    override fun create(resolver: Resolver) = TestAspectKoraAspect()
}
