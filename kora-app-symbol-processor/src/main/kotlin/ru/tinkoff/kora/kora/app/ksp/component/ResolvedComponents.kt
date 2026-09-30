package ru.tinkoff.kora.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.kora.app.ksp.ProcessingContext
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclarations
import java.util.*

/**
 * Resolved components in resolution order (position == [ResolvedComponent.index])
 * with lookups by declaration and by "raw type name -> components" index.
 */
class ResolvedComponents private constructor(
    private val ctx: ProcessingContext,
    private val components: MutableList<ResolvedComponent>,
    private val declarationToComponent: IdentityHashMap<ComponentDeclaration, ResolvedComponent>,
    private val typeToComponents: MutableMap<String, MutableList<ResolvedComponent>>,
    // components whose type hierarchy can't be indexed reliably: they are candidates for every type
    private val notIndexed: MutableList<ResolvedComponent>,
) {
    constructor(ctx: ProcessingContext) : this(ctx, ArrayList(256), IdentityHashMap(256), HashMap(), ArrayList())

    constructor(that: ResolvedComponents) : this(
        that.ctx,
        ArrayList(that.components),
        IdentityHashMap(that.declarationToComponent),
        that.typeToComponents.mapValuesTo(HashMap(that.typeToComponents.size)) { ArrayList(it.value) },
        ArrayList(that.notIndexed),
    )

    val size get() = components.size

    operator fun get(index: Int) = components[index]

    fun components(): List<ResolvedComponent> = Collections.unmodifiableList(components)

    fun add(component: ResolvedComponent) {
        components.add(component)
        declarationToComponent.putIfAbsent(component.declaration, component)
        val keys = ComponentDeclarations.indexKeys(ctx, component.type)
        if (keys == null) {
            notIndexed.add(component)
        } else {
            for (key in keys) {
                typeToComponents.getOrPut(key) { ArrayList() }.add(component)
            }
        }
    }

    /**
     * @return first resolved component with exactly this declaration (by reference)
     */
    fun getByDeclaration(declaration: ComponentDeclaration): ResolvedComponent? = declarationToComponent[declaration]

    /**
     * @return components that may be assignable to the given type, in resolution order
     */
    fun getByType(type: KSType): List<ResolvedComponent> {
        val key = ComponentDeclarations.lookupKey(type) ?: return components()
        val indexed = typeToComponents[key] ?: emptyList()
        if (notIndexed.isEmpty()) {
            return Collections.unmodifiableList(indexed)
        }
        return (indexed + notIndexed).sortedBy { it.index }
    }
}
