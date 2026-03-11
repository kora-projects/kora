package io.koraframework.aop.symbol.processor

import com.google.devtools.ksp.processing.Resolver

interface KoraAspectFactory {
    fun create(resolver: Resolver): KoraAspect?
}
