package ru.tinkoff.kora.logging.symbol.processor.aop

import com.google.devtools.ksp.processing.Resolver
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.aop.symbol.processor.KoraAspectFactory

class LogKoraAspectFactory : KoraAspectFactory {
    override fun create(resolver: Resolver): KoraAspect {
        return LogKoraAspect(resolver)
    }
}
