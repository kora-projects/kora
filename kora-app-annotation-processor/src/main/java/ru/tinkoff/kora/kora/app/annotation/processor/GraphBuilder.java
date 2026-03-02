package ru.tinkoff.kora.kora.app.annotation.processor;

import com.palantir.javapoet.*;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependency;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependencyHelper;
import ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponents;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclarations;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.DeclarationWithIndex;
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
import java.util.function.Predicate;

import static ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim.DependencyClaimType.*;

public class GraphBuilder {
    private final ProcessingContext ctx;
    private final RoundEnvironment roundEnv;
    private final TypeElement root;
    private final List<TypeElement> allModules;
    private final List<ComponentDeclaration> templates;
    private final List<ComponentDeclaration> rootSet;

    private final ResolvedComponents resolvedComponents;
    private final Deque<ResolutionFrame> stack;
    private final ComponentDeclarations declarations;


    public GraphBuilder(ProcessingContext ctx, RoundEnvironment roundEnv, TypeElement root, List<TypeElement> allModules, List<ComponentDeclaration> sourceDeclarations, List<ComponentDeclaration> templates, List<ComponentDeclaration> rootSet) {
        this.ctx = ctx;
        this.roundEnv = roundEnv;
        this.root = root;
        this.allModules = allModules;
        this.templates = templates;
        this.rootSet = rootSet;
        this.stack = new ArrayDeque<>();

        for (var rootDeclaration : rootSet) {
            var rootDeclarationIdx = sourceDeclarations.indexOf(rootDeclaration);
            this.stack.push(new ResolutionFrame.Root(rootDeclaration, rootDeclarationIdx));
        }
        this.declarations = new ComponentDeclarations(ctx);
        for (var sourceDeclaration : sourceDeclarations) {
            this.declarations.add(sourceDeclaration);
        }
        this.resolvedComponents = new ResolvedComponents();
    }

    public GraphBuilder(GraphBuilder from) {
        this.ctx = from.ctx;
        this.roundEnv = from.roundEnv;
        this.root = from.root;
        this.allModules = from.allModules;
        this.templates = new ArrayList<>(from.templates);
        this.rootSet = from.rootSet;
        this.stack = new ArrayDeque<>(from.stack);
        this.resolvedComponents = new ResolvedComponents(from.resolvedComponents);
        this.declarations = new ComponentDeclarations(from.declarations);
    }

    public sealed interface ResolutionFrame {
        record Root(ComponentDeclaration componentDeclaration, int componentDeclarationIdx) implements ResolutionFrame {}

        record Component(ComponentDeclaration declaration, int declarationIdx, List<DependencyClaim> dependenciesToFind, List<ComponentDependency> resolvedDependencies,
                         int currentDependency) implements ResolutionFrame {
            public Component(ComponentDeclaration declaration, int declarationIdx, List<DependencyClaim> dependenciesToFind) {
                this(declaration, declarationIdx, dependenciesToFind, new ArrayList<>(dependenciesToFind.size()), 0);
            }

            public Component withCurrentDependency(int currentDependency) {
                return new Component(declaration, declarationIdx, dependenciesToFind, resolvedDependencies, currentDependency);
            }
        }
    }


