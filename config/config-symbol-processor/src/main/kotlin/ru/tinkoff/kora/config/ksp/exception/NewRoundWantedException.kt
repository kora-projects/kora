package ru.tinkoff.kora.config.ksp.exception

import com.google.devtools.ksp.symbol.KSClassDeclaration

class NewRoundWantedException(val declaration: KSClassDeclaration) : RuntimeException()
