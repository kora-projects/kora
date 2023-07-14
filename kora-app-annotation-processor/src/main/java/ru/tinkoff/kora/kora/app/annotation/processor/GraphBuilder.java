package ru.tinkoff.kora.kora.app.annotation.processor;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependency;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependencyHelper;
import ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponent;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;
import ru.tinkoff.kora.kora.app.annotation.processor.exception.CircularDependencyException;
import ru.tinkoff.kora.kora.app.annotation.processor.exception.NewRoundException;
import ru.tinkoff.kora.kora.app.annotation.processor.exception.UnresolvedDependencyException;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;

import javax.annotation.Nullable;
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
    public static ProcessingState processProcessing(ProcessingContext ctx, RoundEnvironment roundEnv, ProcessingState.Processing processing) {
        return processProcessing(ctx, roundEnv, processing, null);
    }

    public static ProcessingState processProcessing(ProcessingContext ctx, RoundEnvironment roundEnv, ProcessingState.Processing processing, @Nullable DependencyClaim forClaim) {
        if (processing.rootSet().isEmpty()) {
            return new ProcessingState.Failed(new ProcessingErrorException(
                "@KoraApp has no root components, expected at least one component annotated with @Root",
                processing.root()
            ));
        }
        var stack = processing.resolutionStack();
        frame:
        while (!stack.isEmpty()) {
            var frame = stack.removeLast();
            if (frame instanceof ProcessingState.ResolutionFrame.Root root) {
                var declaration = processing.rootSet().get(root.rootIndex());
                if (processing.findResolvedComponent(declaration) != null) {
                    continue;
                }
                stack.add(new ProcessingState.ResolutionFrame.Component(
                    declaration, ComponentDependencyHelper.parseDependencyClaims(declaration)
                ));
                stack.addAll(findInterceptors(ctx, processing, declaration));
                continue;
            }

            var componentFrame = (ProcessingState.ResolutionFrame.Component) frame;
            var declaration = componentFrame.declaration();
            var dependenciesToFind = componentFrame.dependenciesToFind();
            var resolvedDependencies = componentFrame.resolvedDependencies();
            if (checkCycle(ctx, processing, declaration)) {
                continue;
            }

            dependency:
            for (int currentDependency = componentFrame.currentDependency(); currentDependency < dependenciesToFind.size(); currentDependency++) {
                var dependencyClaim = dependenciesToFind.get(currentDependency);
                if (dependencyClaim.claimType() == ALL_OF_ONE || dependencyClaim.claimType() == ALL_OF_PROMISE || dependencyClaim.claimType() == ALL_OF_VALUE) {
                    var allOfDependency = processAllOf(ctx, processing, componentFrame, currentDependency);
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
                var dependencyComponent = GraphResolutionHelper.findDependency(ctx, declaration, processing.resolvedComponents(), dependencyClaim);
                if (dependencyComponent != null) {
                    // there's matching component in graph
                    resolvedDependencies.add(dependencyComponent);
                    continue dependency;
                }
                var dependencyDeclaration = GraphResolutionHelper.findDependencyDeclaration(ctx, declaration, processing.sourceDeclarations(), dependencyClaim);
                if (dependencyDeclaration != null) {
                    // component not yet resolved - adding it to the tail, resolving
                    stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                    stack.addLast(new ProcessingState.ResolutionFrame.Component(
                        dependencyDeclaration, ComponentDependencyHelper.parseDependencyClaims(dependencyDeclaration)
                    ));
                    stack.addAll(findInterceptors(ctx, processing, dependencyDeclaration));
                    continue frame;
                }
                var templates = GraphResolutionHelper.findDependencyDeclarationsFromTemplate(ctx, declaration, processing.templates(), dependencyClaim);
                if (!templates.isEmpty()) {
                    if (templates.size() == 1) {
                        var template = templates.get(0);
                        processing.sourceDeclarations().add(template);
                        stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                        stack.addLast(new ProcessingState.ResolutionFrame.Component(
                            template, ComponentDependencyHelper.parseDependencyClaims(template)
                        ));
                        stack.addAll(findInterceptors(ctx, processing, template));
                        continue frame;
                    }
                    UnresolvedDependencyException exception = null;
                    var results = new ArrayList<ProcessingState>(templates.size());
                    for (var template : templates) {
                        var newProcessing = new ProcessingState.Processing(
                            processing.root(),
                            processing.allModules(),
                            new ArrayList<>(processing.sourceDeclarations()),
                            new ArrayList<>(processing.templates()),
                            processing.rootSet(),
                            new ArrayList<>(processing.resolvedComponents()),
                            new ArrayDeque<>(processing.resolutionStack())
                        );
                        newProcessing.sourceDeclarations().add(template);
                        newProcessing.resolutionStack().addLast(componentFrame.withCurrentDependency(currentDependency));
                        newProcessing.resolutionStack().addLast(new ProcessingState.ResolutionFrame.Component(
                            template, ComponentDependencyHelper.parseDependencyClaims(template)
                        ));
                        newProcessing.resolutionStack().addAll(findInterceptors(ctx, processing, template));

                        try {
                            results.add(processProcessing(ctx, roundEnv, newProcessing, dependencyClaim));
                        } catch (NewRoundException e) {
                            results.add(e.getResolving());
                        } catch (UnresolvedDependencyException e) {
                            if (exception != null) {
                                exception.addSuppressed(e);
                            } else {
                                exception = e;
                            }
                        }
                    }
                    if (results.size() == 1) {
                        var result = results.get(0);
                        if (result instanceof ProcessingState.Processing processing1) {
                            processing = processing1;
                            stack = processing1.resolutionStack();
                            continue frame;
                        }
                    }
                    if (results.size() > 1) {
                        var deps = templates.stream().map(Objects::toString).collect(Collectors.joining("\n")).indent(2);
                        if(dependencyClaim.tags().isEmpty()) {
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
                    processing.sourceDeclarations().add(optionalDeclaration);
                    stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                    stack.addLast(new ProcessingState.ResolutionFrame.Component(
                        optionalDeclaration, List.of(ComponentDependencyHelper.parseClaim(((DeclaredType) dependencyClaim.type()).getTypeArguments().get(0), dependencyClaim.tags(), true))
                    ));
                    continue frame;
                }
                var finalClassComponent = GraphResolutionHelper.findFinalDependency(dependencyClaim);
                if (finalClassComponent != null) {
                    processing.sourceDeclarations().add(finalClassComponent);
                    stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                    stack.addLast(new ProcessingState.ResolutionFrame.Component(
                        finalClassComponent, ComponentDependencyHelper.parseDependencyClaims(finalClassComponent)
                    ));
                    stack.addAll(findInterceptors(ctx, processing, finalClassComponent));
                    continue frame;
                }
                var extension = ctx.extensions.findExtension(roundEnv, dependencyClaim.type());
                if (extension != null) {
                    ExtensionResult extensionResult;
                    try {
                        extensionResult = Objects.requireNonNull(extension.generateDependency());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    if (extensionResult instanceof ExtensionResult.RequiresCompilingResult) {
                        stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                        throw new NewRoundException(
                            processing, extension, dependencyClaim.type(), dependencyClaim.tags()
                        );
                    } else {
                        var generated = (ExtensionResult.GeneratedResult) extensionResult;
                        var extensionComponent = ComponentDeclaration.fromExtension(generated);
                        if (extensionComponent.isTemplate()) {
                            processing.templates().add(extensionComponent);
                        } else {
                            processing.sourceDeclarations().add(extensionComponent);
                        }
                        stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                        continue frame;
                    }
                }
                var hints = ctx.dependencyModuleHintProvider.findHints(dependencyClaim.type(), dependencyClaim.tags());
                var msg = new StringBuilder();
                var claimTypeName = TypeName.get(dependencyClaim.type()).annotated(List.of());
                if (dependencyClaim.tags().isEmpty()) {
                    msg.append(String.format("Required dependency type was not found and can't be auto created: %s.\n" +
                                             "Please check class for @%s annotation or that required module with component is plugged in.",
                        claimTypeName, CommonClassNames.component.simpleName()));
                } else {
                    var tagMsg = dependencyClaim.tags().stream().collect(Collectors.joining(", ", "@Tag(", ")"));
                    msg.append(String.format("Required dependency type was not found and can't be auto created: %s with tag %s.\n" +
                                             "Please check class for @%s annotation or that required module with component is plugged in.",
                        claimTypeName, tagMsg, CommonClassNames.component.simpleName()));
                }
                for (var hint : hints) {
                    msg.append("\n  Hint: ").append(hint.message());
                }
                throw new UnresolvedDependencyException(
                    msg.toString(),
                    declaration.source(),
                    dependencyClaim.type(),
                    dependencyClaim.tags()
                );
            }
            processing.resolvedComponents().add(new ResolvedComponent(
                processing.resolvedComponents().size(), declaration, declaration.type(), declaration.tags(),
                List.of(), // TODO,
                resolvedDependencies
            ));
            if (forClaim != null) {
                if (forClaim.tagsMatches(componentFrame.declaration().tags()) && ctx.types.isAssignable(componentFrame.declaration().type(), forClaim.type())) {
                    return processing;
                }
            }
        }
        return new ProcessingState.Ok(processing.root(), processing.allModules(), processing.resolvedComponents());
    }

    @Nullable
    private static ComponentDependency processAllOf(ProcessingContext ctx, ProcessingState.Processing processing, ProcessingState.ResolutionFrame.Component componentFrame, int currentDependency) {
        var dependencyClaim = componentFrame.dependenciesToFind().get(currentDependency);
        var dependencies = GraphResolutionHelper.findDependencyDeclarations(ctx, processing.sourceDeclarations(), dependencyClaim);
        for (var dependency : dependencies) {
            if (dependency.isDefault()) {
                continue;
            }
            var resolved = processing.findResolvedComponent(dependency);
            if (resolved != null) {
                continue;
            }
            processing.resolutionStack().addLast(componentFrame.withCurrentDependency(currentDependency));
            processing.resolutionStack().addLast(new ProcessingState.ResolutionFrame.Component(
                dependency, ComponentDependencyHelper.parseDependencyClaims(dependency)
            ));
            processing.resolutionStack().addAll(findInterceptors(ctx, processing, dependency));
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

    private static List<ProcessingState.ResolutionFrame.Component> findInterceptors(ProcessingContext ctx, ProcessingState.Processing processing, ComponentDeclaration declaration) {
        return GraphResolutionHelper.findInterceptorDeclarations(ctx, processing.sourceDeclarations(), declaration.type())
            .stream()
            .filter(id -> processing.resolvedComponents().stream().noneMatch(rc -> rc.declaration() == id) && processing.resolutionStack().stream().noneMatch(rf -> rf instanceof ProcessingState.ResolutionFrame.Component c && c.declaration() == id))
            .map(id -> new ProcessingState.ResolutionFrame.Component(id, ComponentDependencyHelper.parseDependencyClaims(id)))
            .toList();
    }

    private static ComponentDeclaration generatePromisedProxy(ProcessingContext ctx, TypeElement typeElement) {
        var resultClassName = CommonUtils.getOuterClassesAsPrefix(typeElement) + typeElement.getSimpleName() + "_PromisedProxy";
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
        var packageElement = ctx.elements.getPackageOf(typeElement);
        var javaFile = JavaFile.builder(packageElement.getQualifiedName().toString(), type.build());
        try {
            javaFile.build().writeTo(ctx.filer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new ComponentDeclaration.PromisedProxyComponent(typeElement, ClassName.get(packageElement.getQualifiedName().toString(), resultClassName));
    }

    private static boolean checkCycle(ProcessingContext ctx, ProcessingState.Processing processing, ComponentDeclaration declaration) {
        var prevFrame = processing.resolutionStack().peekLast();
        if (!(prevFrame instanceof ProcessingState.ResolutionFrame.Component prevComponent)) {
            return false;
        }
        if (prevComponent.dependenciesToFind().isEmpty()) {
            return false;
        }
        var dependencyClaim = prevComponent.dependenciesToFind().get(prevComponent.currentDependency());
        var dependencyClaimType = dependencyClaim.type();
        var dependencyClaimTypeElement = ctx.types.asElement(dependencyClaimType);
        assert ctx.types.isAssignable(declaration.type(), dependencyClaim.type()) || ctx.serviceTypeHelper.isAssignableToUnwrapped(declaration.type(), dependencyClaim.type()) || ctx.serviceTypeHelper.isInterceptor(declaration.type());
        for (var inStackFrame : processing.resolutionStack()) {
            if (!(inStackFrame instanceof ProcessingState.ResolutionFrame.Component componentFrame) || componentFrame.declaration() != declaration) {
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
            var alreadyGenerated = GraphResolutionHelper.findDependency(ctx, prevComponent.declaration(), processing.resolvedComponents(), proxyDependencyClaim);
            if (alreadyGenerated != null) {
                processing.resolutionStack().removeLast();
                prevComponent.resolvedDependencies().add(alreadyGenerated);
                processing.resolutionStack().addLast(prevComponent.withCurrentDependency(prevComponent.currentDependency() + 1));
                return true;
            }
            var proxyComponentDeclaration = GraphResolutionHelper.findDependencyDeclarationFromTemplate(
                ctx, declaration, processing.templates(), proxyDependencyClaim
            );
            if (proxyComponentDeclaration == null) {
                proxyComponentDeclaration = generatePromisedProxy(ctx, (TypeElement) dependencyClaimTypeElement);
                if (proxyComponentDeclaration.isTemplate()) {
                    processing.templates().add(proxyComponentDeclaration);
                } else {
                    processing.sourceDeclarations().add(proxyComponentDeclaration);
                }
            }
            var proxyResolvedComponent = new ResolvedComponent(
                processing.resolvedComponents().size(),
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
            processing.resolvedComponents().add(proxyResolvedComponent);
            return true;
        }
        return false;
    }
}
