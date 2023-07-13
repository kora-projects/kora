package ru.tinkoff.kora.ksp.common

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

    fun KSClassDeclaration.recordComponents(): Sequence<KSPropertyDeclaration> {
        require(isRecord())
        val fields = arrayListOf<KSPropertyDeclaration>()
        val accessors = hashMapOf<String, KSFunctionDeclaration>()
        for (declaration in declarations) {
            if (declaration is KSPropertyDeclaration) {
                fields.add(declaration)
            } else if (declaration is KSFunctionDeclaration && declaration.parameters.isEmpty()) {
                accessors[declaration.simpleName.asString()] = declaration
            }
        }
        return declarations
            .filterIsInstance<KSPropertyDeclaration>()
            .filter { !it.modifiers.contains(Modifier.JAVA_STATIC) }
    }
}
