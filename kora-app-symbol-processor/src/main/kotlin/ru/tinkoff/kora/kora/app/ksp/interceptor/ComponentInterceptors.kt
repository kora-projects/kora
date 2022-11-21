package ru.tinkoff.kora.kora.app.ksp.interceptor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.kora.app.ksp.ProcessingContext
import ru.tinkoff.kora.kora.app.ksp.component.ResolvedComponent
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.ksp.common.CommonClassNames

data class ComponentInterceptors(
    private val resolver: Resolver,
    private val interceptors: MutableList<ComponentInterceptor> = mutableListOf()
) {

    companion object {
        fun parseInterceptors(ctx: ProcessingContext, components: Collection<ResolvedComponent>): ComponentInterceptors {
            val interceptors = ArrayList<ComponentInterceptor>()
            for (component in components) {
                val factory = parseInterceptor(ctx, component)
                if (factory != null) {
                    interceptors.add(factory)
                }
            }
            return ComponentInterceptors(ctx.resolver, interceptors)
        }

        private fun parseInterceptor(ctx: ProcessingContext, component: ResolvedComponent): ComponentInterceptor? {
            if (!ctx.serviceTypesHelper.isInterceptor(component.type)) {
                return null
            }
            val interceptType = ctx.serviceTypesHelper.interceptType(component.type)
            return ComponentInterceptor(component, component.declaration, interceptType)
        }
    }

    @KspExperimental
    fun interceptorsFor(descriptor: ComponentDeclaration): List<ComponentInterceptor> {
        val type = descriptor.type.makeNotNullable()

        return this.interceptors.filter { interceptor ->
            val realInterceptorType = interceptor.interceptType.makeNotNullable()
            type == realInterceptorType && descriptor.tags.containsAll(interceptor.declaration.tags)
        }
    }
}
