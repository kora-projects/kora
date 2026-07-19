package io.koraframework.kora.app.ksp

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.koraframework.ksp.common.exception.ProcessingErrorException


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
                """
                @Component type has no public constructors.

                Fix:
                  - Add one public constructor.
                  - Move construction to a module function if constructor cannot be public.
                """.trimIndent(), this
            )
        }
        if (constructors.size > 1) {
            throw ProcessingErrorException(
                """
                @Component type has more than one public constructor.

                Fix:
                  - Keep exactly one public constructor.
                  - Make extra constructors non-public.
                """.trimIndent(), this
            )
        }
        return constructors[0]
    }
}
