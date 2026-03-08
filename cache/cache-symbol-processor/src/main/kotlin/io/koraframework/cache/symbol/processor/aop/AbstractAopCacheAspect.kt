package io.koraframework.cache.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.koraframework.aop.symbol.processor.KoraAspect

@KspExperimental
abstract class AbstractAopCacheAspect : KoraAspect {

    open fun getSuperMethod(method: KSFunctionDeclaration, superCall: String): String {
        return method.parameters.joinToString(", ", "$superCall(", ")")
    }
}
