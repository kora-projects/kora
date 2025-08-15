package ru.tinkoff.kora.kora.app.annotation.processor;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependency;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependencyHelper;
import ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponent;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;
import ru.tinkoff.kora.kora.app.annotation.processor.exception.CircularDependencyException;
import ru.tinkoff.kora.kora.app.annotation.processor.exception.UnresolvedDependencyException;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim.DependencyClaimType.*;

public class GraphBuilder {
    private final ProcessingContext ctx;
    private final RoundEnvironment roundEnv;
    private final TypeElement root;
    private final List<TypeElement> allModules;
    private final List<ComponentDeclaration> sourceDeclarations;
    private final List<ComponentDeclaration> templates;
    private final List<ComponentDeclaration> rootSet;

    public GraphBuilder(ProcessingContext ctx, RoundEnvironment roundEnv, TypeElement root, List<TypeElement> allModules, List<ComponentDeclaration> sourceDeclarations, List<ComponentDeclaration> templates, List<ComponentDeclaration> rootSet) {
        this.ctx = ctx;
        this.roundEnv = roundEnv;
        this.root = root;
        this.allModules = allModules;
        this.sourceDeclarations = sourceDeclarations;
        this.templates = templates;
        this.rootSet = rootSet;
    }

    public ResolvedGraph build() {
        return build(new ArrayList<>(), new ArrayDeque<>());
    }

    private sealed interface ResolutionFrame {
        record Root(int rootIndex) implements ResolutionFrame {}

        record Component(ComponentDeclaration declaration, List<DependencyClaim> dependenciesToFind, List<ComponentDependency> resolvedDependencies, int currentDependency) implements ResolutionFrame {
            public Component(ComponentDeclaration declaration, List<DependencyClaim> dependenciesToFind) {
                this(declaration, dependenciesToFind, new ArrayList<>(dependenciesToFind.size()), 0);
            }

            public Component withCurrentDependency(int currentDependency) {
                return new Component(declaration, dependenciesToFind, resolvedDependencies, currentDependency);
            }
        }
    }


