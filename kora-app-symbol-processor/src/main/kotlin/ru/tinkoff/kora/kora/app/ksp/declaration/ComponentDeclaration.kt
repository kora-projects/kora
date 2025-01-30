package ru.tinkoff.kora.kora.app.ksp.declaration

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import ru.tinkoff.kora.kora.app.ksp.ProcessingContext
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.fixPlatformType
import ru.tinkoff.kora.ksp.common.TagUtils
import ru.tinkoff.kora.ksp.common.TagUtils.parseTags
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

sealed interface ComponentDeclaration {
    val type: KSType
    val source: KSDeclaration
    val tags: Set<String>

    fun declarationString(): String

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
        override fun declarationString() = module.element.qualifiedName?.asString() + "." + method.simpleName.asString()

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
        override fun declarationString() = classDeclaration.qualifiedName?.asString().toString()
    }

    data class DiscoveredAsDependencyComponent(
        override val type: KSType,
        val classDeclaration: KSClassDeclaration,
        val constructor: KSFunctionDeclaration,
        override val tags: Set<String>
    ) : ComponentDeclaration {
        override val source get() = this.constructor
        override fun declarationString() = classDeclaration.qualifiedName?.asString().toString()
    }

    data class FromExtensionComponent(
        override val type: KSType,
        override val source: KSDeclaration,
        val methodParameterTypes: List<KSType>,
        val methodParameterTags: List<Set<String>>,
        override val tags: Set<String>,
        val generator: (CodeBlock) -> CodeBlock
    ) : ComponentDeclaration {
        override fun declarationString(): String {
            return source.parentDeclaration?.qualifiedName?.asString().toString() + source.simpleName.asString()
        }

    }

    data class PromisedProxyComponent(
        override val type: KSType,
        val classDeclaration: KSClassDeclaration,
        val className: TypeName

    ) : ComponentDeclaration {
        override val source get() = this.classDeclaration
        override val tags get() = setOf(CommonClassNames.promisedProxy.canonicalName)
        override fun declarationString() = "<Proxy>"
    }


    data class OptionalComponent(
        override val type: KSType,
        override val tags: Set<String>
    ) : ComponentDeclaration {
        override val source get() = type.declaration
        override fun declarationString() = "Optional.empty"
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
            val type = classDeclaration.asType(listOf())
            val tags = TagUtils.parseTagValue(classDeclaration)
            val parameterTypes = constructor.parameters.map { it.type.resolve() }

            return AnnotatedComponent(type, classDeclaration, tags, constructor, parameterTypes, typeParameters)
        }

        fun fromDependency(@Suppress("UNUSED_PARAMETER") ctx: ProcessingContext, classDeclaration: KSClassDeclaration, type: KSType): DiscoveredAsDependencyComponent {
            val constructor = classDeclaration.primaryConstructor
            if (constructor == null) {
                throw ProcessingErrorException("No primary constructor to parse component for: $classDeclaration", classDeclaration)
            }
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
            val parameterTags = sourceMethod.parameters.map { it.parseTags() }
            val type = sourceType.returnType!!
            if (type.isError) {
                throw ProcessingErrorException("Component type is not resolvable in the current round of processing", sourceMethod)
            }
            val tag = if (sourceMethod.isConstructor()) {
                sourceMethod.closestClassDeclaration()!!.parseTags()
            } else {
                sourceMethod.parseTags()
            }

            return FromExtensionComponent(type, sourceMethod, parameterTypes, parameterTags, tag) {
                if (sourceMethod.isConstructor()) {
                    val clazz = sourceMethod.closestClassDeclaration()!!
                    CodeBlock.of("%T(%L)", clazz.toClassName(), it)
                } else {
                    val parent = sourceMethod.parentDeclaration
                    if (parent is KSClassDeclaration) {
                        CodeBlock.of("%M(%L)", MemberName(parent.toClassName(), sourceMethod.simpleName.asString()), it)
                    } else {
                        CodeBlock.of("%M(%L)", MemberName(sourceMethod.packageName.asString(), sourceMethod.simpleName.asString()), it)
                    }
                }
            }
        }

        fun fromExtension(extensionResult: ExtensionResult.CodeBlockResult): FromExtensionComponent {
            return FromExtensionComponent(
                extensionResult.componentType,
                extensionResult.source,
                extensionResult.dependencyTypes,
                extensionResult.dependencyTags,
                extensionResult.componentTag,
                extensionResult.codeBlock
            )
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