    public ResolvedGraph build() {
        if (rootSet.isEmpty()) {
            throw new ProcessingErrorException(
                "@KoraApp has no root components, expected at least one declaration annotated with @Root",
                root
            );
        }
        frame:
        while (!stack.isEmpty()) {
            var frame = stack.removeLast();
            if (frame instanceof ResolutionFrame.Root(var decl, var declIdx)) {
                if (resolvedComponents.getByDeclarationIndex(declIdx) != null) {
                    // resolved
                    continue;
                }
                stack.add(new ResolutionFrame.Component(
                    decl, declIdx, ComponentDependencyHelper.parseDependencyClaims(ctx, decl)
                ));
                stack.addAll(findInterceptors(ctx, resolvedComponents, stack, decl));
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
                var dependencyDeclarations = GraphResolutionHelper.findDependencyDeclarations(ctx, this.declarations, dependencyClaim);
                if (!dependencyDeclarations.isEmpty()) {
                    final DeclarationWithIndex dependencyDeclaration;
                    if (dependencyDeclarations.size() == 1) {
                        dependencyDeclaration = dependencyDeclarations.getFirst();
                    } else {
                        var exactMatch = dependencyDeclarations.stream()
                            .filter(d -> ctx.types.isSameType(d.declaration().type(), dependencyClaim.type()) || ctx.serviceTypeHelper.isSameToUnwrapped(d.declaration().type(), dependencyClaim.type()))
                            .toList();
                        if (exactMatch.size() == 1) {
                            dependencyDeclaration = exactMatch.getFirst();
                        } else {
                            var nonDefaultComponents = dependencyDeclarations.stream()
                                .filter(Predicate.not(d -> d.declaration().isDefault()))
                                .toList();
                            if (nonDefaultComponents.size() == 1) {
                                dependencyDeclaration = nonDefaultComponents.getFirst();
                            } else {
                                throw new DuplicateDependencyException(dependencyClaim, declaration, dependencyDeclarations.stream().map(DeclarationWithIndex::declaration).toList());
                            }
                        }
                    }
                    var resolved = resolvedComponents.getByDeclaration(dependencyDeclaration);
                    if (resolved != null) {
                        resolvedDependencies.add(GraphResolutionHelper.toDependency(ctx, resolved, dependencyClaim));
                        continue dependency;
                    } else {
                        this.addResolveComponentFrame(componentFrame.withCurrentDependency(currentDependency), dependencyDeclaration);
                        continue frame;
                    }
                }
                var matchingTemplates = GraphResolutionHelper.findDependencyDeclarationsFromTemplate(ctx, declaration, templates, dependencyClaim);
                if (!matchingTemplates.isEmpty()) {
                    if (matchingTemplates.size() == 1) {
                        var template = matchingTemplates.getFirst();
                        var idx = this.declarations.add(template);
                        this.addResolveComponentFrame(componentFrame.withCurrentDependency(currentDependency), new DeclarationWithIndex(template, idx));
                        continue frame;
                    }
                    UnresolvedDependencyException exception = null;
                    var results = new ArrayList<ResolvedGraph>(matchingTemplates.size());
                    for (var template : matchingTemplates) {
                        var fork = new GraphBuilder(this);
                        var idx = fork.declarations.add(template);
                        fork.addResolveComponentFrame(componentFrame.withCurrentDependency(currentDependency), new DeclarationWithIndex(template, idx));

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
                    var declIdx = this.declarations.add(optionalDeclaration);
                    stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                    stack.addLast(new ResolutionFrame.Component(
                        optionalDeclaration, declIdx, List.of(ComponentDependencyHelper.parseClaim(componentFrame.declaration().source(), ((DeclaredType) dependencyClaim.type()).getTypeArguments().get(0), dependencyClaim.tag(), true))
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
                    var extensionComponent = switch (extensionResult) {
                        case ExtensionResult.CodeBlockResult codeBlockResult -> ComponentDeclaration.fromExtension(codeBlockResult);
                        case ExtensionResult.GeneratedResult generated -> ComponentDeclaration.fromExtension(ctx, generated);
                    };
                    if (extensionComponent.isTemplate()) {
                        templates.add(extensionComponent);
                    } else {
                        this.declarations.add(extensionComponent);
                    }
                    stack.addLast(componentFrame.withCurrentDependency(currentDependency));
                    continue frame;
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

            resolvedComponents.add(componentFrame.declarationIdx, declaration, resolvedDependencies);

        }
        return new ResolvedGraph(root, allModules, declarations, resolvedComponents);
    }

    private void addResolveComponentFrame(ResolutionFrame.Component currentFrame, DeclarationWithIndex declaration) {
        stack.addLast(currentFrame);
        stack.addLast(new ResolutionFrame.Component(
            declaration.declaration(), declaration.index(), ComponentDependencyHelper.parseDependencyClaims(ctx, declaration.declaration())
        ));
        stack.addAll(findInterceptors(ctx, resolvedComponents, stack, declaration.declaration()));
    }

    @Nullable
    private ComponentDependency processAllOf(ResolutionFrame.Component componentFrame, int currentDependency) {
        var dependencyClaim = componentFrame.dependenciesToFind().get(currentDependency);
        var dependencies = GraphResolutionHelper.findDependencyDeclarations(ctx, declarations, dependencyClaim);
        for (var dependency : dependencies) {
            if (dependency.declaration().isDefault()) {
                continue;
            }
            var resolved = resolvedComponents.getByDeclaration(dependency);
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

    private List<ResolutionFrame.Component> findInterceptors(ProcessingContext ctx, ResolvedComponents resolvedComponents, Deque<ResolutionFrame> resolutionStack, ComponentDeclaration declaration) {
        return GraphResolutionHelper.findInterceptorDeclarations(ctx, this.declarations.interceptors(), declaration.type())
            .stream()
            .filter(declarationWithIndex -> resolvedComponents.getByDeclaration(declarationWithIndex) == null && resolutionStack.stream().noneMatch(rf -> rf instanceof ResolutionFrame.Component c && c.declarationIdx == declarationWithIndex.index()))
            .map(declarationWithIndex -> new ResolutionFrame.Component(declarationWithIndex.declaration(), declarationWithIndex.index(), ComponentDependencyHelper.parseDependencyClaims(ctx, declarationWithIndex.declaration())))
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
            var declarations = GraphResolutionHelper.findDependencyDeclarations(ctx, this.declarations, proxyDependencyClaim);
            if (!declarations.isEmpty()) {
                if (declarations.size() > 1) {
                    throw new IllegalStateException();
                }
                var decl = declarations.getFirst();
                var resolved = resolvedComponents.getByDeclaration(decl);
                if (resolved == null) {
                    throw new IllegalStateException();
                }
                stack.removeLast();
                prevComponent.resolvedDependencies().add(GraphResolutionHelper.toDependency(ctx, resolved, dependencyClaim));
                stack.addLast(prevComponent.withCurrentDependency(prevComponent.currentDependency() + 1));
                return true;
            }
            var proxyComponentDeclarations = GraphResolutionHelper.findDependencyDeclarationsFromTemplate(
                ctx, declaration, templates, proxyDependencyClaim
            );
            final int declIdx;
            final ComponentDeclaration proxyComponentDeclaration;
            if (proxyComponentDeclarations.isEmpty()) {
                var generatedDeclaration = generatePromisedProxy(ctx, (TypeElement) dependencyClaimTypeElement);
                if (generatedDeclaration.isTemplate()) {
                    templates.add(generatedDeclaration);
                    proxyComponentDeclarations = GraphResolutionHelper.findDependencyDeclarationsFromTemplate(
                        ctx, declaration, templates, proxyDependencyClaim
                    );
                    if (proxyComponentDeclarations.size() != 1) {
                        throw new IllegalStateException();
                    }
                    proxyComponentDeclaration = proxyComponentDeclarations.getFirst();
                    declIdx = this.declarations.add(proxyComponentDeclaration);
                } else {
                    proxyComponentDeclaration = generatedDeclaration;
                    declIdx = this.declarations.add(generatedDeclaration);
                }
            } else {
                if (proxyComponentDeclarations.size() > 1) {
                    throw new IllegalStateException();
                }
                proxyComponentDeclaration = proxyComponentDeclarations.getFirst();
                declIdx = this.declarations.add(proxyComponentDeclaration);
            }
            resolvedComponents.add(declIdx, proxyComponentDeclaration, List.of(new ComponentDependency.PromisedProxyParameterDependency(declaration, new DependencyClaim(
                declaration.type(),
                declaration.tag(),
                ONE_REQUIRED
            ))));
            return true;
        }
        return false;
    }
}
