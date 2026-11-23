package io.koraframework.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import io.koraframework.kora.app.ksp.ProcessingContext
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration
import io.koraframework.ksp.common.CommonClassNames


sealed interface ComponentDependency {
    val claim: DependencyClaim

    fun write(ctx: ProcessingContext): CodeBlock = when (this) {
        is AllOfDependency -> {
            val codeBlock = CodeBlock.builder()
            // explicit type arguments: a generic vararg call with many generic arguments is inferred in superlinear time
            when (claim.claimType) {
                DependencyClaim.DependencyClaimType.ALL -> codeBlock.add("%T.all<%T>(it", CommonClassNames.all, claim.type.toTypeName())
                DependencyClaim.DependencyClaimType.ALL_OF_VALUE -> codeBlock.add("%T.allValues<%T>(it", CommonClassNames.all, claim.type.toTypeName())
                DependencyClaim.DependencyClaimType.ALL_OF_PROMISE -> codeBlock.add("%T.allPromises<%T>(it", CommonClassNames.all, claim.type.toTypeName())
                else -> throw IllegalStateException("Kora internal error: unsupported All<T> claim type for code generation: $claim")
            }
            for (dependency in resolvedDependencies) {
                codeBlock.add(", %L", nodeWithMapper(dependency))
            }
            codeBlock.add(")").build()
        }

        is NullDependency -> {
            when (claim.claimType) {
                DependencyClaim.DependencyClaimType.NULLABLE_ONE -> CodeBlock.of("null as %T", claim.type.toTypeName().copy(true))
                DependencyClaim.DependencyClaimType.NULLABLE_VALUE_OF -> CodeBlock.of("null as %T", CommonClassNames.valueOf.parameterizedBy(claim.type.toTypeName()).copy(true))
                DependencyClaim.DependencyClaimType.NULLABLE_PROMISE_OF -> CodeBlock.of("null as %T", CommonClassNames.promiseOf.parameterizedBy(claim.type.toTypeName()).copy(true))
                else -> throw IllegalStateException("Kora internal error: unsupported nullable dependency claim type for code generation: $claim")
            }
        }


        is PromisedProxyParameterDependency -> {
            val dependency = realDependency
            CodeBlock.of("it.promiseOf(self.%N.%N)", dependency.holderName, dependency.fieldName)
        }

        is PromiseOfDependency -> {
            if (delegate is NullDependency) {
                CodeBlock.of("%T.promiseOfNull()", CommonClassNames.promiseOf)
            } else {
                val component = delegate.component!!
                if (delegate is WrappedTargetDependency) {
                    nullIfConditionFailed(claim, component, CodeBlock.of("it.promiseOf(%N.%N).map { it.value() }", component.holderName, component.fieldName))
                } else {
                    nullIfConditionFailed(claim, component, CodeBlock.of("it.promiseOf(%N.%N)", component.holderName, component.fieldName))
                }
            }
        }

        is TargetDependency -> {
            if (claim.claimType == DependencyClaim.DependencyClaimType.NODE_OF) {
                CodeBlock.of("%N.%N", component.holderName, component.fieldName)
            } else if (claim.claimType == DependencyClaim.DependencyClaimType.NULLABLE_ONE) {
                CodeBlock.of("it.getNullable(%N.%N)", component.holderName, component.fieldName)
            } else {
                CodeBlock.of("it.get(%N.%N)", component.holderName, component.fieldName)
            }
        }

        is ValueOfDependency -> {
            if (delegate is NullDependency) {
                CodeBlock.of("%T.valueOfNull()", CommonClassNames.valueOf)
            } else {
                val component = delegate.component!!
                if (delegate is WrappedTargetDependency) {
                    nullIfConditionFailed(claim, component, CodeBlock.of("it.valueOf(%N.%N).map { it.value() }", component.holderName, component.fieldName))
                } else {
                    nullIfConditionFailed(claim, component, CodeBlock.of("it.valueOf(%N.%N)", component.holderName, component.fieldName))
                }
            }
        }

        is WrappedTargetDependency -> {
            CodeBlock.of("it.get(%N.%N).value()", component.holderName, component.fieldName)
        }

        is TypeOfDependency -> {
            buildTypeRef(claim.type)
        }

        is OneOfDependency -> {
            val b = CodeBlock.builder()
            when (claim.claimType) {
                DependencyClaim.DependencyClaimType.ONE_REQUIRED -> b.add("it.getOneOf(")
                DependencyClaim.DependencyClaimType.VALUE_OF -> b.add("it.getOneValueOf(")
                DependencyClaim.DependencyClaimType.PROMISE_OF -> b.add("it.getOnePromiseOf(")
                else -> throw IllegalStateException("Kora internal error: unsupported one-of dependency claim type for code generation: $claim")
            }
            for ((i, dependency) in dependencies.withIndex()) {
                if (i > 0) b.add(", ")
                b.add(nodeWithMapper(dependency))
            }
            b.add(")").build()
        }

        is GraphDependency -> CodeBlock.of("it")
    }

