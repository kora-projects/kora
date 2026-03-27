package io.koraframework.kora.app.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.kora.app.annotation.processor.component.ComponentDependency;
import io.koraframework.kora.app.annotation.processor.component.DependencyClaim;
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
        var currentClass = (TypeSpec.Builder) null;
        var currentConstructor = (MethodSpec.Builder) null;
        int holders = 0;
        var componentsList = new ArrayList<>(components);
        for (int i = 0; i < componentsList.size(); i++) {
            var componentNumber = i % KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS;
            if (componentNumber == 0) {
                if (currentClass != null) {
                    currentClass.addMethod(currentConstructor.build());
                    classBuilder.addType(currentClass.build());
                    var prevNumber = ((i / KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS) - 1);
                    classBuilder.addField(graphTypeName.nestedClass("ComponentHolder" + prevNumber), "holder" + prevNumber, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
                }
                holders++;
                var className = graphTypeName.nestedClass("ComponentHolder" + i / KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS);
                currentClass = TypeSpec.classBuilder(className)
                    .addAnnotation(AnnotationUtils.generated(KoraAppProcessor.class))
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
                currentConstructor = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(CommonClassNames.applicationGraphDraw, "graphDraw")
                    .addParameter(implClass, "impl")
                    .addStatement("var map = new $T<$T, $T>()", HashMap.class, String.class, Type.class)
                    .beginControlFlow("for (var field : $T.class.getDeclaredFields())", className)
                    .addStatement("if (!field.getName().startsWith($S)) continue", "component")
                    .addStatement("map.put(field.getName(), (($T) field.getGenericType()).getActualTypeArguments()[0])", ParameterizedType.class)
                    .endControlFlow();
                for (int j = 0; j < i / KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS; j++) {
                    currentConstructor.addParameter(graphTypeName.nestedClass("ComponentHolder" + j), "ComponentHolder" + j);
                }
            }
            var component = componentsList.get(i);
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
            currentConstructor.addStatement("var _type_of_$L = map.get($S)", component.fieldName(), component.fieldName());
            var statement = this.generateComponentStatement(graphTypeName, component);
            currentConstructor.addStatement(statement);
        }
        if (components.size() > 0) {
            var lastComponentNumber = components.size() / KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS;
            if (components.size() % KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS == 0) {
                lastComponentNumber--;
            }
            currentClass.addMethod(currentConstructor.build());
            classBuilder.addType(currentClass.build());
            classBuilder.addField(graphTypeName.nestedClass("ComponentHolder" + lastComponentNumber), "holder" + lastComponentNumber, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
        }


        var staticBlock = CodeBlock.builder();

        staticBlock
            .addStatement("var impl = new $T()", implClass)
            .addStatement("graphDraw = new $T($T.class)", CommonClassNames.applicationGraphDraw, classElement);
        for (int i = 0; i < holders; i++) {
            staticBlock.add("$N = new $T(graphDraw, impl", "holder" + i, graphTypeName.nestedClass("ComponentHolder" + i));
            for (int j = 0; j < i; j++) {
                staticBlock.add(", holder" + j);
            }
            staticBlock.add(");\n");
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

    private CodeBlock generateComponentStatement(ClassName graphTypeName, ResolvedComponent component) {
        var statement = CodeBlock.builder();
        var declaration = component.declaration();
        var componentHolder = component.holderName();
        var componentField = component.fieldName();
        statement.add("$L = graphDraw.addNode(_type_of_$L, ", componentField, componentField);
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

        var createDependencies = this.getCreateDependencies(componentHolder, component);
        statement.add("$L,\n", createDependencies);

        var refreshDependencies = this.getRefreshDependencies(componentHolder, component);
        statement.add("$L,\n", refreshDependencies);

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
        var dependenciesCode = this.generateDependenciesCode(ctx, component, graphTypeName);

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
                if (moduleComponent.module() instanceof ModuleDeclaration.AnnotatedModule(var element)) {
                    statement.add("impl.module$L.", allModules.indexOf(element));
                } else {
                    statement.add("impl.");
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
            case null, default -> throw new RuntimeException("Unknown type " + declaration);
        }
        statement.add(")");
        return statement.build();

    }


    private CodeBlock generateDependenciesCode(ProcessingContext ctx, ResolvedComponent component, ClassName graphTypeName) {
        var resolvedDependencies = component.dependencies();
        if (resolvedDependencies.isEmpty()) {
            return CodeBlock.of("");
        }
        var b = CodeBlock.builder();
        b.indent();
        b.add("\n");
        for (int i = 0, dependenciesSize = resolvedDependencies.size(); i < dependenciesSize; i++) {
            if (i > 0) b.add(",\n");
            var resolvedDependency = resolvedDependencies.get(i);
            b.add(resolvedDependency.write(ctx, graphTypeName));
        }
        b.unindent();
        b.add("\n");
        return b.build();
    }

    private CodeBlock getCreateDependencies(String componentHolder, ResolvedComponent component) {
        var result = new ArrayList<ResolvedComponent>();
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
        var b = CodeBlock.builder();
        b.add("$T.of(", List.class);
        for (int i = 0; i < result.size(); i++) {
            if (i > 0) {
                b.add(", ");
            }
            b.add("$L", CodeBlock.of("$L", result.get(i).nodeRef(componentHolder)));
        }
        b.add(")");
        return b.build();
    }

    private CodeBlock getRefreshDependencies(String componentHolder, ResolvedComponent component) {
        var result = new ArrayList<ResolvedComponent>();
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
        var b = CodeBlock.builder();
        b.add("$T.of(", List.class);
        for (int i = 0; i < result.size(); i++) {
            if (i > 0) {
                b.add(", ");
            }
            b.add("$L", result.get(i).nodeRef(componentHolder));
        }
        b.add(")");
        return b.build();
    }

}
