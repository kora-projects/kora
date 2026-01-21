package ru.tinkoff.kora.kora.app.annotation.processor;

import com.palantir.javapoet.*;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependency;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependencyHelper;
import ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponent;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;
import ru.tinkoff.kora.kora.app.annotation.processor.exception.CircularDependencyException;
import ru.tinkoff.kora.kora.app.annotation.processor.exception.DuplicateDependencyException;
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

import static ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim.DependencyClaimType.*;

public class GraphBuilder {
    private final ProcessingContext ctx;
    private final RoundEnvironment roundEnv;
    private final TypeElement root;
    private final List<TypeElement> allModules;
    private final List<ComponentDeclaration> sourceDeclarations;
    private final List<ComponentDeclaration> templates;
    private final List<ComponentDeclaration> rootSet;

    private final List<ResolvedComponent> resolvedComponents;
    private final Deque<ResolutionFrame> stack;

    // todo we should have fast matching map here for resolved components and non template components here

    public GraphBuilder(ProcessingContext ctx, RoundEnvironment roundEnv, TypeElement root, List<TypeElement> allModules, List<ComponentDeclaration> sourceDeclarations, List<ComponentDeclaration> templates, List<ComponentDeclaration> rootSet) {
        this.ctx = ctx;
        this.roundEnv = roundEnv;
        this.root = root;
        this.allModules = allModules;
        this.sourceDeclarations = sourceDeclarations;
        this.templates = templates;
        this.rootSet = rootSet;
        this.stack = new ArrayDeque<>();
        this.resolvedComponents = new ArrayList<>();

        for (int i = 0; i < rootSet.size(); i++) {
            this.stack.push(new ResolutionFrame.Root(i, rootSet.get(i)));
        }
    }

    public GraphBuilder(GraphBuilder from) {
        this.ctx = from.ctx;
        this.roundEnv = from.roundEnv;
        this.root = from.root;
        this.allModules = from.allModules;
        this.sourceDeclarations = new ArrayList<>(from.sourceDeclarations);
        this.templates = new ArrayList<>(from.templates);
        this.rootSet = from.rootSet;
        this.stack = new ArrayDeque<>(from.stack);
        this.resolvedComponents = new ArrayList<>(from.resolvedComponents);

    }

    public sealed interface ResolutionFrame {
        record Root(int rootIndex, ComponentDeclaration componentDeclaration) implements ResolutionFrame {}

        record Component(ComponentDeclaration declaration, List<DependencyClaim> dependenciesToFind, List<ComponentDependency> resolvedDependencies, int currentDependency) implements ResolutionFrame {
            public Component(ComponentDeclaration declaration, List<DependencyClaim> dependenciesToFind) {
                this(declaration, dependenciesToFind, new ArrayList<>(dependenciesToFind.size()), 0);
            }

            public Component withCurrentDependency(int currentDependency) {
                return new Component(declaration, dependenciesToFind, resolvedDependencies, currentDependency);
            }
        }
    }


