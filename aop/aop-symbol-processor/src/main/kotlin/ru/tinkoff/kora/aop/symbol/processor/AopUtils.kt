package ru.tinkoff.kora.aop.symbol.processor

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isJavaPackagePrivate
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix

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


