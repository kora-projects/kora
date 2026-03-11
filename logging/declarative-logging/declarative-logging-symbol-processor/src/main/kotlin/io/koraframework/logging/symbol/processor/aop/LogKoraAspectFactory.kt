package io.koraframework.logging.symbol.processor.aop

import com.google.devtools.ksp.processing.Resolver
import io.koraframework.aop.symbol.processor.KoraAspect
import io.koraframework.aop.symbol.processor.KoraAspectFactory

class LogKoraAspectFactory : KoraAspectFactory {
    override fun create(resolver: Resolver): KoraAspect {
        return LogKoraAspect()
    }
}
