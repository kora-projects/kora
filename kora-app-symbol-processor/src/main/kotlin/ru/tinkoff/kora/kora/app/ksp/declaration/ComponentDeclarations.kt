package ru.tinkoff.kora.kora.app.ksp.declaration

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.kora.app.ksp.ProcessingContext

/**
 * Component declarations in insertion order with an index "raw type name -> declarations".
 *
 * The index only narrows the list of candidates: [getByType] returns every declaration
 * that can be assignable (directly or through `Wrapped<T>`) to the requested type, in insertion order.
 * Callers still apply the exact same matching checks as before.
 */
class ComponentDeclarations private constructor(
    private val ctx: ProcessingContext,
    private val declarations: MutableList<ComponentDeclaration>,
    private val typeToDeclarations: MutableMap<String, MutableList<DeclarationWithIndex>>,
    // declarations whose type hierarchy can't be indexed reliably: they are candidates for every type
    private val notIndexed: MutableList<DeclarationWithIndex>,
    private val interceptors: MutableList<ComponentDeclaration>,
) {
    constructor(ctx: ProcessingContext, declarations: List<ComponentDeclaration>) : this(ctx, ArrayList(declarations.size), HashMap(), ArrayList(), ArrayList()) {
        for (declaration in declarations) {
            add(declaration)
        }
    }

    constructor(that: ComponentDeclarations) : this(
        that.ctx,
        ArrayList(that.declarations),
        that.typeToDeclarations.mapValuesTo(HashMap(that.typeToDeclarations.size)) { ArrayList(it.value) },
        ArrayList(that.notIndexed),
        ArrayList(that.interceptors),
    )

    fun add(declaration: ComponentDeclaration) {
        val declarationWithIndex = DeclarationWithIndex(declarations.size, declaration)
        declarations.add(declaration)
        val keys = indexKeys(ctx, declaration.type)
        if (keys == null) {
            notIndexed.add(declarationWithIndex)
        } else {
            for (key in keys) {
                typeToDeclarations.getOrPut(key) { ArrayList() }.add(declarationWithIndex)
            }
        }
        if (ctx.serviceTypesHelper.isInterceptor(declaration.type)) {
            interceptors.add(declaration)
        }
    }

    /**
     * @return declarations that may be assignable to the given type, in insertion order
     */
    fun getByType(type: KSType): List<ComponentDeclaration> {
        val key = lookupKey(type) ?: return declarations.toList()
        val indexed = typeToDeclarations[key] ?: emptyList()
        val candidates = if (notIndexed.isEmpty()) indexed else (indexed + notIndexed).sortedBy { it.index }
        return candidates.map { it.declaration }
    }

    /**
     * @return declarations of types assignable to GraphInterceptor, in insertion order
     */
    fun interceptors(): List<ComponentDeclaration> = interceptors.toList()

    companion object {
        // Java collection types are seen as flexible `(MutableX..X?)` types: a read-only type satisfies such a claim
        private val readOnlyCollections = mapOf(
            "kotlin.collections.MutableIterable" to "kotlin.collections.Iterable",
            "kotlin.collections.MutableCollection" to "kotlin.collections.Collection",
            "kotlin.collections.MutableList" to "kotlin.collections.List",
            "kotlin.collections.MutableSet" to "kotlin.collections.Set",
            "kotlin.collections.MutableMap" to "kotlin.collections.Map",
            "kotlin.collections.MutableMap.MutableEntry" to "kotlin.collections.Map.Entry",
            "kotlin.collections.MutableIterator" to "kotlin.collections.Iterator",
            "kotlin.collections.MutableListIterator" to "kotlin.collections.ListIterator",
        )

        private fun key(type: KSType): String? {
            if (type.isError) {
                return null
            }
            val declaration = type.declaration as? KSClassDeclaration ?: return null
            val name = declaration.qualifiedName?.asString() ?: return null
            return readOnlyCollections[name] ?: name
        }

        /**
         * @return index key of the requested type or null if every declaration should be considered
         */
        fun lookupKey(type: KSType) = key(type)

        /**
         * @return raw names of the type, all of its supertypes and of its unwrapped type with supertypes;
         * null if the type can't be indexed and should be considered for every requested type
         */
        fun indexKeys(ctx: ProcessingContext, type: KSType): Set<String>? {
            // every class type is assignable to Any
            val keys = hashSetOf("kotlin.Any")
            if (!collectKeys(type, keys)) {
                return null
            }
            val unwrapped = try {
                ctx.serviceTypesHelper.unwrap(type)
            } catch (e: RuntimeException) {
                return null
            }
            if (unwrapped != null && !collectKeys(unwrapped, keys)) {
                return null
            }
            return keys
        }

        private fun collectKeys(type: KSType, keys: MutableSet<String>): Boolean {
            val typeKey = key(type) ?: return false
            if (typeKey == "kotlin.Nothing") {
                // Nothing is assignable to any type
                return false
            }
            keys.add(typeKey)
            for (superType in (type.declaration as KSClassDeclaration).getAllSuperTypes()) {
                keys.add(key(superType) ?: return false)
            }
            return true
        }
    }
}
