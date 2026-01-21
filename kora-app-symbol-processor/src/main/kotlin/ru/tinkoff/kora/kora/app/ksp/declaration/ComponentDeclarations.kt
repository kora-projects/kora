package ru.tinkoff.kora.kora.app.ksp.declaration

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import ru.tinkoff.kora.kora.app.ksp.ProcessingContext
import java.util.*


class ComponentDeclarations(private val ctx: ProcessingContext) {
    private val typeToDeclarations = LinkedHashMap<TypeName, MutableList<DeclarationWithIndex>>()
    private val declarations = ArrayList<ComponentDeclaration>()
    private val interceptors = ArrayList<DeclarationWithIndex>()


    constructor(that: ComponentDeclarations) : this(that.ctx) {
        for (typeWithDeclarations in that.typeToDeclarations.entries) {
            this.typeToDeclarations[typeWithDeclarations.key] = ArrayList(typeWithDeclarations.value)
        }
        this.declarations.addAll(that.declarations)
    }

    fun add(declaration: ComponentDeclaration): Int {
        assert(!declaration.isTemplate())
        val types = declaration.type.collectAllTypeNames()
        val index = this.declarations.size
        this.declarations.add(declaration)
        val declarationWithIndex = DeclarationWithIndex(index, declaration)
        for (type in types) {
            this.typeToDeclarations.computeIfAbsent(type) { ArrayList() }
                .add(declarationWithIndex)
        }
        if (declaration.isInterceptor) {
            this.interceptors.add(declarationWithIndex)
        }
        return index
    }

    fun getByType(type: KSType): List<DeclarationWithIndex> {
        var typeName = type.toTypeName().copy(false)
        if (typeName is ParameterizedTypeName) {
            typeName = typeName.rawType
        }
        val result = this.typeToDeclarations.getOrDefault(typeName, listOf())
        return Collections.unmodifiableList<DeclarationWithIndex>(result)
    }

    fun interceptors(): MutableList<DeclarationWithIndex> {
        return Collections.unmodifiableList<DeclarationWithIndex>(this.interceptors)
    }

    fun KSType.collectAllTypeNames() = mutableSetOf<TypeName>().let {
        fun visit(set: MutableSet<TypeName>, type: KSType, tpr: TypeParameterResolver) {
            if (type == ctx.resolver.builtIns.anyType) {
                set.add(ANY)
                return
            }
            val resolver = type.declaration.typeParameters.toTypeParameterResolver(tpr)
            var typeName = type.toTypeName(resolver)
            if (typeName is ParameterizedTypeName) {
                typeName = typeName.rawType
            }
            set.add(typeName)
            type.declaration.let { declaration ->
                if (declaration is KSClassDeclaration) {
                    val wrappedType = ctx.serviceTypesHelper.unwrap(type)
                    if (wrappedType != null) {
                        visit(set, wrappedType, resolver)
                    }
                    declaration.getAllSuperTypes().forEach {
                        visit(set, it, resolver)
                    }
                }
            }
        }
        visit(it, this, TypeParameterResolver.EMPTY)
        it.toList()
    }

}
