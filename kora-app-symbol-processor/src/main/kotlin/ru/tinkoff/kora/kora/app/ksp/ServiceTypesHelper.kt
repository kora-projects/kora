package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.CommonClassNames.isVoid
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix


class ServiceTypesHelper(val resolver: Resolver) {
    private val interceptorClassDeclaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(CommonClassNames.graphInterceptor.canonicalName))!!
    private val interceptorType = interceptorClassDeclaration.asStarProjectedType()
    private val interceptorInitFunction = interceptorClassDeclaration.getDeclaredFunctions()
        .filter { it.simpleName.asString() == "init" && it.parameters.size == 1 }
        .first()


    private val wrappedClassDeclaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(CommonClassNames.wrapped.canonicalName))!!
    private val wrappedType = wrappedClassDeclaration.asStarProjectedType()
    private val wrappedValueFunction = wrappedClassDeclaration.getDeclaredFunctions()
        .filter { it.simpleName.asString() == "value" && it.parameters.isEmpty() }
        .first()

    companion object {

        fun findAopProxySuperClass(maybeAopProxy: KSType): KSType? {
            val aopProxyAnnotation = maybeAopProxy.declaration.findAnnotation(CommonClassNames.aopProxy)
            val proxyDeclaration = maybeAopProxy.declaration
            if (aopProxyAnnotation != null && proxyDeclaration is KSClassDeclaration) {
                val proxyParent = (maybeAopProxy.declaration as KSClassDeclaration).superTypes
                    .map { it.resolve() }
                    .filter { (it.declaration as? KSClassDeclaration)?.classKind == ClassKind.CLASS }
                    .firstOrNull()

                if (proxyParent != null) {
                    val proxyParentDeclaration = proxyParent.declaration
                    val aopProxyName = proxyParentDeclaration.getOuterClassesAsPrefix() + proxyParentDeclaration.simpleName.asString() + "__AopProxy"
                    val aopProxyCanonical = proxyParentDeclaration.packageName.asString() + "." + aopProxyName
                    if (aopProxyCanonical == maybeAopProxy.declaration.qualifiedName!!.asString()) {
                        return proxyParent
                    }
                }
            }

            return null
        }
    }

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
        if (targetType == interceptedType.makeNotNullable()) {
            return true
        } else if (interceptedType.isAssignableFrom(targetType)) {
            val aopProxyAnnotation = targetType.declaration.findAnnotation(CommonClassNames.aopProxy)
            val proxyDeclaration = interceptedType.declaration
            if (aopProxyAnnotation != null && proxyDeclaration.parentDeclaration != null) {
                val aopProxyName = proxyDeclaration.getOuterClassesAsPrefix() + proxyDeclaration.simpleName.asString() + "__AopProxy"
                val aopProxyCanonical = proxyDeclaration.packageName.asString() + "." + aopProxyName
                return aopProxyCanonical == targetType.declaration.qualifiedName!!.asString()
            }
        }

        return false
    }

    fun interceptType(maybeInterceptor: KSType): KSType {
        if (!interceptorType.isAssignableFrom(maybeInterceptor)) {
            throw IllegalArgumentException()
        }

        return if (maybeInterceptor.declaration is KSClassDeclaration) {
            (maybeInterceptor.declaration as KSClassDeclaration).getDeclaredFunctions()
                .filter { f -> f.simpleName.asString() == "init" && f.parameters.size == 1 && f.returnType != null && !f.returnType!!.isVoid() }
                .filter { f -> f.parameters.first().type.toTypeName() == f.returnType!!.toTypeName() }
                .map { it.returnType!!.resolve() }
                .firstOrNull() ?: throw IllegalArgumentException()
        } else {
            val memberOf = interceptorInitFunction.asMemberOf(maybeInterceptor)
            return memberOf.parameterTypes[0]!!.makeNotNullable()
        }
    }

    fun isInterceptor(type: KSType) = interceptorType.isAssignableFrom(type)
}
