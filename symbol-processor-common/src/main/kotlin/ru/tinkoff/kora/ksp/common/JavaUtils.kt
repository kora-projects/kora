package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.*

object JavaUtils {
    fun KSClassDeclaration.isRecord(): Boolean {
        if (origin != Origin.JAVA && origin != Origin.JAVA_LIB) {
            return false
        }
        if (classKind != ClassKind.CLASS) {
            return false
        }
        return superTypes.any { it.resolve().declaration.qualifiedName?.asString() == "java.lang.Record" }
    }

    fun KSClassDeclaration.recordComponents(): Sequence<KSFunctionDeclaration> {
        require(isRecord())
        val constructorParameters = this.getAllFunctions().filter { it.isConstructor() }
            .flatMap { it.parameters }
            .map { it.name?.asString().toString() }
            .toSet()

        // KSP can't see any of java record fields or even constructors for some reason
        return this.getAllFunctions()
            .filter { it.parameters.isEmpty() }
            .filter { it.modifiers.contains(Modifier.PUBLIC) }
            .filter { constructorParameters.contains(it.simpleName.asString()) }
    }
}
