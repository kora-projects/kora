package io.koraframework.kora.app.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.koraframework.ksp.common.CommonClassNames


class ServiceTypesHelper(val resolver: Resolver) {
    private val interceptorClassDeclaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(CommonClassNames.graphInterceptor.canonicalName))!!
    private val interceptorType = interceptorClassDeclaration.asStarProjectedType()
    private val interceptorInitFunction = interceptorClassDeclaration.getDeclaredFunctions()
        .filter { it.simpleName.asString() == "afterInit" && it.parameters.size == 1 }
        .first()


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
        //TODO check if also is not working?
        val unwrappedType = wrappedValueFunction.asMemberOf(maybeWrapped).returnType!!
        return unwrappedType.makeNotNullable() == type // platform nullability ruins equality
    }

    fun unwrap(maybeWrapped: KSType): KSType? {
        if (!wrappedType.isAssignableFrom(maybeWrapped)) {
            return null
        }
        val maybeWrappedDeclaration = maybeWrapped.declaration as KSClassDeclaration
        val wrappedClassDeclaration = maybeWrappedDeclaration.getAllSuperTypes().plus(sequence { this.yield(maybeWrappedDeclaration.asType(listOf())) })
            .first { CommonClassNames.wrapped.canonicalName == it.declaration.qualifiedName?.asString() }
            .declaration as KSClassDeclaration
        val wrappedValueFunction = wrappedClassDeclaration.getAllFunctions()
            .filter { it.simpleName.asString() == "value" }
            .first()
        return wrappedValueFunction.asMemberOf(maybeWrapped).returnType!!
    }

    fun isInterceptorFor(maybeInterceptor: KSType, type: KSType): Boolean {
        if (!interceptorType.isAssignableFrom(maybeInterceptor)) {
            return false
        }

        return try {
            val interceptType = interceptType(maybeInterceptor)
            return isInterceptable(interceptType, type)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun isInterceptable(interceptedType: KSType, targetType: KSType): Boolean {
        return targetType == interceptedType.makeNotNullable()
    }

    fun interceptType(maybeInterceptor: KSType): KSType {
        if (!interceptorType.isAssignableFrom(maybeInterceptor)) {
            throw IllegalStateException("Kora internal error: interceptType called for non-interceptor type: ${maybeInterceptor.declaration.qualifiedName?.asString()}")
        }

        return interceptorInitFunction.asMemberOf(maybeInterceptor).parameterTypes[0]!!.makeNotNullable()
    }

    fun isInterceptor(type: KSType) = interceptorType.isAssignableFrom(type)
}
