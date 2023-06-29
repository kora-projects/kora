package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver


object KoraAppUtils {
}


fun isClassExists(resolver: Resolver, fullClassName: String): Boolean {
    val declaration = resolver.getClassDeclarationByName(fullClassName)
    return declaration != null
}


