package ru.tinkoff.kora.kora.app.ksp.interceptor

import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.kora.app.ksp.component.ResolvedComponent
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration

data class ComponentInterceptor(
    val component: ResolvedComponent,
    val declaration: ComponentDeclaration,
    val interceptType: KSType
)
