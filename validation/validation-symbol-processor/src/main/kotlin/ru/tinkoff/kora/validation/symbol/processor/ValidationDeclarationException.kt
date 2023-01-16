package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.symbol.KSAnnotated

class ValidationDeclarationException(message: String, val declaration: KSAnnotated) : RuntimeException(message)
