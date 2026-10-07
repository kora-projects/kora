package io.koraframework.kora.app.ksp.declaration

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import io.koraframework.kora.app.ksp.ProcessingContext
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
        this.interceptors.addAll(that.interceptors)
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
        return this.getByRawTypeName(rawTypeName(type))
    }

    fun getByType(type: ClassName): List<DeclarationWithIndex> {
        return this.typeToDeclarations.getOrDefault(type, listOf())
    }

    /**
     * @param rawTypeName type name as returned by [rawTypeName]
     * @return declarations in order they were added, declarations added later are always appended to the end
     */
    fun getByRawTypeName(rawTypeName: TypeName): List<DeclarationWithIndex> {
        return this.typeToDeclarations.getOrDefault(rawTypeName, listOf())
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
            if (!set.add(typeName)) {
                // already visited: diamond hierarchy or self wrapped type
                return
            }
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

    companion object {
        fun rawTypeName(type: KSType): TypeName {
            val typeName = type.toTypeName().copy(false)
            return if (typeName is ParameterizedTypeName) typeName.rawType else typeName
        }
    }

}
