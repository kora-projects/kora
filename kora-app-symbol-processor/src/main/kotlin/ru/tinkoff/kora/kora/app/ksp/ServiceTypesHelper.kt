package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.ksp.common.CommonClassNames


class ServiceTypesHelper(val resolver: Resolver) {
    private val interceptorClassDeclaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(CommonClassNames.graphInterceptor.canonicalName))!!
    private val interceptorType = interceptorClassDeclaration.asStarProjectedType()
    private val interceptorInitFunction = interceptorClassDeclaration.getDeclaredFunctions()
        .filter { it.simpleName.asString() == "init" && it.parameters.size == 1 }
        .first()

    private val lifecycleClassDeclaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(CommonClassNames.lifecycle.canonicalName))!!
    private val lifecycleType = lifecycleClassDeclaration.asStarProjectedType()

    private val wrappedClassDeclaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(CommonClassNames.wrapped.canonicalName))!!
    private val wrappedType = wrappedClassDeclaration.asStarProjectedType()
    private val wrappedValueFunction = wrappedClassDeclaration.getDeclaredFunctions()
        .filter { it.simpleName.asString() == "value" && it.parameters.isEmpty() }
        .first()


    fun isAssignableToUnwrapped(maybeWrapped: KSType, type: KSType): Boolean {
        if (!wrappedType.isAssignableFrom(maybeWrapped)) {
            return false
        }
        val maybeWrappedDeclaration = maybeWrapped.declaration as KSClassDeclaration
        val wrappedClassDeclaration = maybeWrappedDeclaration.getAllSuperTypes().plus(sequence { this.yield(maybeWrappedDeclaration.asType(listOf())) })
            .first { CommonClassNames.wrapped.canonicalName == it.declaration.qualifiedName?.asString() }
            .declaration as KSClassDeclaration
        val wrappedValueFunction = wrappedClassDeclaration.getAllFunctions()
            .filter { it.simpleName.asString() == "value" }
            .first()
        val unwrappedType = wrappedValueFunction.asMemberOf(maybeWrapped).returnType!!
        return type.isAssignableFrom(unwrappedType)
    }

    fun isSameToUnwrapped(maybeWrapped: KSType, type: KSType): Boolean {
        if (!wrappedType.isAssignableFrom(maybeWrapped)) {
            return false
        }
        val unwrappedType = wrappedValueFunction.asMemberOf(maybeWrapped).returnType!!
        return unwrappedType.makeNotNullable() == type // platform nullability ruins equality
    }

    fun isInterceptorFor(maybeInterceptor: KSType, type: KSType): Boolean {
        if (!interceptorType.isAssignableFrom(maybeInterceptor)) {
            return false
        }

        val interceptsType = interceptorInitFunction.asMemberOf(maybeInterceptor).parameterTypes[0]!!
        return type == interceptsType.makeNotNullable()
    }


    fun interceptType(maybeInterceptor: KSType): KSType {
        if (!interceptorType.isAssignableFrom(maybeInterceptor)) {
            throw IllegalArgumentException()
        }

        return interceptorInitFunction.asMemberOf(maybeInterceptor).parameterTypes[0]!!.makeNotNullable()
    }

    fun isLifecycle(type: KSType): Boolean {
        return lifecycleType.isAssignableFrom(type) || isAssignableToUnwrapped(type, this.lifecycleType)
    }

    fun isInterceptor(type: KSType) = interceptorType.isAssignableFrom(type)
}
