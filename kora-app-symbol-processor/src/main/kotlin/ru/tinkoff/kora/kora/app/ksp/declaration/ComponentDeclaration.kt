package ru.tinkoff.kora.kora.app.ksp.declaration

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.TypeName
import ru.tinkoff.kora.kora.app.ksp.ProcessingContext
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.fixPlatformType
import ru.tinkoff.kora.ksp.common.TagUtils
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

sealed interface ComponentDeclaration {
    val type: KSType
    val source: KSDeclaration
    val tags: Set<String>

    fun isTemplate(): Boolean {
        for (argument in type.arguments) {
            if (argument.hasGenericVariable()) {
                return true
            }
        }
        return false
    }

    fun isDefault(): Boolean {
        return false
    }

    data class FromModuleComponent(
        override val type: KSType,
        val module: ModuleDeclaration,
        override val tags: Set<String>,
        val method: KSFunctionDeclaration,
        val methodParameterTypes: List<KSType>,
        val typeVariables: List<KSTypeArgument>
    ) : ComponentDeclaration {
        override val source get() = this.method
        override fun isDefault(): Boolean {
            return method.findAnnotation(CommonClassNames.defaultComponent) != null
        }
    }

    data class AnnotatedComponent(
        override val type: KSType,
        val classDeclaration: KSClassDeclaration,
        override val tags: Set<String>,
        val constructor: KSFunctionDeclaration,
        val methodParameterTypes: List<KSType>,
        val typeVariables: List<KSTypeArgument>
    ) : ComponentDeclaration {
        override val source get() = this.constructor
    }

    data class DiscoveredAsDependencyComponent(
        override val type: KSType,
        val classDeclaration: KSClassDeclaration,
        val constructor: KSFunctionDeclaration,
        override val tags: Set<String>
    ) : ComponentDeclaration {
        override val source get() = this.constructor
    }

    data class FromExtensionComponent(
        override val type: KSType,
        val sourceMethod: KSFunctionDeclaration,
        val methodParameterTypes: List<KSType>,

        ) : ComponentDeclaration {
        override val source get() = this.sourceMethod
        override val tags get() = setOf<String>()

    }

    data class PromisedProxyComponent(
        override val type: KSType,
        val classDeclaration: KSClassDeclaration,
        val className: TypeName

    ) : ComponentDeclaration {
        override val source get() = this.classDeclaration
        override val tags get() = setOf(CommonClassNames.promisedProxy.canonicalName)
    }


    data class OptionalComponent(
        override val type: KSType,
        override val tags: Set<String>
    ) : ComponentDeclaration {
        override val source get() = type.declaration
    }


    companion object {
        fun fromModule(ctx: ProcessingContext, module: ModuleDeclaration, method: KSFunctionDeclaration): FromModuleComponent {
            // modules can be written in java so we better fix platform nullability
            val type = method.returnType!!.resolve().fixPlatformType(ctx.resolver)
            if (type.isError) {
                throw ProcessingErrorException("Component type is not resolvable in the current round of processing", method)
            }
            val tags = TagUtils.parseTagValue(method)
            val parameterTypes = method.parameters.map { it.type.resolve().fixPlatformType(ctx.resolver) }
            val typeParameters = method.typeParameters.map {
                val t = it.bounds.firstOrNull()?.resolve()?.fixPlatformType(ctx.resolver) ?: ctx.resolver.builtIns.anyType

                ctx.resolver.getTypeArgument(
                    ctx.resolver.createKSTypeReferenceFromKSType(t),
                    it.variance
                )
            }
            return FromModuleComponent(type, module, tags, method, parameterTypes, typeParameters)
        }

        fun fromAnnotated(ctx: ProcessingContext, classDeclaration: KSClassDeclaration): AnnotatedComponent {
            val constructor = classDeclaration.primaryConstructor
            if (constructor == null) {
                throw ProcessingErrorException("@Component annotated class should have primary constructor", classDeclaration)
            }
            val typeParameters = classDeclaration.typeParameters.map {
                val t = it.bounds.firstOrNull()?.resolve() ?: ctx.resolver.builtIns.anyType

                ctx.resolver.getTypeArgument(
                    ctx.resolver.createKSTypeReferenceFromKSType(t),
                    it.variance
                )
            }
            val type = classDeclaration.asType(classDeclaration.typeParameters.map { ctx.resolver.getTypeArgument(it.bounds.first(), it.variance) })
            val tags = TagUtils.parseTagValue(classDeclaration)
            val parameterTypes = constructor.parameters.map { it.type.resolve() }

            return AnnotatedComponent(type, classDeclaration, tags, constructor, parameterTypes, typeParameters)
        }

        fun fromDependency(ctx: ProcessingContext, classDeclaration: KSClassDeclaration): DiscoveredAsDependencyComponent {
            val constructor = classDeclaration.primaryConstructor
            if (constructor == null) {
                throw ProcessingErrorException("No primary constructor to parse component for: $classDeclaration", classDeclaration)
            }
            val type = classDeclaration.asType(listOf())
            if (type.isError) {
                throw ProcessingErrorException("Component type is not resolvable in the current round of processing", classDeclaration)
            }
            val tags = TagUtils.parseTagValue(classDeclaration)

            return DiscoveredAsDependencyComponent(type, classDeclaration, constructor, tags)
        }

        fun fromExtension(ctx: ProcessingContext, extensionResult: ExtensionResult.GeneratedResult): FromExtensionComponent {
            val sourceMethod = extensionResult.constructor
            val sourceType = extensionResult.type
            val parameterTypes = sourceType.parameterTypes.map { it!!.fixPlatformType(ctx.resolver) }
            val type = sourceType.returnType!!
            if (type.isError) {
                throw ProcessingErrorException("Component type is not resolvable in the current round of processing", sourceMethod)
            }
            return FromExtensionComponent(type, sourceMethod, parameterTypes)
        }
    }
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

    for (param in resolvedType.arguments) {
        if (param.hasGenericVariable()) {
            return true
        }
    }
    return false
}
