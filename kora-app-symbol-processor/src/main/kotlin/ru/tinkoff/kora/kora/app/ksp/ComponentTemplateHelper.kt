package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*

object ComponentTemplateHelper {
    sealed interface TemplateMatch {
        object None : TemplateMatch
        data class Some(val map: Map<TypeParameterWrapper, KSType>) : TemplateMatch
    }

    class TypeParameterWrapper(val type: KSTypeParameter) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TypeParameterWrapper

            val otherParentDeclaration = other.type.parentDeclaration!!
            val parentDeclaration = type.parentDeclaration!!
            if (otherParentDeclaration is KSClassDeclaration && parentDeclaration is KSClassDeclaration) {
                return parentDeclaration.qualifiedName?.asString() == otherParentDeclaration.qualifiedName?.asString()
            }
            if (otherParentDeclaration is KSFunctionDeclaration && parentDeclaration is KSFunctionDeclaration) {
                return parentDeclaration.qualifiedName?.asString() == otherParentDeclaration.qualifiedName?.asString()
            }

            return false
        }

        override fun hashCode(): Int {
            return type.name.asString().hashCode()
        }

        override fun toString(): String {
            return "${type.parentDeclaration!!.simpleName.asString()}->${type.name.asString()}"
        }
    }

    fun match(ctx: ProcessingContext, templateTypeArguments: List<KSTypeParameter>, templateType: KSType, requiredType: KSType): TemplateMatch {
        if (!requiredType.starProjection().isAssignableFrom(templateType.starProjection())) {
            return TemplateMatch.None
        }
        val map = HashMap<TypeParameterWrapper, KSType>()
        fun fillMap(template: KSType, required: KSType): Boolean {
            val templateDeclaration = template.declaration
            if (templateDeclaration is KSTypeParameter) {
                if (templateDeclaration.bounds.any {
                        !it.resolve().fixSelfReference(ctx.resolver, templateDeclaration, required).isAssignableFrom(required)
                    }) {
                    return false
                }
                val oldValue = map.put(TypeParameterWrapper(templateDeclaration), required)
                if (oldValue != null) {
                    fillMap(oldValue, required)
                }
                return true
            }
            if (template.declaration.qualifiedName?.asString() != required.declaration.qualifiedName?.asString()) {
                return false
            }
            for ((templateArg, requiredArg) in template.arguments.zip(required.arguments)) {
                if (!fillMap(templateArg.type!!.resolve(), requiredArg.type!!.resolve())) {
                    return false
                }
            }
            return true
        }

        initMap(map, templateType)
        val commonType = findCommonType(templateType, requiredType.declaration)
        if (!fillMap(commonType, requiredType)) {
            return TemplateMatch.None
        }
        for (templateTypeArg in templateTypeArguments) {
            if (!map.containsKey(TypeParameterWrapper(templateTypeArg))) {
                return TemplateMatch.None
            }
        }
        return TemplateMatch.Some(map)
    }

    private fun KSType.fixSelfReference(resolver: Resolver, tp: KSTypeParameter, withType: KSType): KSType {
        val map = mapOf(TypeParameterWrapper(tp) to withType)
        return replace(resolver, this, map)!!
    }

    private fun initMap(map: MutableMap<TypeParameterWrapper, KSType>, type: KSType) {
        val declaration = type.declaration
        for ((typeArg, typeArgType) in declaration.typeParameters.zip(type.arguments)) {
            map[TypeParameterWrapper(typeArg)] = typeArgType.type!!.resolve()
        }
        if (declaration is KSClassDeclaration) {
            for (parent in declaration.superTypes) {
                initMap(map, parent.resolve())
            }
        }
    }

    private fun findCommonType(templateType: KSType, declaration: KSDeclaration): KSType {
        val templateDecl = templateType.declaration as KSClassDeclaration
        if (templateDecl.qualifiedName?.asString() == declaration.qualifiedName?.asString()) {
            return templateType
        }
        return templateDecl.superTypes
            .firstNotNullOf {
                val type = it.resolve()
                findCommonType(type, declaration)
            }

    }


    fun replace(resolver: Resolver, t: KSType, map: Map<TypeParameterWrapper, KSType>): KSType? {
        if (t.declaration is KSTypeParameter) {
            return replace(resolver, t.declaration as KSTypeParameter, map)?.type?.resolve()
        }
        if (t.arguments.isEmpty()) {
            return t
        }
        var changed = false
        val newArguments = ArrayList<KSTypeArgument>(t.arguments.size)
        for (argument in t.arguments) {
            val ta = replace(resolver, argument.type!!.resolve(), map)
            if (ta !== argument) {
                changed = true
            }
            if (ta == null) {
                return null
            }
            newArguments.add(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(ta), argument.variance))
        }
        if (changed) {
            return t.replace(newArguments)
        } else {
            return t
        }
    }

    fun replace(resolver: Resolver, type: KSTypeParameter, map: Map<TypeParameterWrapper, KSType>): KSTypeArgument? {
        val argumentType = map[TypeParameterWrapper(type)]!!
        if (!argumentType.hasGenericVariable()) {
            return resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(argumentType), type.variance)
        }
        if (argumentType.arguments.isEmpty()) {
            return resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(argumentType), type.variance)
        }
        val argumentDeclaration = argumentType.declaration as KSClassDeclaration
        var changed = false
        val newArguments = ArrayList<KSTypeArgument>(argumentType.arguments.size)
        for ((i, typeDeclarationParameter) in argumentDeclaration.typeParameters.withIndex()) {
            val typeParameter = argumentType.arguments[i]
            val ta = map[TypeParameterWrapper(typeDeclarationParameter)]
            if (ta != null && ta !== typeParameter) {
                changed = true
                newArguments.add(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(ta), typeDeclarationParameter.variance))
            } else if (ta == null) {
                return null
            } else {
                newArguments.add(typeParameter)
            }
        }
        if (!changed) {
            return resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(argumentType), type.variance)
        }
        val replaced = argumentType.replace(newArguments)
        return resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(replaced), type.variance)
    }

    private fun KSTypeArgument.hasGenericVariable(): Boolean {
        val type = this.type
        if (type == null) {
            return true
        }
        val resolvedType = type.resolve()
        if (resolvedType.declaration is KSTypeParameter) {
            return true
        }
        return type.resolve().hasGenericVariable()
    }

    private fun KSType.hasGenericVariable(): Boolean {
        if (this.declaration is KSTypeParameter) {
            return true
        }

        for (param in this.arguments) {
            if (param.hasGenericVariable()) {
                return true
            }
        }
        return false
    }
}

