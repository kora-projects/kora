package io.koraframework.logging.symbol.processor.aop.mdc

import com.google.devtools.ksp.processing.Resolver
import io.koraframework.aop.symbol.processor.KoraAspect
import io.koraframework.aop.symbol.processor.KoraAspectFactory

class MdcKoraAspectFactory : KoraAspectFactory {
    override fun create(resolver: Resolver): KoraAspect = MdcKoraAspect()
}
