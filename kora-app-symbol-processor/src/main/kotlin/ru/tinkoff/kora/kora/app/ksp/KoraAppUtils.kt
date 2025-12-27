package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException


object KoraAppUtils {
    fun KSClassDeclaration.validateComponent(): Boolean {
        findSinglePublicConstructor()
        return true
    }

    fun KSClassDeclaration.findSinglePublicConstructor(): KSFunctionDeclaration {
        val primaryConstructor = primaryConstructor
        if (primaryConstructor != null && primaryConstructor.isPublic()) return primaryConstructor

        val constructors = getConstructors()
            .filter { c -> c.isPublic() }
            .toList()
        if (constructors.isEmpty()) {
            throw ProcessingErrorException(
                "Type annotated with @Component has no public constructors", this
            )
        }
        if (constructors.size > 1) {
            throw ProcessingErrorException(
                "Type annotated with @Component has more then one public constructor", this
            )
        }
        return constructors[0]
    }
}
