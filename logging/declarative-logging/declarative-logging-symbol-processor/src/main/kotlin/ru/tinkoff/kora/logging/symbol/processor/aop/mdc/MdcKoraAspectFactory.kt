package ru.tinkoff.kora.logging.symbol.processor.aop.mdc

import com.google.devtools.ksp.processing.Resolver
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.aop.symbol.processor.KoraAspectFactory

class MdcKoraAspectFactory : KoraAspectFactory {
    override fun create(resolver: Resolver): KoraAspect = MdcKoraAspect()
}
