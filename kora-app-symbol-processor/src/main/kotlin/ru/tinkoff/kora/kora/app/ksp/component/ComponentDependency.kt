package ru.tinkoff.kora.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import ru.tinkoff.kora.kora.app.ksp.GraphResolutionHelper
import ru.tinkoff.kora.kora.app.ksp.ProcessingContext
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclarations
import ru.tinkoff.kora.ksp.common.CommonClassNames


sealed interface ComponentDependency {
    val claim: DependencyClaim

    fun write(ctx: ProcessingContext, typeToDeclarations: ComponentDeclarations, resolvedComponents: ResolvedComponents): CodeBlock = when (this) {
        is AllOfDependency -> {
            val codeBlock = CodeBlock.builder().add("%T.of(", CommonClassNames.all)
            val dependencyDeclarations = GraphResolutionHelper.findDependencyDeclarations(ctx, typeToDeclarations, claim);
            val dependencies = GraphResolutionHelper.findDependenciesForAllOf(ctx, claim, dependencyDeclarations, resolvedComponents)
            for ((i, dependency) in dependencies.withIndex()) {
                if (i == 0) {
                    codeBlock.indent().add("\n")
                }
                codeBlock.add(dependency.write(ctx, typeToDeclarations, resolvedComponents))
                if (i == dependencies.size - 1) {
                    codeBlock.unindent()
                } else {
                    codeBlock.add(", ")
                }
                codeBlock.add("\n")
            }
            codeBlock.add(")").build()
        }

        is NullDependency -> when (claim.claimType) {
            DependencyClaim.DependencyClaimType.NULLABLE_ONE -> CodeBlock.of("null as %T", claim.type.toTypeName().copy(true))
            DependencyClaim.DependencyClaimType.NULLABLE_VALUE_OF -> CodeBlock.of("null as %T", CommonClassNames.valueOf.parameterizedBy(claim.type.toTypeName()).copy(true))
            DependencyClaim.DependencyClaimType.NULLABLE_PROMISE_OF -> CodeBlock.of("null as %T", CommonClassNames.promiseOf.parameterizedBy(claim.type.toTypeName()).copy(true))
            else -> throw IllegalArgumentException(claim.claimType.toString())
        }


        is PromisedProxyParameterDependency -> {
            val declarations = GraphResolutionHelper.findDependencyDeclarations(ctx, typeToDeclarations, claim)
            if (declarations.size != 1) {
                throw IllegalStateException();
            }
            val dependency = resolvedComponents.getByDeclaration(declarations.first())!!
            CodeBlock.of("it.promiseOf(self.%N.%N)", dependency.holderName, dependency.fieldName)
        }

        is PromiseOfDependency -> {
            if (delegate is NullDependency) {
                CodeBlock.of("%T.promiseOfNull()", CommonClassNames.promiseOf)
            } else {
                val component = delegate.component!!
                if (delegate is WrappedTargetDependency) {
                    CodeBlock.of("it.promiseOf(%N.%N).map { it.value() }.map { it as %T }", component.holderName, component.fieldName, claim.type.toTypeName())
                } else {
                    CodeBlock.of("it.promiseOf(%N.%N).map { it as %T }", component.holderName, component.fieldName, claim.type.toTypeName())
                }
            }
        }

        is TargetDependency -> CodeBlock.of("it.get(%N.%N)", component.holderName, component.fieldName)
        is ValueOfDependency -> {
            if (delegate is NullDependency) {
                CodeBlock.of("%T.valueOfNull()", CommonClassNames.valueOf)
            } else {
                val component = delegate.component!!
                if (delegate is WrappedTargetDependency) {
                    CodeBlock.of("it.valueOf(%N.%N).map { it.value() }.map { it as %T }", component.holderName, component.fieldName, claim.type.toTypeName())
                } else {
                    CodeBlock.of("it.valueOf(%N.%N).map { it as %T }", component.holderName, component.fieldName, claim.type.toTypeName())
                }
            }
        }

        is WrappedTargetDependency -> CodeBlock.of("it.get(%N.%N).value()", component.holderName, component.fieldName)
        is TypeOfDependency -> buildTypeRef(claim.type)
    }

    sealed interface SingleDependency : ComponentDependency {
        val component: ResolvedComponent?
    }

    data class TargetDependency(override val claim: DependencyClaim, override val component: ResolvedComponent) : SingleDependency

    data class WrappedTargetDependency(override val claim: DependencyClaim, override val component: ResolvedComponent) : SingleDependency

    data class NullDependency(override val claim: DependencyClaim) : SingleDependency {
        override val component = null
    }


    data class ValueOfDependency(override val claim: DependencyClaim, val delegate: SingleDependency) : SingleDependency {
        override val component
            get() = delegate.component
    }

    data class PromiseOfDependency(override val claim: DependencyClaim, val delegate: SingleDependency) : SingleDependency {
        override val component
            get() = delegate.component
    }

    data class TypeOfDependency(override val claim: DependencyClaim) : ComponentDependency {
        fun buildTypeRef(typeRef: KSType): CodeBlock {
            val typeParameterResolver = typeRef.declaration.typeParameters.toTypeParameterResolver()
            var declaration = typeRef.declaration
            if (declaration is KSTypeAlias) {
                declaration = declaration.type.resolve().declaration
            }
            if (declaration is KSClassDeclaration) {
                val b = CodeBlock.builder()
                val typeArguments = typeRef.arguments

                if (typeArguments.isEmpty()) {
                    b.add("%T.of(%T::class.java)", CommonClassNames.typeRef, declaration.toClassName())
                } else {
                    b.add("%T.of(%T::class.java", CommonClassNames.typeRef, declaration.toClassName())
                    for (typeArgument in typeArguments) {
                        b.add(",\n%L", buildTypeRef(typeArgument.type!!.resolve()))
                    }
                    b.add("\n)")
                }
                return b.build()
            } else {
                return CodeBlock.of("%T.of(%T::class.java)", CommonClassNames.typeRef, typeRef.toTypeName(typeParameterResolver))
            }
        }
    }

    data class AllOfDependency(override val claim: DependencyClaim) : ComponentDependency

    data class PromisedProxyParameterDependency(val declaration: ComponentDeclaration, override val claim: DependencyClaim) : ComponentDependency

}
