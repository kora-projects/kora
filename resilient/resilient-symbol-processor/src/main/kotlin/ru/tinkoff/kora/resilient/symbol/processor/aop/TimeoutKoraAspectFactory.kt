package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.aop.symbol.processor.KoraAspectFactory

@KspExperimental
class TimeoutKoraAspectFactory : KoraAspectFactory {
    override fun create(resolver: Resolver): KoraAspect = TimeoutKoraAspect(resolver)
}
