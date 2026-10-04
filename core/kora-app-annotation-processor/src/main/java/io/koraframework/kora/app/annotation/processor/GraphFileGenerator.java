package io.koraframework.kora.app.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.kora.app.annotation.processor.component.ComponentDependency;
import io.koraframework.kora.app.annotation.processor.component.DependencyClaim;
import io.koraframework.kora.app.annotation.processor.component.GraphHelperMethods;
import io.koraframework.kora.app.annotation.processor.component.ResolvedComponent;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;
import io.koraframework.kora.app.annotation.processor.declaration.ModuleDeclaration;
import io.koraframework.kora.app.annotation.processor.interceptor.ComponentInterceptors;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Supplier;

public class GraphFileGenerator {
    private final ProcessingContext ctx;
    private final Element classElement;
    private final List<TypeElement> allModules;
    private final ComponentInterceptors interceptors;
    private final List<ResolvedComponent> components;
    private final Map<ClassName, ResolvedComponent> conditions;
    private boolean typeOfFieldUsed = false;

    // upper bound estimations of the holder constructor bytecode
    private static final int NODE_CODE_SIZE = 48;
    private static final int NODE_REFERENCE_CODE_SIZE = 12;

    public GraphFileGenerator(ProcessingContext ctx, Element classElement, List<TypeElement> allModules, ComponentInterceptors interceptors, List<ResolvedComponent> components, Map<ClassName, ResolvedComponent> conditions) {
        this.ctx = ctx;
        this.classElement = classElement;
        this.allModules = allModules;
        this.interceptors = interceptors;
        this.components = components;
        this.conditions = conditions;
    }