    private fun nodeWithMapper(dependency: SingleDependency): CodeBlock {
        val component = dependency.component!!
        val dependencyNode = component.nodeRef("some_fake_holder_idc")
        val wrapped = when (dependency) {
            is WrappedTargetDependency -> true
            is ValueOfDependency -> dependency.delegate is WrappedTargetDependency
            is PromiseOfDependency -> dependency.delegate is WrappedTargetDependency
            else -> false
        }
        return if (wrapped) {
            CodeBlock.of("%T.unwrap<%T, %T>(%L)", CommonClassNames.nodeWithMapper, dependency.claim.type.toTypeName(), component.type.toTypeName(), dependencyNode)
        } else {
            CodeBlock.of("%T.node<%T>(%L)", CommonClassNames.nodeWithMapper, dependency.claim.type.toTypeName(), dependencyNode)
        }
    }

    /**
     * A nullable ValueOf/PromiseOf of a conditional component is null when the component condition failed, same as a nullable component itself
     */
    private fun nullIfConditionFailed(claim: DependencyClaim, component: ResolvedComponent, dependency: CodeBlock): CodeBlock {
        val nullable = claim.claimType == DependencyClaim.DependencyClaimType.NULLABLE_VALUE_OF || claim.claimType == DependencyClaim.DependencyClaimType.NULLABLE_PROMISE_OF
        if (!nullable || component.declaration.condition == null && component.getParentConditions().isEmpty()) {
            return dependency
        }
        return CodeBlock.of("(if (%N.%N.condition()!!.apply(it) is %T.ConditionResult.Failed) null else %L)", component.holderName, component.fieldName, CommonClassNames.graphCondition, dependency)
    }

    sealed interface SingleDependency : ComponentDependency {
        val component: ResolvedComponent?
    }

    data class TargetDependency(override val claim: DependencyClaim, override val component: ResolvedComponent) : SingleDependency

    data class OneOfDependency(override val claim: DependencyClaim, val dependencies: List<SingleDependency>) : ComponentDependency

    data class WrappedTargetDependency(override val claim: DependencyClaim, override val component: ResolvedComponent) : SingleDependency

    data class NullDependency(override val claim: DependencyClaim) : SingleDependency {
        override val component = null
    }


    data class ValueOfDependency(override val claim: DependencyClaim, val delegate: SingleDependency) : SingleDependency {
        override val component: ResolvedComponent
            get() = delegate.component!!
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

    data class GraphDependency(override val claim: DependencyClaim) : ComponentDependency

    data class AllOfDependency(override val claim: DependencyClaim) : ComponentDependency {
        val resolvedDependencies: MutableList<SingleDependency> = ArrayList()


        fun addResolved(resolvedComponents: List<SingleDependency>) {
            this.resolvedDependencies.addAll(resolvedComponents)
        }

        override fun toString(): String {
            return "AllOfDependency(claim=$claim)"
        }
    }

    data class PromisedProxyParameterDependency(val declaration: ComponentDeclaration, override val claim: DependencyClaim) : ComponentDependency {
        lateinit var realDependency: ResolvedComponent
    }

}