    private ResolvedGraph build(List<ResolvedComponent> resolvedComponents, Deque<ResolutionFrame> stack) {
        if (rootSet.isEmpty()) {
            throw new ProcessingErrorException(
                "@KoraApp has no root components, expected at least one component annotated with @Root",
                root
            );
        }
        frame:
        while (!stack.isEmpty()) {
            var frame = stack.removeLast();
            if (frame instanceof ResolutionFrame.Root root) {
                var declaration = rootSet.get(root.rootIndex());
                if (findResolvedComponent(resolvedComponents, declaration) != null) {
                    continue;
                }
                stack.add(new ResolutionFrame.Component(
                    declaration, ComponentDependencyHelper.parseDependencyClaims(ctx, declaration)
                ));
                stack.addAll(findInterceptors(ctx, resolvedComponents, stack, declaration));
                continue;
            }

            var componentFrame = (ResolutionFrame.Component) frame;
            var declaration = componentFrame.declaration();
            var dependenciesToFind = componentFrame.dependenciesToFind();
            var resolvedDependencies = componentFrame.resolvedDependencies();
            if (checkCycle(ctx, stack, resolvedComponents, declaration)) {
                continue;
            }

            dependency:
            for (int currentDependency = componentFrame.currentDependency(); currentDependency < dependenciesToFind.size(); currentDependency++) {
                var dependencyClaim = dependenciesToFind.get(currentDependency);
                if (dependencyClaim.claimType() == ALL_OF_ONE || dependencyClaim.claimType() == ALL_OF_PROMISE || dependencyClaim.claimType() == ALL_OF_VALUE) {
                    var allOfDependency = processAllOf(ctx, resolvedComponents, stack, componentFrame, currentDependency);
                    if (allOfDependency == null) {
                        continue frame;
                    } else {
                        resolvedDependencies.add(allOfDependency);
                        continue dependency;
                    }
                }
                if (dependencyClaim.claimType() == TYPE_REF) {
                    resolvedDependencies.add(new ComponentDependency.TypeOfDependency(dependencyClaim));
                    continue dependency;
                }
                var dependencyComponent = GraphResolutionHelper.findDependency(ctx, declaration, resolvedComponents, dependencyClaim);
                if (dependencyComponent != null) {
                    // there's matching component in graph
                    resolvedDependencies.add(dependencyComponent);
                    continue dependency;
                }
                var dependencyDeclaration = GraphResolutionHelper.findDependencyDeclaration(ctx, declaration, sourceDeclarations, dependencyClaim);
                if (dependencyDeclaration != null) {
                    // component not yet resolved - adding it to the tail, resolving
                    stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                    stack.addLast(new ResolutionFrame.Component(
                        dependencyDeclaration, ComponentDependencyHelper.parseDependencyClaims(ctx, dependencyDeclaration)
                    ));
                    stack.addAll(findInterceptors(ctx, resolvedComponents, stack, dependencyDeclaration));
                    continue frame;
                }
                var matchingTemplates = GraphResolutionHelper.findDependencyDeclarationsFromTemplate(ctx, declaration, templates, dependencyClaim);
                if (!matchingTemplates.isEmpty()) {
                    if (matchingTemplates.size() == 1) {
                        var template = matchingTemplates.get(0);
                        sourceDeclarations.add(template);
                        stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                        stack.addLast(new ResolutionFrame.Component(
                            template, ComponentDependencyHelper.parseDependencyClaims(ctx, template)
                        ));
                        stack.addAll(findInterceptors(ctx, resolvedComponents, stack, template));
                        continue frame;
                    }
                    UnresolvedDependencyException exception = null;
                    var results = new ArrayList<ResolvedGraph>(templates.size());
                    for (var template : templates) {
                        var fork = new GraphBuilder(ctx, roundEnv, root, new ArrayList<>(allModules), new ArrayList<>(sourceDeclarations), new ArrayList<>(templates), new ArrayList<>(rootSet));
                        var forkStack = new ArrayDeque<>(stack);
                        var forkComponents = new ArrayList<>(resolvedComponents);
                        fork.build(forkComponents, forkStack);
                        fork.sourceDeclarations.add(template);
                        forkStack.addLast(componentFrame.withCurrentDependency(currentDependency));
                        forkStack.addLast(new ResolutionFrame.Component(
                            template, ComponentDependencyHelper.parseDependencyClaims(ctx, template)
                        ));
                        forkStack.addAll(fork.findInterceptors(ctx, forkComponents, forkStack, template));

                        try {
                            results.add(fork.build(forkComponents, forkStack));
                        } catch (UnresolvedDependencyException e) {
                            if (exception != null) {
                                exception.addSuppressed(e);
                            } else {
                                exception = e;
                            }
                        }
                    }
                    if (results.size() == 1) {
                        return results.get(0);
                    }
                    if (results.size() > 1) {
                        var deps = templates.stream().map(Objects::toString).collect(Collectors.joining("\n")).indent(2);
                        if (dependencyClaim.tags().isEmpty()) {
                            throw new ProcessingErrorException("More than one component matches dependency claim " + dependencyClaim.type() + ":\n" + deps, declaration.source());
                        } else {
                            var tagMsg = dependencyClaim.tags().stream().collect(Collectors.joining(", ", "@Tag(", ")"));
                            throw new ProcessingErrorException("More than one component matches dependency claim " + dependencyClaim.type() + " with tag " + tagMsg + " :\n" + deps, declaration.source());
                        }
                    }
                    throw exception;
                }
                if (dependencyClaim.claimType().isNullable()) {
                    resolvedDependencies.add(new ComponentDependency.NullDependency(dependencyClaim));
                    continue dependency;
                }
                if (dependencyClaim.type().toString().startsWith("java.util.Optional<")) {
                    var optionalDeclaration = new ComponentDeclaration.OptionalComponent(dependencyClaim.type(), dependencyClaim.tags());
                    sourceDeclarations.add(optionalDeclaration);
                    stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                    stack.addLast(new ResolutionFrame.Component(
                        optionalDeclaration, List.of(ComponentDependencyHelper.parseClaim(componentFrame.declaration().source(), ((DeclaredType) dependencyClaim.type()).getTypeArguments().get(0), dependencyClaim.tags(), true))
                    ));
                    continue frame;
                }
                var finalClassComponent = GraphResolutionHelper.findFinalDependency(ctx, dependencyClaim);
                if (finalClassComponent != null) {
                    sourceDeclarations.add(finalClassComponent);
                    stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                    stack.addLast(new ResolutionFrame.Component(
                        finalClassComponent, ComponentDependencyHelper.parseDependencyClaims(ctx, finalClassComponent)
                    ));
                    stack.addAll(findInterceptors(ctx, resolvedComponents, stack, finalClassComponent));
                    continue frame;
                }
                var extension = ctx.extensions.findExtension(roundEnv, dependencyClaim.type(), dependencyClaim.tags());
                if (extension != null) {
                    ExtensionResult extensionResult;
                    try {
                        extensionResult = Objects.requireNonNull(extension.generateDependency());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    if (extensionResult instanceof ExtensionResult.CodeBlockResult codeBlockResult) {
                        var extensionComponent = ComponentDeclaration.fromExtension(codeBlockResult);
                        if (extensionComponent.isTemplate()) {
                            templates.add(extensionComponent);
                        } else {
                            sourceDeclarations.add(extensionComponent);
                        }
                        stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                        continue frame;

                    } else {
                        var generated = (ExtensionResult.GeneratedResult) extensionResult;
                        var extensionComponent = ComponentDeclaration.fromExtension(ctx, generated);
                        if (extensionComponent.isTemplate()) {
                            templates.add(extensionComponent);
                        } else {
                            sourceDeclarations.add(extensionComponent);
                        }
                        stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                        continue frame;
                    }
                }

                var claimTypeName = TypeName.get(dependencyClaim.type()).annotated(List.of());
                var hints = ctx.dependencyModuleHintProvider.findHints(dependencyClaim.type(), dependencyClaim.tags());
                var msg = new StringBuilder();
                if (dependencyClaim.tags().isEmpty()) {
                    msg.append(String.format("Required dependency type wasn't found and can't be auto created: %s.\n" +
                            "Please check class for @%s annotation or that required module with component is plugged in.",
                        claimTypeName, CommonClassNames.component.simpleName()));
                } else {
                    var tagMsg = dependencyClaim.tags().stream().collect(Collectors.joining(", ", "@Tag(", ")"));
                    msg.append(String.format("Required dependency type wasn't found and can't be auto created: %s with tag %s.\n" +
                            "Please check class for @%s annotation or that required module with component is plugged in.",
                        claimTypeName, tagMsg, CommonClassNames.component.simpleName()));
                }
                for (var hint : hints) {
                    msg.append("\n  Hint: ").append(hint.message());
                }
                msg.append("\nDependency chain:");
                msg.append("\n  ").append(declaration.declarationString());
                var i = stack.descendingIterator();
                while (i.hasNext()) {
                    var iFrame = i.next();
                    if (iFrame instanceof ResolutionFrame.Root root) {
                        msg.append("\n  ").append(rootSet.get(root.rootIndex()).declarationString());
                        break;
                    }
                    var c = (ResolutionFrame.Component) iFrame;
                    msg.append("\n  ").append(c.declaration().declarationString());
                }

                throw new UnresolvedDependencyException(
                    msg.toString(),
                    declaration.source(),
                    dependencyClaim.type(),
                    dependencyClaim.tags()
                );
            }
            resolvedComponents.add(new ResolvedComponent(
                resolvedComponents.size(), declaration, declaration.type(), declaration.tags(),
                List.of(), // TODO,
                resolvedDependencies
            ));
        }
        return new ResolvedGraph(root, allModules, resolvedComponents);
    }

    @Nullable
    public static ResolvedComponent findResolvedComponent(List<ResolvedComponent> resolvedComponents, ComponentDeclaration declaration) {
        for (var resolvedComponent : resolvedComponents) {
            if (declaration == resolvedComponent.declaration()) {
                return resolvedComponent;
            }
        }
        return null;
    }

    @Nullable
    private ComponentDependency processAllOf(ProcessingContext ctx, List<ResolvedComponent> resolvedComponents, Deque<ResolutionFrame> resolutionStack, ResolutionFrame.Component componentFrame, int currentDependency) {
        var dependencyClaim = componentFrame.dependenciesToFind().get(currentDependency);
        var dependencies = GraphResolutionHelper.findDependencyDeclarations(ctx, sourceDeclarations, dependencyClaim);
        for (var dependency : dependencies) {
            if (dependency.isDefault()) {
                continue;
            }
            var resolved = findResolvedComponent(resolvedComponents, dependency);
            if (resolved != null) {
                continue;
            }
            resolutionStack.addLast(componentFrame.withCurrentDependency(currentDependency));
            resolutionStack.addLast(new ResolutionFrame.Component(
                dependency, ComponentDependencyHelper.parseDependencyClaims(ctx, dependency)
            ));
            resolutionStack.addAll(findInterceptors(ctx, resolvedComponents, resolutionStack, dependency));
            return null;
        }
        if (dependencyClaim.claimType() == ALL_OF_ONE) {
            return new ComponentDependency.AllOfDependency(dependencyClaim);
        }
        if (dependencyClaim.claimType() == ALL_OF_VALUE) {
            return new ComponentDependency.AllOfDependency(dependencyClaim);
        }
        if (dependencyClaim.claimType() == ALL_OF_PROMISE) {
            return new ComponentDependency.AllOfDependency(dependencyClaim);
        }
        throw new IllegalStateException();
    }

    private List<ResolutionFrame.Component> findInterceptors(ProcessingContext ctx, List<ResolvedComponent> resolvedComponents, Deque<ResolutionFrame> resolutionStack, ComponentDeclaration declaration) {
        return GraphResolutionHelper.findInterceptorDeclarations(ctx, sourceDeclarations, declaration.type())
            .stream()
            .filter(id -> resolvedComponents.stream().noneMatch(rc -> rc.declaration() == id) && resolutionStack.stream().noneMatch(rf -> rf instanceof ResolutionFrame.Component c && c.declaration() == id))
            .map(id -> new ResolutionFrame.Component(id, ComponentDependencyHelper.parseDependencyClaims(ctx, id)))
            .toList();
    }

    private static ComponentDeclaration generatePromisedProxy(ProcessingContext ctx, TypeElement typeElement) {
        var packageElement = ctx.elements.getPackageOf(typeElement);
        var resultClassName = NameUtils.generatedType(typeElement, CommonClassNames.promisedProxy);
        var alreadyGenerated = ctx.elements.getTypeElement(packageElement.getQualifiedName() + "." + resultClassName);
        if (alreadyGenerated != null) {
            return new ComponentDeclaration.PromisedProxyComponent(typeElement, ClassName.get(packageElement.getQualifiedName().toString(), resultClassName));
        }

        var typeMirror = typeElement.asType();
        var typeName = TypeName.get(typeMirror);
        var promiseType = ParameterizedTypeName.get(CommonClassNames.promiseOf, WildcardTypeName.subtypeOf(typeName));
        var type = TypeSpec.classBuilder(resultClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addField(promiseType, "promise", Modifier.PRIVATE, Modifier.FINAL)
            .addField(typeName, "delegate", Modifier.PRIVATE, Modifier.VOLATILE)
            .addSuperinterface(ParameterizedTypeName.get(CommonClassNames.promisedProxy, typeName))
            .addSuperinterface(CommonClassNames.refreshListener)
            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(promiseType, "promise")
                .addStatement("this.promise = promise")
                .build())
            .addMethod(MethodSpec.methodBuilder("graphRefreshed")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addStatement("this.delegate = null")
                .addStatement("this.getDelegate()")
                .build())
            .addMethod(MethodSpec.methodBuilder("getDelegate")
                .addModifiers(Modifier.PRIVATE)
                .returns(typeName)
                .addCode(CodeBlock.builder()
                    .addStatement("var delegate = this.delegate")
                    .beginControlFlow("if (delegate == null)")
                    .addStatement("delegate = this.promise.get().get()")
                    .addStatement("this.delegate = delegate")
                    .endControlFlow()
                    .addStatement("return delegate")
                    .build())
                .build());
        for (var typeParameter : typeElement.getTypeParameters()) {
            type.addTypeVariable(TypeVariableName.get(typeParameter));
        }
        if (typeElement.getKind() == ElementKind.INTERFACE) {
            type.addSuperinterface(typeName);
        } else {
            type.superclass(typeName);
        }
        for (var enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            var methodElement = (ExecutableElement) enclosedElement;
            var method = MethodSpec.overriding(methodElement, (DeclaredType) typeMirror, ctx.types);
            if (methodElement.getReturnType().getKind() != TypeKind.VOID) {
                method.addCode("return ");
            }
            method.addCode("this.getDelegate().$L(", methodElement.getSimpleName());

            for (int i = 0; i < methodElement.getParameters().size(); i++) {
                if (i > 0) {
                    method.addCode(", ");
                }
                method.addCode(methodElement.getParameters().get(i).getSimpleName().toString());
            }
            method.addCode(");\n");
            type.addMethod(method.build());
        }
        var javaFile = JavaFile.builder(packageElement.getQualifiedName().toString(), type.build());
        try {
            javaFile.build().writeTo(ctx.filer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new ComponentDeclaration.PromisedProxyComponent(typeElement, ClassName.get(packageElement.getQualifiedName().toString(), resultClassName));
    }

    private boolean checkCycle(ProcessingContext ctx, Deque<ResolutionFrame> resolutionStack, List<ResolvedComponent> resolvedComponents, ComponentDeclaration declaration) {
        var prevFrame = resolutionStack.peekLast();
        if (!(prevFrame instanceof ResolutionFrame.Component prevComponent)) {
            return false;
        }
        if (prevComponent.dependenciesToFind().isEmpty()) {
            return false;
        }
        var dependencyClaim = prevComponent.dependenciesToFind().get(prevComponent.currentDependency());
        var dependencyClaimType = dependencyClaim.type();
        var dependencyClaimTypeElement = ctx.types.asElement(dependencyClaimType);
        if (!(ctx.types.isAssignable(declaration.type(), dependencyClaimType) || ctx.serviceTypeHelper.isAssignableToUnwrapped(declaration.type(), dependencyClaimType) || ctx.serviceTypeHelper.isInterceptor(declaration.type()))) {
            throw new CircularDependencyException(List.of(prevComponent.declaration().toString(), declaration.toString()), declaration);
        }
        for (var inStackFrame : resolutionStack) {
            if (!(inStackFrame instanceof ResolutionFrame.Component componentFrame) || componentFrame.declaration() != declaration) {
                continue;
            }
            if (dependencyClaim.type().getKind() != TypeKind.DECLARED) {
                throw new CircularDependencyException(List.of(prevComponent.declaration().toString(), declaration.toString()), componentFrame.declaration());
            }
            if (dependencyClaimTypeElement.getKind() != ElementKind.INTERFACE && (dependencyClaimTypeElement.getKind() != ElementKind.CLASS || dependencyClaimTypeElement.getModifiers().contains(Modifier.FINAL))) {
                throw new CircularDependencyException(List.of(prevComponent.declaration().toString(), declaration.toString()), componentFrame.declaration());
            }
            var proxyDependencyClaim = new DependencyClaim(
                dependencyClaimType, Set.of(CommonClassNames.promisedProxy.canonicalName()), dependencyClaim.claimType()
            );
            var alreadyGenerated = GraphResolutionHelper.findDependency(ctx, prevComponent.declaration(), resolvedComponents, proxyDependencyClaim);
            if (alreadyGenerated != null) {
                resolutionStack.removeLast();
                prevComponent.resolvedDependencies().add(alreadyGenerated);
                resolutionStack.addLast(prevComponent.withCurrentDependency(prevComponent.currentDependency() + 1));
                return true;
            }
            var proxyComponentDeclaration = GraphResolutionHelper.findDependencyDeclarationFromTemplate(
                ctx, declaration, templates, proxyDependencyClaim
            );
            if (proxyComponentDeclaration == null) {
                proxyComponentDeclaration = generatePromisedProxy(ctx, (TypeElement) dependencyClaimTypeElement);
                if (proxyComponentDeclaration.isTemplate()) {
                    templates.add(proxyComponentDeclaration);
                } else {
                    sourceDeclarations.add(proxyComponentDeclaration);
                }
            }
            var proxyResolvedComponent = new ResolvedComponent(
                resolvedComponents.size(),
                proxyComponentDeclaration,
                dependencyClaimType,
                Set.of(CommonClassNames.promisedProxy.canonicalName()),
                List.of(),
                List.of(new ComponentDependency.PromisedProxyParameterDependency(declaration, new DependencyClaim(
                    declaration.type(),
                    declaration.tags(),
                    ONE_REQUIRED
                )))
            );
            resolvedComponents.add(proxyResolvedComponent);
            return true;
        }
        return false;
    }
}