    public JavaFile generate() {
        var packageElement = (PackageElement) classElement.getEnclosingElement();
        var implClass = ClassName.get(packageElement.getQualifiedName().toString(), "$" + classElement.getSimpleName().toString() + "Impl");
        var graphName = classElement.getSimpleName().toString() + "Graph";
        var graphTypeName = ClassName.get(packageElement.getQualifiedName().toString(), graphName);
        var classBuilder = TypeSpec.classBuilder(graphName)
            .addAnnotation(AnnotationUtils.generated(KoraAppProcessor.class))
            .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "{$S, $S}", "unchecked", "rawtypes").build())
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Supplier.class), CommonClassNames.applicationGraphDraw))
            .addField(CommonClassNames.applicationGraphDraw, "graphDraw", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .addMethod(MethodSpec
                .methodBuilder("get")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(CommonClassNames.applicationGraphDraw)
                .addStatement("return graphDraw")
                .build());
        var holders = this.assignHolders();
        var currentClass = (TypeSpec.Builder) null;
        var currentClassName = (ClassName) null;
        var currentConstructor = (MethodSpec.Builder) null;
        var currentHelperMethods = new GraphHelperMethods();
        for (var component : components) {
            if (currentClassName == null || !currentClassName.simpleName().equals("ComponentHolder" + component.holderNumber())) {
                if (currentClass != null) {
                    currentClass.addMethod(currentConstructor.build());
                    currentClass.addMethods(currentHelperMethods.methods());
                    classBuilder.addType(currentClass.build());
                    currentHelperMethods = new GraphHelperMethods();
                }
                currentClassName = graphTypeName.nestedClass("ComponentHolder" + component.holderNumber());
                classBuilder.addField(currentClassName, component.holderName(), Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
                currentClass = TypeSpec.classBuilder(currentClassName)
                    .addAnnotation(AnnotationUtils.generated(KoraAppProcessor.class))
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
                currentConstructor = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(CommonClassNames.applicationGraphDraw, "graphDraw")
                    .addParameter(implClass, "impl");
            }
            TypeName componentTypeName = TypeName.get(component.type()).box();
            var typeMirrorElement = ctx.types.asElement(component.type());
            if (typeMirrorElement instanceof TypeElement te) {
                var annotation = AnnotationUtils.findAnnotation(typeMirrorElement, CommonClassNames.aopProxy);
                if (annotation != null) {
                    var superElement = ctx.types.asElement(te.getSuperclass());
                    var aopProxyName = NameUtils.generatedType(superElement, "_AopProxy");
                    if (typeMirrorElement.getSimpleName().contentEquals(aopProxyName)) {
                        componentTypeName = TypeName.get(te.getSuperclass()).box();
                    }
                }
            }

            currentClass.addField(FieldSpec.builder(ParameterizedTypeName.get(CommonClassNames.node, componentTypeName), component.fieldName(), Modifier.PRIVATE, Modifier.FINAL).build());
            final CodeBlock nodeType;
            if (isClassLiteral(componentTypeName)) {
                nodeType = CodeBlock.of("$T.class", componentTypeName);
            } else {
                // generic type has to be exactly the same as reflection returns, so it is read from the field declared above
                this.typeOfFieldUsed = true;
                nodeType = CodeBlock.of("$T.typeOfField($T.class, $S)", graphTypeName, currentClassName, component.fieldName());
            }
            var statement = this.generateComponentStatement(graphTypeName, component, nodeType, currentHelperMethods);
            currentConstructor.addStatement(statement);
        }
        if (currentClass != null) {
            currentClass.addMethod(currentConstructor.build());
            currentClass.addMethods(currentHelperMethods.methods());
            classBuilder.addType(currentClass.build());
        }
        if (this.typeOfFieldUsed) {
            classBuilder.addMethod(MethodSpec.methodBuilder("typeOfField")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(Type.class)
                .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class)), "holder")
                .addParameter(String.class, "field")
                .beginControlFlow("try")
                .addStatement("return (($T) holder.getDeclaredField(field).getGenericType()).getActualTypeArguments()[0]", ParameterizedType.class)
                .nextControlFlow("catch ($T e)", NoSuchFieldException.class)
                .addStatement("throw new $T(e)", IllegalStateException.class)
                .endControlFlow()
                .build());
        }


        var staticBlock = CodeBlock.builder();

        staticBlock
            .addStatement("var impl = new $T()", implClass)
            .addStatement("graphDraw = new $T($T.class)", CommonClassNames.applicationGraphDraw, classElement);
        for (int i = 0; i < holders; i++) {
            // previous holders are accessed by holder constructors through static fields that are already assigned at this point
            staticBlock.addStatement("$N = new $T(graphDraw, impl)", "holder" + i, graphTypeName.nestedClass("ComponentHolder" + i));
        }

        var supplierMethodBuilder = MethodSpec.methodBuilder("graph")
            .returns(CommonClassNames.applicationGraphDraw)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addStatement("return graphDraw");


        return JavaFile.builder(packageElement.getQualifiedName().toString(), classBuilder
                .addMethod(supplierMethodBuilder.build())
                .addStaticBlock(staticBlock.build())
                .build())
            .build();
    }

    /**
     * @return number of holders
     */
    private int assignHolders() {
        var holder = 0;
        var holderComponents = 0;
        var holderCodeSize = 0;
        for (var component : components) {
            var references = inlineReferences(this.createDependencies(component)) + inlineReferences(this.refreshDependencies(component)) + this.interceptors.interceptorsFor(component).size();
            var codeSize = NODE_CODE_SIZE + references * NODE_REFERENCE_CODE_SIZE;
            if (holderComponents > 0 && (holderComponents >= KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS || holderCodeSize + codeSize > KoraAppProcessor.HOLDER_CONSTRUCTOR_CODE_BUDGET)) {
                holder++;
                holderComponents = 0;
                holderCodeSize = 0;
            }
            component.setHolder(holder);
            holderComponents++;
            holderCodeSize += codeSize;
        }
        return components.isEmpty() ? 0 : holder + 1;
    }

    private static int inlineReferences(Set<ResolvedComponent> nodes) {
        // wide list is built by helper methods, constructor only has a method call
        return nodes.size() >= GraphHelperMethods.WIDE_LIST_SIZE
            ? 1
            : nodes.size();
    }

    private static boolean isClassLiteral(TypeName typeName) {
        if (typeName instanceof ArrayTypeName arrayTypeName) {
            return arrayTypeName.componentType().isPrimitive() || isClassLiteral(arrayTypeName.componentType());
        }
        return typeName instanceof ClassName;
    }

    private CodeBlock parentCondition(ResolvedComponent component) {
        if (component.parentConditions().size() == 1) {
            var conditionComponent = Objects.requireNonNull(conditions.get(component.parentConditions().iterator().next()));
            return CodeBlock.of("g.condition($L)", conditionComponent.nodeRef("_"));
        } else {
            var b = CodeBlock.builder();
            b.add("$T.or(", CommonClassNames.graphCondition);
            var parentConditions = new ArrayList<>(component.parentConditions());
            for (int i = 0; i < parentConditions.size(); i++) {
                if (i > 0) {
                    b.add(", ");
                }
                var condition = parentConditions.get(i);
                var conditionComponent = Objects.requireNonNull(conditions.get(condition));
                b.add("g.condition($L)", conditionComponent.nodeRef("_"));
            }
            b.add(")");
            return b.build();
        }
    }

    private CodeBlock generateComponentStatement(ClassName graphTypeName, ResolvedComponent component, CodeBlock nodeType, GraphHelperMethods helperMethods) {
        var statement = CodeBlock.builder();
        var declaration = component.declaration();
        var componentHolder = component.holderName();
        var componentField = component.fieldName();
        statement.add("$L = graphDraw.addNode($L, ", componentField, nodeType);
        if (component.tag() == null) {
            statement.add("null, \n");
        } else {
            statement.add("$L.class, \n", component.tag());
        }

        if (component.parentConditions().isEmpty() && component.declaration().condition() == null) {
            statement.add("null,\n");
        } else if (component.parentConditions().isEmpty()) {
            var conditionComponent = Objects.requireNonNull(conditions.get(component.declaration().condition()));
            statement.add("g -> g.condition($L).eval(),\n", conditionComponent.nodeRef("_"));
        } else if (component.declaration().condition() == null) {
            statement.add("g -> $L.eval(),\n", parentCondition(component));
        } else {
            var conditionComponent = Objects.requireNonNull(conditions.get(component.declaration().condition()));
            statement.add("g -> $T.and($L, g.condition($L)).eval(),\n", CommonClassNames.graphCondition, parentCondition(component), conditionComponent.nodeRef("_"));
        }

        var createDependencies = this.createDependencies(component);
        var refreshDependencies = this.refreshDependencies(component);
        var createDependenciesCode = nodeList(componentHolder, createDependencies, helperMethods);
        statement.add("$L,\n", createDependenciesCode);
        statement.add("$L,\n", new ArrayList<>(createDependencies).equals(new ArrayList<>(refreshDependencies))
            ? createDependenciesCode
            : nodeList(componentHolder, refreshDependencies, helperMethods));

        var interceptorsFor = interceptors.interceptorsFor(component);
        statement.add("$T.of(", List.class);
        for (int i = 0; i < interceptorsFor.size(); i++) {
            if (i > 0) {
                statement.add(", ");
            }
            var interceptor = interceptorsFor.get(i);
            statement.add("$L", interceptor.component().nodeRef(componentHolder));
        }
        statement.add("),\n");

        statement.add("g -> ");
        var hasModuleInstance = declaration instanceof ComponentDeclaration.FromModuleComponent moduleComponent
            && (moduleComponent.module() instanceof ModuleDeclaration.ClassModule || moduleComponent.module() instanceof ModuleDeclaration.FactoryModule);
        var dependenciesCode = this.generateDependenciesCode(ctx, component, graphTypeName, hasModuleInstance ? 1 : 0, helperMethods);

        switch (declaration) {
            case ComponentDeclaration.AnnotatedComponent annotatedComponent -> {
                statement.add("new $T", ClassName.get(annotatedComponent.typeElement()));
                if (!annotatedComponent.typeVariables().isEmpty()) {
                    statement.add("<");
                    for (int i = 0; i < annotatedComponent.typeVariables().size(); i++) {
                        if (i > 0) statement.add(", ");
                        statement.add("$T", annotatedComponent.typeVariables().get(i));
                    }
                    statement.add(">");
                }
                statement.add("($L)", dependenciesCode);
            }
            case ComponentDeclaration.FromModuleComponent moduleComponent -> {
                if (moduleComponent.module() instanceof ModuleDeclaration.ClassModule || moduleComponent.module() instanceof ModuleDeclaration.FactoryModule) {
                    var moduleInstDep = component.dependencies().getFirst();
                    statement.add("$L.", moduleInstDep.write(ctx, graphTypeName, helperMethods));
                } else {
                    if (moduleComponent.module() instanceof ModuleDeclaration.AnnotatedModule(var element)) {
                        statement.add("impl.module$L.", allModules.indexOf(element));
                    } else {
                        statement.add("impl.");
                    }
                }
                if (!moduleComponent.typeVariables().isEmpty()) {
                    statement.add("<");
                    for (int i = 0; i < moduleComponent.typeVariables().size(); i++) {
                        if (i > 0) statement.add(", ");
                        statement.add("$T", moduleComponent.typeVariables().get(i));
                    }
                    statement.add(">");
                }
                statement.add("$L($L)", moduleComponent.method().getSimpleName(), dependenciesCode);
            }
            case ComponentDeclaration.FromExtensionComponent extension -> statement.add(extension.generator().apply(dependenciesCode));
            case ComponentDeclaration.PromisedProxyComponent promisedProxyComponent -> {
                if (promisedProxyComponent.typeElement().getTypeParameters().isEmpty()) {
                    statement.add("new $T($L)", promisedProxyComponent.className(), dependenciesCode);
                } else {
                    statement.add("new $T<>($L)", promisedProxyComponent.className(), dependenciesCode);
                }
            }
            case ComponentDeclaration.OptionalComponent optional -> {
                var optionalOf = ((DeclaredType) optional.type()).getTypeArguments().get(0);
                statement.add("$T.<$T>ofNullable($L)", Optional.class, optionalOf, dependenciesCode);
            }
            case null, default -> throw new IllegalStateException("Kora internal error: graph generator got unsupported component declaration: " + declaration);
        }
        statement.add(")");
        return statement.build();

    }


    private CodeBlock generateDependenciesCode(ProcessingContext ctx, ResolvedComponent component, ClassName graphTypeName, int startFrom, GraphHelperMethods helperMethods) {
        var resolvedDependencies = component.dependencies();
        if (resolvedDependencies.isEmpty()) {
            return CodeBlock.of("");
        }
        var b = CodeBlock.builder();
        b.indent();
        b.add("\n");
        for (int i = startFrom, dependenciesSize = resolvedDependencies.size(); i < dependenciesSize; i++) {
            if (i > startFrom) b.add(",\n");
            var resolvedDependency = resolvedDependencies.get(i);
            b.add(resolvedDependency.write(ctx, graphTypeName, helperMethods));
        }
        b.unindent();
        b.add("\n");
        return b.build();
    }

    private Set<ResolvedComponent> createDependencies(ResolvedComponent component) {
        // the same node can be used for several parameters, but graph has to track it as a dependency only once
        var result = new LinkedHashSet<ResolvedComponent>();
        if (component.declaration().condition() != null) {
            var condition = Objects.requireNonNull(this.conditions.get(component.declaration().condition()));
            result.add(condition);
        }
        for (var parentConditionTag : component.parentConditions()) {
            var condition = Objects.requireNonNull(this.conditions.get(parentConditionTag));
            result.add(condition);
        }
        for (var dependency : component.dependencies()) {
            switch (dependency) {
                case ComponentDependency.NullDependency _, ComponentDependency.PromisedProxyParameterDependency _ -> {}
                case ComponentDependency.SingleDependency singleDependency -> {
                    switch (singleDependency) {
                        case ComponentDependency.PromiseOfDependency _ -> {}
                        case ComponentDependency.TargetDependency targetDependency when targetDependency.claim().claimType() != DependencyClaim.DependencyClaimType.NODE_OF ->
                            result.add(targetDependency.component());
                        case ComponentDependency.TargetDependency _ -> {}
                        case ComponentDependency.ValueOfDependency valueOfDependency -> result.add(valueOfDependency.component());
                        case ComponentDependency.WrappedTargetDependency wrappedTargetDependency -> result.add(wrappedTargetDependency.component());
                    }
                }
                case ComponentDependency.AllOfDependency allOfDependency -> {
                    if (allOfDependency.claim().claimType() != DependencyClaim.DependencyClaimType.ALL_OF_PROMISE) {
                        for (var d : allOfDependency.getResolvedDependencies()) {
                            result.add(d.component());
                        }
                    }
                }
                case ComponentDependency.OneOfDependency oneOfDependency -> {
                    for (var singleDependency : oneOfDependency.dependencies()) {
                        switch (singleDependency) {
                            case ComponentDependency.PromiseOfDependency _ -> {}
                            case ComponentDependency.TargetDependency targetDependency -> result.add(targetDependency.component());
                            case ComponentDependency.ValueOfDependency valueOfDependency -> result.add(valueOfDependency.component());
                            case ComponentDependency.WrappedTargetDependency wrappedTargetDependency -> result.add(wrappedTargetDependency.component());
                        }
                    }
                }
                case ComponentDependency.GraphDependency _, ComponentDependency.TypeOfDependency _ -> {}
            }
        }
        return result;
    }

    private static CodeBlock nodeList(String componentHolder, Set<ResolvedComponent> nodes, GraphHelperMethods helperMethods) {
        if (nodes.size() >= GraphHelperMethods.WIDE_LIST_SIZE) {
            return CodeBlock.of("$L()", nodeListMethod(componentHolder, nodes, helperMethods));
        }
        var b = CodeBlock.builder();
        b.add("$T.of(", List.class);
        var first = true;
        for (var node : nodes) {
            if (!first) {
                b.add(", ");
            }
            first = false;
            b.add("$L", node.nodeRef(componentHolder));
        }
        b.add(")");
        return b.build();
    }

    /**
     * Thousands of nodes listed in the holder constructor do not fit into the method size limit, so the list is built by a few methods of the holder
     *
     * @return name of the method that returns list of nodes
     */
    private static String nodeListMethod(String componentHolder, Set<ResolvedComponent> nodes, GraphHelperMethods helperMethods) {
        var name = helperMethods.nextName("nodes");
        var listType = ParameterizedTypeName.get(ClassName.get(List.class), ParameterizedTypeName.get(CommonClassNames.node, WildcardTypeName.subtypeOf(Object.class)));
        var method = MethodSpec.methodBuilder(name)
            .addModifiers(Modifier.PRIVATE)
            .returns(listType)
            .addStatement("$T nodes = new $T<>($L)", listType, ArrayList.class, nodes.size());
        var chunk = 0;
        var chunkMethod = (MethodSpec.Builder) null;
        var chunkSize = 0;
        for (var node : nodes) {
            if (chunkMethod == null) {
                chunkMethod = MethodSpec.methodBuilder(name + "_" + chunk)
                    .addModifiers(Modifier.PRIVATE)
                    .addParameter(listType, "nodes");
            }
            // nodes of this holder are read from its fields: static field of the holder is not assigned yet when constructor is running
            chunkMethod.addStatement("nodes.add($L)", node.nodeRef(componentHolder));
            chunkSize++;
            if (chunkSize == GraphHelperMethods.WIDE_LIST_SIZE) {
                helperMethods.add(chunkMethod.build());
                method.addStatement("$L(nodes)", name + "_" + chunk);
                chunkMethod = null;
                chunkSize = 0;
                chunk++;
            }
        }
        if (chunkMethod != null) {
            helperMethods.add(chunkMethod.build());
            method.addStatement("$L(nodes)", name + "_" + chunk);
        }
        helperMethods.add(method.addStatement("return nodes").build());
        return name;
    }

    private Set<ResolvedComponent> refreshDependencies(ResolvedComponent component) {
        // the same node can be used for several parameters, but graph has to track it as a dependency only once
        var result = new LinkedHashSet<ResolvedComponent>();
        if (component.declaration().condition() != null) {
            var condition = Objects.requireNonNull(this.conditions.get(component.declaration().condition()));
            result.add(condition);
        }
        for (var parentConditionTag : component.parentConditions()) {
            var condition = Objects.requireNonNull(this.conditions.get(parentConditionTag));
            result.add(condition);
        }
        for (var dependency : component.dependencies()) {
            switch (dependency) {
                case ComponentDependency.NullDependency _, ComponentDependency.PromisedProxyParameterDependency _ -> {}
                case ComponentDependency.SingleDependency singleDependency -> {
                    switch (singleDependency) {
                        case ComponentDependency.PromiseOfDependency _, ComponentDependency.ValueOfDependency _ -> {}
                        case ComponentDependency.TargetDependency targetDependency when targetDependency.claim().claimType() != DependencyClaim.DependencyClaimType.NODE_OF ->
                            result.add(targetDependency.component());
                        case ComponentDependency.TargetDependency _ -> {}
                        case ComponentDependency.WrappedTargetDependency wrappedTargetDependency -> result.add(wrappedTargetDependency.component());
                    }
                }
                case ComponentDependency.AllOfDependency allOfDependency -> {
                    if (allOfDependency.claim().claimType() == DependencyClaim.DependencyClaimType.ALL_OF_ONE) {
                        for (var resolved : allOfDependency.getResolvedDependencies()) {
                            result.add(Objects.requireNonNull(resolved.component()));
                        }
                    }
                }
                case ComponentDependency.OneOfDependency oneOfDependency -> {
                    for (var singleDependency : oneOfDependency.dependencies()) {
                        switch (singleDependency) {
                            case ComponentDependency.PromiseOfDependency _ -> {}
                            case ComponentDependency.TargetDependency targetDependency -> result.add(targetDependency.component());
                            case ComponentDependency.ValueOfDependency valueOfDependency -> result.add(valueOfDependency.component());
                            case ComponentDependency.WrappedTargetDependency wrappedTargetDependency -> result.add(wrappedTargetDependency.component());
                        }
                    }
                }
                case ComponentDependency.GraphDependency _, ComponentDependency.TypeOfDependency _ -> {}
            }
        }
        return result;
    }

}
