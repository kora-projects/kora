package ru.tinkoff.kora.kora.app.ksp.extension

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType

interface KoraExtension {
    fun getDependencyGenerator(resolver: Resolver, type: KSType): (() -> ExtensionResult)?
}
