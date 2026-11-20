package io.koraframework.database.symbol.processor.mongo

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter

/**
 * Expands typealiases at every level of a type and substitutes the alias type arguments, so `Tags<Login>` with
 * `typealias Tags<T> = List<T>` and `typealias Login = String` becomes `List<String>`. The codec and parameter
 * generators recognise native, collection and map types by their declaration, which an alias hides.
 */
internal fun KSType.expandTypeAliases(resolver: Resolver): KSType {
    val alias = this.declaration as? KSTypeAlias
    if (alias != null) {
        val bindings = alias.typeParameters.map { it.name.asString() }.zip(this.arguments).toMap()
        val underlying = substitute(alias.type.resolve(), bindings, resolver).expandTypeAliases(resolver)
        return if (this.isMarkedNullable) underlying.makeNullable() else underlying
    }
    return this.mapArguments(resolver) { it.expandTypeAliases(resolver) }
}

private fun substitute(type: KSType, bindings: Map<String, KSTypeArgument>, resolver: Resolver): KSType {
    val parameter = type.declaration as? KSTypeParameter
    if (parameter != null) {
        val bound = bindings[parameter.name.asString()]?.type?.resolve() ?: return type
        // List<V?> with V = Int is List<Int?>
        return if (type.isMarkedNullable) bound.makeNullable() else bound
    }
    return type.mapArguments(resolver) { substitute(it, bindings, resolver) }
}

// the type is rebuilt only when an argument changed, so a type without aliases stays exactly as resolved
private fun KSType.mapArguments(resolver: Resolver, transform: (KSType) -> KSType): KSType {
    var changed = false
    val arguments = this.arguments.map { argument ->
        val type = argument.type?.resolve() ?: return@map argument
        val transformed = transform(type)
        // identity: KSType equality treats an alias as equal to the type it stands for
        if (transformed === type) {
            argument
        } else {
            changed = true
            resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(transformed), argument.variance)
        }
    }
    if (!changed) {
        return this
    }
    val replaced = this.replace(arguments)
    return if (this.isMarkedNullable) replaced.makeNullable() else replaced
}
