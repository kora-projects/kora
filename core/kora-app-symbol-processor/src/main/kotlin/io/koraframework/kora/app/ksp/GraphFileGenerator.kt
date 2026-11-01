package io.koraframework.kora.app.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.kora.app.ksp.component.ComponentDependency
import io.koraframework.kora.app.ksp.component.DependencyClaim
import io.koraframework.kora.app.ksp.component.GraphHelperFunctions
import io.koraframework.kora.app.ksp.component.ResolvedComponent
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration
import io.koraframework.kora.app.ksp.declaration.ModuleDeclaration
import io.koraframework.kora.app.ksp.interceptor.ComponentInterceptors
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.KspCommonUtils.generated
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import java.util.function.Supplier


// upper bound estimations of the holder constructor bytecode
private const val NODE_CODE_SIZE = 64
private const val NODE_REFERENCE_CODE_SIZE = 16

class GraphFileGenerator(
    val ctx: ProcessingContext,
    val declaration: KSClassDeclaration,
    val allModules: List<KSClassDeclaration>,
    val interceptors: ComponentInterceptors,
    val components: List<ResolvedComponent>,
    val conditions: Map<ClassName, ResolvedComponent>
) {

    fun generate(): FileSpec {
        val packageName = declaration.packageName.asString()
        val graphName = "${declaration.simpleName.asString()}Graph"
        val graphTypeName = ClassName(packageName, graphName)

        val fileSpec = FileSpec.builder(
            packageName = packageName,
            fileName = graphName
        )

        val implClass = ClassName(packageName, "\$${declaration.simpleName.asString()}Impl")
        val supplierSuperInterface = Supplier::class.asClassName().parameterizedBy(CommonClassNames.applicationGraphDraw)
        val classBuilder = TypeSpec.classBuilder(graphName)
            .generated(KoraAppProcessor::class)
            .addSuperinterface(supplierSuperInterface)
            .addFunction(
                FunSpec.builder("get")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(CommonClassNames.applicationGraphDraw)
                    .addStatement("return graphDraw")
                    .build()
            )

        val companion = TypeSpec.companionObjectBuilder()
            .generated(KoraAppProcessor::class)
            .addProperty("graphDraw", CommonClassNames.applicationGraphDraw)

        val holders = this.assignHolders()
        var currentClass: TypeSpec.Builder? = null
        var currentHolder = -1
        var currentConstructor: FunSpec.Builder? = null
        var currentHelperFunctions = GraphHelperFunctions()
        var typeOfFieldUsed = false

        fun closeHolder() {
            val holderClass = currentClass ?: return
            holderClass.addFunction(currentConstructor!!.build())
            holderClass.addFunctions(currentHelperFunctions.functions())
            if (typeOfFieldUsed) {
                holderClass.addFunction(
                    FunSpec.builder("typeOfField")
                        .addModifiers(KModifier.PRIVATE)
                        .addParameter("field", String::class)
                        .returns(Type::class)
                        .addStatement("return (javaClass.getDeclaredField(field).genericType as %T).actualTypeArguments[0]", ParameterizedType::class.asClassName())
                        .build()
                )
            }
            classBuilder.addType(holderClass.build())
        }

        for (component in components) {
            if (component.holderNumber != currentHolder) {
                closeHolder()
                currentHolder = component.holderNumber
                currentHelperFunctions = GraphHelperFunctions()
                typeOfFieldUsed = false
                val className = graphTypeName.nestedClass("ComponentHolder$currentHolder")
                companion.addProperty(component.holderName, className)
                currentClass = TypeSpec.classBuilder(className)
                    .generated(KoraAppProcessor::class)
                currentConstructor = FunSpec.constructorBuilder()
                    .addParameter("graphDraw", CommonClassNames.applicationGraphDraw)
                    .addParameter("impl", implClass)
                    .addStatement("val self = %T", graphTypeName)
            }

            val propertyType = component.type.toTypeName()

            currentClass!!.addProperty(component.fieldName, CommonClassNames.node.parameterizedBy(propertyType))
            val nodeType = if (propertyType is ClassName && component.type.arguments.isEmpty() && component.type.declaration is KSClassDeclaration) {
                // javaObjectType because type of the node of kotlin.Int is java.lang.Integer
                CodeBlock.of("%T::class.javaObjectType", propertyType.copy(nullable = false))
            } else {
                // generic type has to be exactly the same as reflection returns, so it is read from the field declared above
                typeOfFieldUsed = true
                CodeBlock.of("typeOfField(%S)", component.fieldName)
            }
            val statement = this.generateComponentStatement(packageName, component, nodeType, currentHelperFunctions)
            currentConstructor!!.addCode(statement).addCode("\n")
        }
        closeHolder()


        val initBlock = CodeBlock.builder()
            .addStatement("val self = %T", graphTypeName)
            .addStatement("val impl = %T()", implClass)
            .addStatement("graphDraw =  %T(%T::class.java)", CommonClassNames.applicationGraphDraw, declaration.toClassName())
        for (i in 0 until holders) {
            // previous holders are accessed by holder constructors through companion properties that are already assigned at this point
            initBlock.addStatement("%N = %T(graphDraw, impl)", "holder$i", graphTypeName.nestedClass("ComponentHolder$i"))
        }

        val supplierMethodBuilder = FunSpec.builder("graph")
            .returns(CommonClassNames.applicationGraphDraw)
            .addCode("\nreturn graphDraw\n", declaration.simpleName.asString() + "Graph")
        return fileSpec.addType(
            classBuilder
                .addType(companion.addInitializerBlock(initBlock.build()).addFunction(supplierMethodBuilder.build()).build())
                .addFunction(supplierMethodBuilder.build())
                .build()
        ).build()
    }


    /**
     * @return number of holders
     */
    private fun assignHolders(): Int {
        var holder = 0
        var holderComponents = 0
        var holderCodeSize = 0
        for (component in components) {
            val references = inlineReferences(this.createDependencies(component)) + inlineReferences(this.refreshDependencies(component)) + this.interceptors.interceptorsFor(component.declaration).size
            val codeSize = NODE_CODE_SIZE + references * NODE_REFERENCE_CODE_SIZE
            if (holderComponents > 0 && (holderComponents >= KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS || holderCodeSize + codeSize > KoraAppProcessor.HOLDER_CONSTRUCTOR_CODE_BUDGET)) {
                holder++
                holderComponents = 0
                holderCodeSize = 0
            }
            component.setHolder(holder)
            holderComponents++
            holderCodeSize += codeSize
        }
        return if (components.isEmpty()) 0 else holder + 1
    }

    private fun inlineReferences(nodes: Set<ResolvedComponent>): Int {
        // wide list is built by helper functions, constructor only has a function call
        return if (nodes.size >= GraphHelperFunctions.WIDE_LIST_SIZE) 1 else nodes.size
    }

    private fun parentCondition(component: ResolvedComponent): CodeBlock {
        if (component.getParentConditions().size == 1) {
            val conditionComponent = conditions[component.getParentConditions().first()]!!
            return CodeBlock.of("it.condition(%L)", conditionComponent.nodeRef("_"))
        } else {
            val b = CodeBlock.builder()
            b.add("%T.or(", CommonClassNames.graphCondition)
            for ((i, condition) in component.getParentConditions().withIndex()) {
                if (i > 0) {
                    b.add(", ")
                }
                val conditionComponent = conditions[condition]!!
                b.add("it.condition(%L)", conditionComponent.nodeRef("_"))
            }
            b.add(")")
            return b.build()
        }
    }

    private fun generateComponentStatement(graphPackageName: String, component: ResolvedComponent, nodeType: CodeBlock, helperFunctions: GraphHelperFunctions): CodeBlock {
        val statement = CodeBlock.builder()
        val declaration = component.declaration
        val componentHolder = component.holderName
        val componentField = component.fieldName

        statement.add("%N = graphDraw.addNode(%L, ", componentField, nodeType)
        statement.indent().add("\n")
        if (component.tag == null) {
            statement.add("null,\n")
        } else {
            statement.add("%L::class.java,\n", component.tag)
        }

        if (component.getParentConditions().isEmpty() && component.declaration.condition == null) {
            statement.add("null,\n");
        } else if (component.getParentConditions().isEmpty()) {
            val conditionComponent = conditions[component.declaration.condition]!!
            statement.add("{ it.condition(%L).eval() },\n", conditionComponent.nodeRef("_"));
        } else if (component.declaration.condition == null) {
            statement.add("{ %L.eval() },\n", parentCondition(component));
        } else {
            val conditionComponent = conditions[component.declaration.condition]!!
            statement.add("{ %T.and(%L, it.condition(%L)).eval() },\n", CommonClassNames.graphCondition, parentCondition(component), conditionComponent.nodeRef("_"));
        }


        val createDependencies = this.createDependencies(component)
        val refreshDependencies = this.refreshDependencies(component)
        val createDependenciesCode = nodeList(componentHolder, createDependencies, helperFunctions)
        statement.add("%L,\n", createDependenciesCode)
        statement.add("%L,\n", if (createDependencies.toList() == refreshDependencies.toList()) createDependenciesCode else nodeList(componentHolder, refreshDependencies, helperFunctions))

        statement.add("listOf(")
        for ((i, interceptor) in interceptors.interceptorsFor(declaration).withIndex()) {
            if (i > 0) {
                statement.add(", ")
            }
            statement.add("%L", interceptor.component.nodeRef(componentHolder))
        }
        statement.add("),\n")


        statement.add("{ ")
        val hasModuleInstance = declaration is ComponentDeclaration.FromModuleComponent && (declaration.module is ModuleDeclaration.FactoryModule || declaration.module is ModuleDeclaration.ClassModule)
        val dependenciesCode = this.getDependenciesCode(graphPackageName, component, helperFunctions, if (hasModuleInstance) 1 else 0)

        when (declaration) {
            is ComponentDeclaration.AnnotatedComponent -> {
                statement.add("%T", declaration.classDeclaration.toClassName())
                if (declaration.typeVariables.isNotEmpty()) {
                    statement.add("<")
                    for ((i, tv) in declaration.typeVariables.withIndex()) {
                        if (i > 0) {
                            statement.add(", ")
                        }
                        statement.add("%L", tv.type!!.toTypeName())
                    }
                    statement.add(">")
                }
                statement.add("(%L)", dependenciesCode)
            }

            is ComponentDeclaration.FromModuleComponent -> {
                val methodDependenciesCode: CodeBlock
                when (val module = declaration.module) {
                    is ModuleDeclaration.AnnotatedModule -> {
                        methodDependenciesCode = dependenciesCode
                        statement.add("impl.module%L.", allModules.indexOf(module.element))
                    }

                    is ModuleDeclaration.FactoryModule -> {
                        methodDependenciesCode = dependenciesCode
                        statement.add("%L.", component.dependencies.first().write(ctx, graphPackageName, helperFunctions))
                    }

                    is ModuleDeclaration.ClassModule -> {
                        methodDependenciesCode = dependenciesCode
                        statement.add("%L.", component.dependencies.first().write(ctx, graphPackageName, helperFunctions))
                    }

                    else -> {
                        methodDependenciesCode = dependenciesCode
                        statement.add("impl.")
                    }
                }
                statement.add("%N", declaration.method.simpleName.asString())
                if (declaration.typeVariables.isNotEmpty()) {
                    statement.add("<")
                    for ((i, tv) in declaration.typeVariables.withIndex()) {
                        if (i > 0) {
                            statement.add(", ")
                        }
                        statement.add("%L", tv.type!!.toTypeName())
                    }
                    statement.add(">")
                }
                statement.add("(%L)", methodDependenciesCode)
            }

            is ComponentDeclaration.FromExtensionComponent -> {
                statement.add(declaration.generator(dependenciesCode))
            }

            is ComponentDeclaration.PromisedProxyComponent -> {
                statement.add("%T(%L)", declaration.className, dependenciesCode)
            }

            is ComponentDeclaration.OptionalComponent -> {
                statement.add("%T.ofNullable(%L)", Optional::class.asClassName(), dependenciesCode)
            }
        }
        statement.add(" }\n")
        return statement.unindent().add(")\n").build()
    }

    private fun createDependencies(component: ResolvedComponent): Set<ResolvedComponent> {
        // the same node can be used for several parameters, but graph has to track it as a dependency only once
        val result = LinkedHashSet<ResolvedComponent>()
        if (component.declaration.condition != null) {
            val condition = this.conditions[component.declaration.condition]!!
            result.add(condition);
        }
        for (parentConditionTag in component.getParentConditions()) {
            val condition = this.conditions[parentConditionTag]!!
            result.add(condition);
        }

        for (dependency in component.dependencies) {
            when (dependency) {
                is ComponentDependency.NullDependency, is ComponentDependency.PromisedProxyParameterDependency -> {}
                is ComponentDependency.TypeOfDependency -> {}
                is ComponentDependency.SingleDependency -> {
                    when (dependency) {
                        is ComponentDependency.PromiseOfDependency -> {}
                        is ComponentDependency.TargetDependency -> result.add(dependency.component)
                        is ComponentDependency.ValueOfDependency -> result.add(dependency.component)

                        is ComponentDependency.WrappedTargetDependency -> result.add(dependency.component)
                    }
                }

                is ComponentDependency.AllOfDependency -> {
                    if (dependency.claim.claimType !== DependencyClaim.DependencyClaimType.ALL_OF_PROMISE) {
                        for (resolvedComponent in dependency.resolvedDependencies) {
                            resolvedComponent.component?.let { result.add(it) }
                        }
                    }
                }

                is ComponentDependency.OneOfDependency -> {
                    for (singleDependency in dependency.dependencies) {
                        when (singleDependency) {
                            is ComponentDependency.PromiseOfDependency -> {}
                            is ComponentDependency.TargetDependency -> result.add(singleDependency.component)
                            is ComponentDependency.ValueOfDependency -> result.add(singleDependency.component)
                            is ComponentDependency.WrappedTargetDependency -> result.add(singleDependency.component)
                            is ComponentDependency.NullDependency -> throw IllegalStateException("Kora internal error: OneOf<T> dependency unexpectedly contains a NullDependency while collecting initialization dependencies: ${dependency.claim}")
                        }
                    }

                }

                is ComponentDependency.GraphDependency -> {}
            }
        }
        return result
    }


    private fun refreshDependencies(component: ResolvedComponent): Set<ResolvedComponent> {
        // the same node can be used for several parameters, but graph has to track it as a dependency only once
        val result = LinkedHashSet<ResolvedComponent>()
        if (component.declaration.condition != null) {
            val condition = this.conditions[component.declaration.condition]!!
            result.add(condition);
        }
        for (parentConditionTag in component.getParentConditions()) {
            val condition = this.conditions[parentConditionTag]!!
            result.add(condition);
        }

        for (dependency in component.dependencies) {
            when (dependency) {
                is ComponentDependency.NullDependency, is ComponentDependency.PromisedProxyParameterDependency, is ComponentDependency.TypeOfDependency -> {}
                is ComponentDependency.SingleDependency -> {
                    when (dependency) {
                        is ComponentDependency.TargetDependency -> result.add(dependency.component)
                        is ComponentDependency.WrappedTargetDependency -> result.add(dependency.component)
                        is ComponentDependency.PromiseOfDependency, is ComponentDependency.ValueOfDependency -> {}
                    }
                }

                is ComponentDependency.AllOfDependency -> {
                    if (dependency.claim.claimType === DependencyClaim.DependencyClaimType.ALL) {
                        for (dependencyComponent in dependency.resolvedDependencies) {
                            dependencyComponent.component?.let { result.add(it) }
                        }
                    }
                }

                is ComponentDependency.OneOfDependency -> {
                    for (singleDependency in dependency.dependencies) {
                        when (singleDependency) {
                            is ComponentDependency.PromiseOfDependency -> {}
                            is ComponentDependency.TargetDependency -> result.add(singleDependency.component)
                            is ComponentDependency.ValueOfDependency -> result.add(singleDependency.component)
                            is ComponentDependency.WrappedTargetDependency -> result.add(singleDependency.component)
                            else -> throw IllegalStateException("Kora internal error: OneOf<T> dependency unexpectedly contains unsupported dependency while collecting refresh dependencies: $singleDependency")
                        }
                    }
                }
                is ComponentDependency.GraphDependency -> {}
            }
        }
        return result
    }


    private fun nodeList(componentHolder: String, nodes: Set<ResolvedComponent>, helperFunctions: GraphHelperFunctions): CodeBlock {
        if (nodes.size >= GraphHelperFunctions.WIDE_LIST_SIZE) {
            return CodeBlock.of("%N()", nodeListFunction(componentHolder, nodes, helperFunctions))
        }
        val b = CodeBlock.builder()
        b.add("%M(", MemberName("kotlin.collections", "listOf"))
        for ((i, node) in nodes.withIndex()) {
            if (i > 0) {
                b.add(", ")
            }
            b.add("%L", node.nodeRef(componentHolder))
        }
        b.add(")")
        return b.build()
    }

    /**
     * Thousands of nodes listed in the holder constructor do not fit into the method size limit, so the list is built by a few functions of the holder
     *
     * @return name of the function that returns list of nodes
     */
    private fun nodeListFunction(componentHolder: String, nodes: Set<ResolvedComponent>, helperFunctions: GraphHelperFunctions): String {
        val name = helperFunctions.nextName("nodes")
        val nodeType = CommonClassNames.node.parameterizedBy(STAR)
        val function = FunSpec.builder(name)
            .addModifiers(KModifier.PRIVATE)
            .returns(LIST.parameterizedBy(nodeType))
            .addStatement("val nodes = %T<%T>(%L)", ArrayList::class.asClassName(), nodeType, nodes.size)
        for ((chunk, chunkNodes) in nodes.chunked(GraphHelperFunctions.WIDE_LIST_SIZE).withIndex()) {
            val chunkName = name + "_" + chunk
            val chunkFunction = FunSpec.builder(chunkName)
                .addModifiers(KModifier.PRIVATE)
                .addParameter("nodes", MUTABLE_LIST.parameterizedBy(nodeType))
            for (node in chunkNodes) {
                // nodes of this holder are read from its properties: companion property of the holder is not assigned yet when constructor is running
                chunkFunction.addStatement("nodes.add(%L)", node.nodeRef(componentHolder))
            }
            helperFunctions.add(chunkFunction.build())
            function.addStatement("%N(nodes)", chunkName)
        }
        helperFunctions.add(function.addStatement("return nodes").build())
        return name
    }

    private fun getDependenciesCode(graphPackageName: String, component: ResolvedComponent, helperFunctions: GraphHelperFunctions, startIndex: Int): CodeBlock {
        val deps = component.dependencies.drop(startIndex)
        if (deps.isEmpty()) {
            return CodeBlock.of("")
        }
        val block = CodeBlock.builder().indent().add("\n")
        for ((i, dependency) in deps.withIndex()) {
            if (i > 0) {
                block.add(",\n")
            }
            block.add(dependency.write(ctx, graphPackageName, helperFunctions))
        }
        block.unindent().add("\n")
        return block.build()
    }

}
