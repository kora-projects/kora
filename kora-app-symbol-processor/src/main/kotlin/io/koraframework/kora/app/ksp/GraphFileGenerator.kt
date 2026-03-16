package io.koraframework.kora.app.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.kora.app.ksp.component.ComponentDependency
import io.koraframework.kora.app.ksp.component.DependencyClaim
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

        var currentClass: TypeSpec.Builder? = null
        var currentConstructor: FunSpec.Builder? = null
        var holders = 0

        for ((i, component) in components.withIndex()) {
            val componentNumber = i % KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS
            if (componentNumber == 0) {
                if (currentClass != null) {
                    currentClass.primaryConstructor(currentConstructor!!.build())
                    classBuilder.addType(currentClass.build())
                    val prevNumber = i / KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS - 1
                    companion.addProperty("holder$prevNumber", graphTypeName.nestedClass("ComponentHolder$prevNumber"))
                }
                holders++
                val className = graphTypeName.nestedClass("ComponentHolder" + i / KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS)
                currentClass = TypeSpec.classBuilder(className)
                    .generated(KoraAppProcessor::class)
                currentConstructor = FunSpec.constructorBuilder()
                    .addParameter("graphDraw", CommonClassNames.applicationGraphDraw)
                    .addParameter("impl", implClass)
                    .addStatement("val self = %T", graphTypeName)
                    .addStatement("val map = %T<%T, %T>()", HashMap::class.asClassName(), String::class.asClassName(), Type::class.asClassName())
                    .controlFlow("for (field in %T::class.java.declaredFields)", className) {
                        controlFlow("if (!field.name.startsWith(%S))", "component") { addStatement("continue") }
                        addStatement("map[field.name] = (field.genericType as %T).actualTypeArguments[0]", ParameterizedType::class.asClassName())
                    }
                for (j in 0 until i / KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS) {
                    currentConstructor.addParameter("ComponentHolder$j", graphTypeName.nestedClass("ComponentHolder$j"))
                }
            }

            val aopProxySuperClass = ServiceTypesHelper.findAopProxySuperClass(component.type)
            val propertyType = aopProxySuperClass?.toTypeName() ?: component.type.toTypeName()

            currentClass!!.addProperty(component.fieldName, CommonClassNames.node.parameterizedBy(propertyType))
            val statement = this.generateComponentStatement(component)
            currentConstructor!!.addCode(statement).addCode("\n")
        }
        if (components.size > 0) {
            var lastComponentNumber = components.size / KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS
            if (components.size % KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS == 0) {
                lastComponentNumber--
            }
            currentClass!!.addFunction(currentConstructor!!.build())
            classBuilder.addType(currentClass.build())
            companion.addProperty("holder$lastComponentNumber", graphTypeName.nestedClass("ComponentHolder$lastComponentNumber"))
        }


        val initBlock = CodeBlock.builder()
            .addStatement("val self = %T", graphTypeName)
            .addStatement("val impl = %T()", implClass)
            .addStatement("graphDraw =  %T(%T::class.java)", CommonClassNames.applicationGraphDraw, declaration.toClassName())
        for (i in 0 until holders) {
            initBlock.add("%N = %T(graphDraw, impl", "holder$i", graphTypeName.nestedClass("ComponentHolder$i"))
            for (j in 0 until i) {
                initBlock.add(", holder$j")
            }
            initBlock.add(")\n")
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

    private fun generateComponentStatement(component: ResolvedComponent): CodeBlock {
        val statement = CodeBlock.builder()
        val declaration = component.declaration
        val componentHolder = component.holderName
        val componentField = component.fieldName

        statement.add("%N = graphDraw.addNode(map[%S], ", componentField, component.fieldName)
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


        val createDependencies = getCreateDependencies(componentHolder, component)
        statement.add("%L,\n", createDependencies)

        val refreshDependencies = getRefreshDependencies(componentHolder, component)
        statement.add("%L,\n", refreshDependencies)

        statement.add("listOf(")
        for ((i, interceptor) in interceptors.interceptorsFor(declaration).withIndex()) {
            if (i > 0) {
                statement.add(", ")
            }
            statement.add("%L", interceptor.component.nodeRef(componentHolder))
        }
        statement.add("),\n")


        statement.add("{ ")
        val dependenciesCode = this.getDependenciesCode(component)

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
                if (declaration.module is ModuleDeclaration.AnnotatedModule) {
                    statement.add("impl.module%L.", allModules.indexOf(declaration.module.element))
                } else {
                    statement.add("impl.")
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
                statement.add("(%L)", dependenciesCode)
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

    private fun getCreateDependencies(componentHolder: String, component: ResolvedComponent): CodeBlock {
        val result = mutableListOf<ResolvedComponent>()
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
                            is ComponentDependency.NullDependency -> throw IllegalStateException()
                        }
                    }

                }
            }
        }
        val b = CodeBlock.builder()
        b.add("%M(", MemberName("kotlin.collections", "listOf"))
        for (i in result.indices) {
            if (i > 0) {
                b.add(", ")
            }
            b.add("%L", result[i].nodeRef(componentHolder))
        }
        b.add(")")
        return b.build()
    }


    private fun getRefreshDependencies(
        componentHolder: String,
        component: ResolvedComponent
    ): CodeBlock {
        val result = mutableListOf<ResolvedComponent>()
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
                            else -> throw IllegalStateException()
                        }
                    }
                }

            }
        }
        val b = CodeBlock.builder()
        b.add("%M(", MemberName("kotlin.collections", "listOf"))
        for (i in result.indices) {
            if (i > 0) {
                b.add(", ")
            }
            b.add("%L", result[i].nodeRef(componentHolder))
        }
        b.add(")")
        return b.build()
    }


    private fun getDependenciesCode(component: ResolvedComponent): CodeBlock {
        if (component.dependencies.isEmpty()) {
            return CodeBlock.of("")
        }
        val block = CodeBlock.builder().indent().add("\n")
        for ((i, dependency) in component.dependencies.withIndex()) {
            if (i > 0) {
                block.add(",\n")
            }
            block.add(dependency.write(ctx))
        }
        block.unindent().add("\n")
        return block.build()
    }

}

