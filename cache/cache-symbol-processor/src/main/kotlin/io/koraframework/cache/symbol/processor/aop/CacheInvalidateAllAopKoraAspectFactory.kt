package io.koraframework.cache.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import io.koraframework.aop.symbol.processor.KoraAspect
import io.koraframework.aop.symbol.processor.KoraAspectFactory

@KspExperimental
class CacheInvalidateAllAopKoraAspectFactory : KoraAspectFactory {

    override fun create(resolver: Resolver): KoraAspect = CacheInvalidateAllAopKoraAspect(resolver)
}
