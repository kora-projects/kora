package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import ru.tinkoff.kora.ksp.common.CommonClassNames.isFlow
import ru.tinkoff.kora.ksp.common.CommonClassNames.isFlux
import ru.tinkoff.kora.ksp.common.CommonClassNames.isFuture
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.CommonClassNames.isMono
import ru.tinkoff.kora.ksp.common.CommonClassNames.isPublisher
import ru.tinkoff.kora.ksp.common.CommonClassNames.isVoid

object FunctionUtils {

    fun KSFunctionDeclaration.isFlux() = returnType!!.resolve().isFlux()
    fun KSFunctionDeclaration.isMono() = returnType!!.resolve().isMono()
    fun KSFunctionDeclaration.isPublisher() = returnType!!.resolve().isPublisher()
    fun KSFunctionDeclaration.isFlow() = returnType!!.resolve().isFlow()
    fun KSFunctionDeclaration.isFuture() = returnType!!.resolve().isFuture()
    fun KSFunctionDeclaration.isList() = returnType!!.resolve().isList()
    fun KSFunctionDeclaration.isSuspend() = modifiers.contains(Modifier.SUSPEND)
    fun KSFunctionDeclaration.isVoid() = returnType!!.isVoid()
}
