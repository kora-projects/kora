package io.koraframework.aop.symbol.processor

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isJavaPackagePrivate
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.ksp.common.getOuterClassesAsPrefix

fun KSClassDeclaration.aopProxyName(): String {
    return getOuterClassesAsPrefix() + simpleName.asString() + "__AopProxy"
}

fun KSClassDeclaration.findAopConstructor(): KSFunctionDeclaration? {
    val publicConstructors = getConstructors().filter { it.isPublic() }.toList()
    if (publicConstructors.size == 1) {
        return publicConstructors[0]
    }
    if (publicConstructors.size > 1) {
        return null
    }
    val protectedConstructors = getConstructors().filter { it.isProtected() }.toList()
    if (protectedConstructors.size == 1) {
        return protectedConstructors[0]
    }
    if (protectedConstructors.size > 1) {
        return null
    }
    val packagePrivateConstructors = getConstructors().filter { it.isJavaPackagePrivate() }.toList()
    return if (packagePrivateConstructors.size == 1) {
        packagePrivateConstructors[0]
    } else null
}

/**
 * Type of the parameter as seen inside the function body: for a vararg parameter KSP resolves the element type,
 * while the generated AOP proxy functions receive it as an array.
 */
fun KSValueParameter.aopProxyParameterType(): TypeName {
    val type = this.type.resolve().toTypeName()
    if (!this.isVararg) {
        return type
    }
    return when (type) {
        BOOLEAN -> BOOLEAN_ARRAY
        BYTE -> BYTE_ARRAY
        CHAR -> CHAR_ARRAY
        SHORT -> SHORT_ARRAY
        INT -> INT_ARRAY
        LONG -> LONG_ARRAY
        FLOAT -> FLOAT_ARRAY
        DOUBLE -> DOUBLE_ARRAY
        else -> ARRAY.parameterizedBy(WildcardTypeName.producerOf(type))
    }
}
