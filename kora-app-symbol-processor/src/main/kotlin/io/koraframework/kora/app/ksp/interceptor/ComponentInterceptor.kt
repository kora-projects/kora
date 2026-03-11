package io.koraframework.kora.app.ksp.interceptor

import com.google.devtools.ksp.symbol.KSType
import io.koraframework.kora.app.ksp.component.ResolvedComponent
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration

data class ComponentInterceptor(
    val component: ResolvedComponent,
    val declaration: ComponentDeclaration,
    val interceptType: KSType
)