    public ResolvedGraph build() {
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
            if (checkCycle(declaration)) {
                continue;
            }

            dependency:
            for (int currentDependency = componentFrame.currentDependency(); currentDependency < dependenciesToFind.size(); currentDependency++) {
                var dependencyClaim = dependenciesToFind.get(currentDependency);
                if (dependencyClaim.claimType() == ALL_OF_ONE || dependencyClaim.claimType() == ALL_OF_PROMISE || dependencyClaim.claimType() == ALL_OF_VALUE) {
                    var allOfDependency = processAllOf(componentFrame, currentDependency);
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
                    this.addResolveComponentFrame(componentFrame.withCurrentDependency(currentDependency), dependencyDeclaration);
                    continue frame;
                }
                var matchingTemplates = GraphResolutionHelper.findDependencyDeclarationsFromTemplate(ctx, declaration, templates, dependencyClaim);
                if (!matchingTemplates.isEmpty()) {
                    if (matchingTemplates.size() == 1) {
                        var template = matchingTemplates.get(0);
                        sourceDeclarations.add(template);
                        this.addResolveComponentFrame(componentFrame.withCurrentDependency(currentDependency), template);
                        continue frame;
                    }
                    UnresolvedDependencyException exception = null;
                    var results = new ArrayList<ResolvedGraph>(matchingTemplates.size());
                    for (var template : matchingTemplates) {
                        var fork = new GraphBuilder(this);
                        fork.sourceDeclarations.add(template);
                        fork.addResolveComponentFrame(componentFrame.withCurrentDependency(currentDependency), template);

                        try {
                            results.add(fork.build());
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
                        throw new DuplicateDependencyException(dependencyClaim, declaration, templates);
                    }
                    throw exception;
                }
                if (dependencyClaim.claimType().isNullable()) {
                    resolvedDependencies.add(new ComponentDependency.NullDependency(dependencyClaim));
                    continue dependency;
                }
                if (dependencyClaim.type().toString().startsWith("java.util.Optional<")) {
                    var optionalDeclaration = new ComponentDeclaration.OptionalComponent(dependencyClaim.type(), dependencyClaim.tag());
                    sourceDeclarations.add(optionalDeclaration);
                    stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                    stack.addLast(new ResolutionFrame.Component(
                        optionalDeclaration, List.of(ComponentDependencyHelper.parseClaim(componentFrame.declaration().source(), ((DeclaredType) dependencyClaim.type()).getTypeArguments().get(0), dependencyClaim.tag(), true))
                    ));
                    continue frame;
                }
                var extension = ctx.extensions.findExtension(roundEnv, dependencyClaim.type(), dependencyClaim.tag());
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
                var hints = ctx.dependencyModuleHintProvider.findHints(dependencyClaim.type(), dependencyClaim.tag());

                throw new UnresolvedDependencyException(
                    root,
                    declaration,
                    dependencyClaim,
                    new ArrayDeque<>(stack),
                    hints
                );
            }
            resolvedComponents.add(new ResolvedComponent(
                resolvedComponents.size(), declaration, declaration.type(), declaration.tag(),
                List.of(), // TODO,
                resolvedDependencies
            ));
        }
        return new ResolvedGraph(root, allModules, resolvedComponents);
    }

    private void addResolveComponentFrame(ResolutionFrame.Component currentFrame, ComponentDeclaration declaration) {
        stack.addLast(currentFrame);
        stack.addLast(new ResolutionFrame.Component(
            declaration, ComponentDependencyHelper.parseDependencyClaims(ctx, declaration)
        ));
        stack.addAll(findInterceptors(ctx, resolvedComponents, stack, declaration));
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
    private ComponentDependency processAllOf(ResolutionFrame.Component componentFrame, int currentDependency) {
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
            addResolveComponentFrame(componentFrame.withCurrentDependency(currentDependency), dependency);
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

    private boolean checkCycle(ComponentDeclaration declaration) {
        var prevFrame = stack.peekLast();
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
            throw new CircularDependencyException(List.of(prevComponent.declaration(), declaration), declaration);
        }
        for (var inStackFrame : stack) {
            if (!(inStackFrame instanceof ResolutionFrame.Component componentFrame) || componentFrame.declaration() != declaration) {
                continue;
            }
            if (dependencyClaim.type().getKind() != TypeKind.DECLARED) {
                throw new CircularDependencyException(List.of(prevComponent.declaration(), declaration), componentFrame.declaration());
            }
            if (dependencyClaimTypeElement.getKind() != ElementKind.INTERFACE && (dependencyClaimTypeElement.getKind() != ElementKind.CLASS || dependencyClaimTypeElement.getModifiers().contains(Modifier.FINAL))) {
                throw new CircularDependencyException(List.of(prevComponent.declaration(), declaration), componentFrame.declaration());
            }
            var proxyDependencyClaim = new DependencyClaim(
                dependencyClaimType, CommonClassNames.promisedProxy.canonicalName(), dependencyClaim.claimType()
            );
            var alreadyGenerated = GraphResolutionHelper.findDependency(ctx, prevComponent.declaration(), resolvedComponents, proxyDependencyClaim);
            if (alreadyGenerated != null) {
                stack.removeLast();
                prevComponent.resolvedDependencies().add(alreadyGenerated);
                stack.addLast(prevComponent.withCurrentDependency(prevComponent.currentDependency() + 1));
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
                CommonClassNames.promisedProxy.canonicalName(),
                List.of(),
                List.of(new ComponentDependency.PromisedProxyParameterDependency(declaration, new DependencyClaim(
                    declaration.type(),
                    declaration.tag(),
                    ONE_REQUIRED
                )))
            );
            resolvedComponents.add(proxyResolvedComponent);
            return true;
        }
        return false;
    }
}
