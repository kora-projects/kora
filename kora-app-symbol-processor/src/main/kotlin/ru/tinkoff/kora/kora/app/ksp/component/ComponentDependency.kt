package ru.tinkoff.kora.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.kora.app.ksp.GraphResolutionHelper
import ru.tinkoff.kora.kora.app.ksp.ProcessingContext
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.ksp.common.CommonClassNames

sealed interface ComponentDependency {
    val claim: DependencyClaim

    fun write(ctx: ProcessingContext, resolvedComponents: List<ResolvedComponent>): CodeBlock

    sealed interface SingleDependency : ComponentDependency {
        val component: ResolvedComponent?
    }

    data class TargetDependency(override val claim: DependencyClaim, override val component: ResolvedComponent) : SingleDependency {
        override fun write(ctx: ProcessingContext, resolvedComponents: List<ResolvedComponent>): CodeBlock {
            return CodeBlock.of("it.get(%L)", component.name)
        }
    }

    data class WrappedTargetDependency(override val claim: DependencyClaim, override val component: ResolvedComponent) : SingleDependency {
        override fun write(ctx: ProcessingContext, resolvedComponents: List<ResolvedComponent>): CodeBlock {
            return CodeBlock.of("it.get(%L).value()", component.name)
        }
    }

    data class NullDependency(override val claim: DependencyClaim) : SingleDependency {
        override val component = null

        override fun write(ctx: ProcessingContext, resolvedComponents: List<ResolvedComponent>): CodeBlock {
            return when (claim.claimType) {
                DependencyClaim.DependencyClaimType.NULLABLE_ONE -> CodeBlock.of("null as %T", claim.type.toTypeName().copy(true))
                DependencyClaim.DependencyClaimType.NULLABLE_VALUE_OF -> CodeBlock.of("null as %T", CommonClassNames.valueOf.parameterizedBy(claim.type.toTypeName()).copy(true))
                DependencyClaim.DependencyClaimType.NULLABLE_PROMISE_OF -> CodeBlock.of("null as %T", CommonClassNames.promiseOf.parameterizedBy(claim.type.toTypeName()).copy(true))
                else -> throw IllegalArgumentException(claim.claimType.toString())
            }
        }
    }


    data class ValueOfDependency(override val claim: DependencyClaim, val delegate: SingleDependency) : SingleDependency {
        override val component
            get() = delegate.component

        override fun write(ctx: ProcessingContext, resolvedComponents: List<ResolvedComponent>): CodeBlock {
            if (delegate is NullDependency) {
                return CodeBlock.of("%T.valueOfNull()", CommonClassNames.valueOf)
            }
            val component = delegate.component!!
            if (delegate is WrappedTargetDependency) {
                return CodeBlock.of("it.valueOf(%L).map { it.value() }.map { it as %T }", component.name, claim.type.toTypeName())
            }
            return CodeBlock.of("it.valueOf(%L).map { it as %T }", component.name, claim.type.toTypeName())
        }
    }

    data class PromiseOfDependency(override val claim: DependencyClaim, val delegate: SingleDependency) : SingleDependency {
        override val component
            get() = delegate.component

        override fun write(ctx: ProcessingContext, resolvedComponents: List<ResolvedComponent>): CodeBlock {
            if (delegate is NullDependency) {
                return CodeBlock.of("%T.promiseOfNull()", CommonClassNames.promiseOf)
            }
            val component = delegate.component!!
            if (delegate is WrappedTargetDependency) {
                return CodeBlock.of("it.promiseOf(%L).map { it.value() }.map { it as %T }", component.name, claim.type.toTypeName())
            }
            return CodeBlock.of("it.promiseOf(%L).map { it as %T }", component.name, claim.type.toTypeName())
        }
    }

    data class TypeOfDependency(override val claim: DependencyClaim) : ComponentDependency {
        override fun write(ctx: ProcessingContext, resolvedComponents: List<ResolvedComponent>): CodeBlock {
            return buildTypeRef(claim.type)
        }

        private fun buildTypeRef(typeRef: KSType): CodeBlock {
            val typeParameterResolver = typeRef.declaration.typeParameters.toTypeParameterResolver()
            var declaration = typeRef.declaration
            if (declaration is KSTypeAlias) {
                declaration = declaration.type.resolve().declaration
            }
            if (declaration is KSClassDeclaration) {
                val b = CodeBlock.builder()
                val typeArguments = typeRef.arguments

                if (typeArguments.isEmpty()) {
                    b.add("%T.of(%T::class.java)", TypeRef::class, declaration.toClassName())
                } else {
                    b.add("%T.of(%T::class.java", TypeRef::class, declaration.toClassName())
                    for (typeArgument in typeArguments) {
                        b.add(",\n%L", buildTypeRef(typeArgument.type!!.resolve()))
                    }
                    b.add("\n)")
                }
                return b.build()
            } else {
                return CodeBlock.of("%T.of(%T::class.java)", TypeRef::class, typeRef.toTypeName(typeParameterResolver))
            }
        }
    }

    data class AllOfDependency(override val claim: DependencyClaim) : ComponentDependency {
        override fun write(ctx: ProcessingContext, resolvedComponents: List<ResolvedComponent>): CodeBlock {
            val codeBlock = CodeBlock.builder().add("%T.of(", CommonClassNames.all)
            val dependencies = GraphResolutionHelper.findDependenciesForAllOf(ctx, claim, resolvedComponents)
            for ((i, dependency) in dependencies.withIndex()) {
                if (i == 0) {
                    codeBlock.indent().add("\n")
                }
                codeBlock.add(dependency.write(ctx, resolvedComponents))
                if (i == dependencies.size - 1) {
                    codeBlock.unindent()
                } else {
                    codeBlock.add(", ")
                }
                codeBlock.add("\n")
            }
            return codeBlock.add(")").build()
        }
    }

    data class PromisedProxyParameterDependency(val declaration: ComponentDeclaration, override val claim: DependencyClaim) : ComponentDependency {
        override fun write(ctx: ProcessingContext, resolvedComponents: List<ResolvedComponent>): CodeBlock {
            val dependencies = GraphResolutionHelper.findDependency(ctx, declaration, resolvedComponents, this.claim)
            return CodeBlock.of("it.promiseOf(self.%L)", dependencies!!.component!!.name)
        }

    }

}
