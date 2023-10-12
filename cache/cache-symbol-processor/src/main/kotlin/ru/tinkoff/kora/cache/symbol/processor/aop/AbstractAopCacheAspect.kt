package ru.tinkoff.kora.cache.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.cache.symbol.processor.CacheOperation

@KspExperimental
abstract class AbstractAopCacheAspect : KoraAspect {

    open fun getSuperMethod(method: KSFunctionDeclaration, superCall: String): String {
        return method.parameters.joinToString(", ", "$superCall(", ")")
    }
}
