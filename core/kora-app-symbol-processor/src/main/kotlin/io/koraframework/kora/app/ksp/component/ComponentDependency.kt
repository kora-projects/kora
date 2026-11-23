package io.koraframework.kora.app.ksp.component

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MUTABLE_LIST
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import io.koraframework.kora.app.ksp.ProcessingContext
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration
import io.koraframework.ksp.common.CommonClassNames


sealed interface ComponentDependency {
    val claim: DependencyClaim

    fun write(ctx: ProcessingContext, graphPackageName: String, helperFunctions: GraphHelperFunctions): CodeBlock = when (this) {
        is AllOfDependency -> {
            val method = when (claim.claimType) {
                DependencyClaim.DependencyClaimType.ALL -> "all"
                DependencyClaim.DependencyClaimType.ALL_OF_VALUE -> "allValues"
                DependencyClaim.DependencyClaimType.ALL_OF_PROMISE -> "allPromises"
                else -> throw IllegalStateException("Kora internal error: unsupported All<T> claim type for code generation: $claim")
            }
            // compiler inference time grows dramatically with the number of generic arguments when all of them are bound
            // to the same inferred type variable, so the type we already know is written explicitly
            val explicitType = explicitTypeArgument(claim.type, graphPackageName)
            if (explicitType != null && resolvedDependencies.size >= GraphHelperFunctions.WIDE_LIST_SIZE) {
                val nodes = allOfNodesFunction(resolvedDependencies, explicitType, graphPackageName, helperFunctions)
                CodeBlock.of("%T.%L<%T>(it, *%N())", CommonClassNames.all, method, explicitType, nodes)
            } else {
                val codeBlock = CodeBlock.builder()
                if (explicitType == null) {
                    // type can't be written in the graph class, so it is left to inference
                    codeBlock.add("%T.%L(it", CommonClassNames.all, method)
                } else {
                    codeBlock.add("%T.%L<%T>(it", CommonClassNames.all, method, explicitType)
                }
                for (dependency in resolvedDependencies) {
                    codeBlock.add(", %L", nodeWithMapper(dependency, explicitType, graphPackageName))
                }
                codeBlock.add(")").build()
            }
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
                    CodeBlock.of("it.promiseOf(%N.%N).map { it.value() }", component.holderName, component.fieldName)
                } else {
                    CodeBlock.of("it.promiseOf(%N.%N)", component.holderName, component.fieldName)
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
                else -> throw IllegalStateException("Kora internal error: unsupported one-of dependency claim type for code generation: $claim")
            }
            val explicitType = explicitTypeArgument(claim.type, graphPackageName)
            for ((i, dependency) in dependencies.withIndex()) {
                if (i > 0) b.add(", ")
                b.add(nodeWithMapper(dependency, explicitType, graphPackageName))
            }
            b.add(")").build()
        }

        is GraphDependency -> CodeBlock.of("it")
    }

    companion object {
        private fun nodeWithMapper(dependency: SingleDependency, explicitType: TypeName?, graphPackageName: String): CodeBlock {
            val dependencyNode = dependency.component!!.nodeRef("some_fake_holder_idc")
            val isWrapped = dependency is WrappedTargetDependency
                || dependency is ValueOfDependency && dependency.delegate is WrappedTargetDependency
                || dependency is PromiseOfDependency && dependency.delegate is WrappedTargetDependency
            if (isWrapped) {
                val wrapperType = dependency.component!!.type
                if (explicitType != null && isDenotable(wrapperType, graphPackageName)) {
                    // unlike javac, kotlin compiler solves all the arguments of the call in one constraint system, so unwrap has to be explicit too
                    return CodeBlock.of("%T.unwrap<%T, %T>(%L)", CommonClassNames.nodeWithMapper, explicitType, wrapperType.toTypeName(), dependencyNode)
                }
                return CodeBlock.of("%T.unwrap(%L)", CommonClassNames.nodeWithMapper, dependencyNode)
            }
            if (explicitType == null) {
                return CodeBlock.of("%T.node(%L)", CommonClassNames.nodeWithMapper, dependencyNode)
            }
            return CodeBlock.of("%T.node<%T>(%L)", CommonClassNames.nodeWithMapper, explicitType, dependencyNode)
        }

        /**
         * Factory with thousands of All&lt;T&gt; items does not fit into the method size limit, so array of items is built by a few functions
         *
         * @return name of the function that returns array of All&lt;T&gt; items
         */
        private fun allOfNodesFunction(dependencies: List<SingleDependency>, explicitType: TypeName, graphPackageName: String, helperFunctions: GraphHelperFunctions): String {
            val name = helperFunctions.nextName("all")
            val nodeType = CommonClassNames.nodeWithMapper.parameterizedBy(STAR, explicitType)
            val function = FunSpec.builder(name)
                .addModifiers(KModifier.PRIVATE)
                .returns(ARRAY.parameterizedBy(nodeType))
                .addStatement("val nodes = %T<%T>(%L)", ArrayList::class.asClassName(), nodeType, dependencies.size)
            for ((chunk, chunkDependencies) in dependencies.chunked(GraphHelperFunctions.WIDE_LIST_SIZE).withIndex()) {
                val chunkName = name + "_" + chunk
                val chunkFunction = FunSpec.builder(chunkName)
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("nodes", MUTABLE_LIST.parameterizedBy(nodeType))
                for (dependency in chunkDependencies) {
                    chunkFunction.addStatement("nodes.add(%L)", nodeWithMapper(dependency, explicitType, graphPackageName))
                }
                helperFunctions.add(chunkFunction.build())
                function.addStatement("%N(nodes)", chunkName)
            }
            helperFunctions.add(function.addStatement("return nodes.toTypedArray()").build())
            return name
        }

        /**
         * @return type if it can be written as an explicit type argument in the generated graph class or null if it should be left to inference
         */
        private fun explicitTypeArgument(type: KSType, graphPackageName: String): TypeName? {
            return if (isDenotable(type, graphPackageName)) type.toTypeName() else null
        }

        private fun isDenotable(type: KSType, fromPackage: String): Boolean {
            if (type.isError) {
                return false
            }
            var declaration: KSDeclaration? = type.declaration
            while (declaration != null) {
                if (declaration !is KSClassDeclaration) {
                    // type alias, type parameter or a local class
                    return false
                }
                when (declaration.getVisibility()) {
                    Visibility.PUBLIC -> {}
                    // internal declaration of another module is not accessible
                    Visibility.INTERNAL -> if (declaration.containingFile == null) return false
                    Visibility.JAVA_PACKAGE -> if (declaration.packageName.asString() != fromPackage) return false
                    else -> return false
                }
                declaration = declaration.parentDeclaration
            }
            for (argument in type.arguments) {
                if (argument.variance == Variance.STAR) {
                    continue
                }
                val argumentType = argument.type?.resolve() ?: return false
                if (!isDenotable(argumentType, fromPackage)) {
                    return false
                }
            }
            return true
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
