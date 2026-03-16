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
            when (claim.claimType) {
                DependencyClaim.DependencyClaimType.ALL -> codeBlock.add("%T.all(it", CommonClassNames.all)
                DependencyClaim.DependencyClaimType.ALL_OF_VALUE -> codeBlock.add("%T.allValues(it", CommonClassNames.all)
                DependencyClaim.DependencyClaimType.ALL_OF_PROMISE -> codeBlock.add("%T.allPromises(it", CommonClassNames.all)
                else -> throw IllegalStateException("Unexpected dependency type ${claim.claimType}")
            }
            for (dependency in resolvedDependencies) {
                val dependencyNode = dependency.component!!.nodeRef("some_fake_holder_idc")
                if (dependency is ValueOfDependency && dependency.delegate is WrappedTargetDependency || dependency is PromiseOfDependency && dependency.delegate is WrappedTargetDependency || dependency is WrappedTargetDependency) {
                    codeBlock.add(", %T.unwrap(%L)", CommonClassNames.nodeWithMapper, dependencyNode)
                } else {
                    codeBlock.add(", %T.node(%L)", CommonClassNames.nodeWithMapper, dependencyNode)
                }
            }
            codeBlock.add(")").build()
        }

        is NullDependency -> {
            when (claim.claimType) {
                DependencyClaim.DependencyClaimType.NULLABLE_ONE -> CodeBlock.of("null as %T", claim.type.toTypeName().copy(true))
                DependencyClaim.DependencyClaimType.NULLABLE_VALUE_OF -> CodeBlock.of("null as %T", CommonClassNames.valueOf.parameterizedBy(claim.type.toTypeName()).copy(true))
                DependencyClaim.DependencyClaimType.NULLABLE_PROMISE_OF -> CodeBlock.of("null as %T", CommonClassNames.promiseOf.parameterizedBy(claim.type.toTypeName()).copy(true))
                else -> throw IllegalArgumentException(claim.claimType.toString())
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
                    CodeBlock.of("it.promiseOf(%N.%N).map { it.value() }", component.holderName, component.fieldName)
                } else {
                    CodeBlock.of("it.promiseOf(%N.%N)", component.holderName, component.fieldName)
                }
            }
        }

        is TargetDependency -> {
            CodeBlock.of("it.get(%N.%N)", component.holderName, component.fieldName)
        }

        is ValueOfDependency -> {
            if (delegate is NullDependency) {
                CodeBlock.of("%T.valueOfNull()", CommonClassNames.valueOf)
            } else {
                val component = delegate.component!!
                if (delegate is WrappedTargetDependency) {
                    CodeBlock.of("it.valueOf(%N.%N).map { it.value() }", component.holderName, component.fieldName)
                } else {
                    CodeBlock.of("it.valueOf(%N.%N)", component.holderName, component.fieldName)
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
                else -> throw IllegalStateException("Unknown claim type: " + claim.claimType)
            }
            for ((i, dependency) in dependencies.withIndex()) {
                if (i > 0) b.add(", ")
                val dependencyNode = dependency.component!!.nodeRef("_")
                when (dependency) {
                    is WrappedTargetDependency -> b.add("%T.unwrap(%L)", CommonClassNames.nodeWithMapper, dependencyNode)
                    is PromiseOfDependency -> if (dependency.delegate is WrappedTargetDependency) {
                        b.add("%T.unwrap(%L)", CommonClassNames.nodeWithMapper, dependencyNode)
                    } else {
                        b.add("%T.node(%L)", CommonClassNames.nodeWithMapper, dependencyNode)
                    }

                    is ValueOfDependency -> if (dependency.delegate is WrappedTargetDependency) {
                        b.add("%T.unwrap(%L)", CommonClassNames.nodeWithMapper, dependencyNode)
                    } else {
                        b.add("%T.node(%L)", CommonClassNames.nodeWithMapper, dependencyNode)
                    }

                    else -> b.add("%T.node(%L)", CommonClassNames.nodeWithMapper, dependencyNode)

                }
            }
            b.add(")").build()
        }
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
