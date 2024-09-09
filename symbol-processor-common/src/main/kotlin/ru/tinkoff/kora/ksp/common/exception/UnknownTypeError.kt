package ru.tinkoff.kora.ksp.common.exception

import com.google.devtools.ksp.symbol.KSDeclaration

class UnknownTypeError(val declaration: KSDeclaration) : RuntimeException()
