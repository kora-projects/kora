package io.koraframework.kora.app.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.kora.app.annotation.processor.component.*;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclarations;
import io.koraframework.kora.app.annotation.processor.declaration.DeclarationWithIndex;
import io.koraframework.kora.app.annotation.processor.exception.CircularDependencyException;
import io.koraframework.kora.app.annotation.processor.exception.DependencySourceFormatter;
import io.koraframework.kora.app.annotation.processor.exception.DuplicateDependencyException;
import io.koraframework.kora.app.annotation.processor.exception.UnresolvedDependencyException;
import io.koraframework.kora.app.annotation.processor.extension.ExtensionResult;
import org.jspecify.annotations.Nullable;

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

import static io.koraframework.kora.app.annotation.processor.component.DependencyClaim.DependencyClaimType.*;

public class GraphBuilder {
    private final ProcessingContext ctx;
    private final RoundEnvironment roundEnv;
    private final TypeElement root;
    private final List<TypeElement> allModules;
    private final List<ComponentDeclaration> templates;
    private final List<DeclarationWithIndex> rootSet;

    private final ResolvedComponents resolvedComponents;
    private final Deque<ResolutionFrame> stack;
    private final ComponentDeclarations declarations;
    private final Map<ClassName, ResolvedComponent> conditionByTag;
    private int[] stackCounts = new int[128];

    public GraphBuilder(ProcessingContext ctx, RoundEnvironment roundEnv, TypeElement root, List<TypeElement> allModules, List<ComponentDeclaration> sourceDeclarations, List<ComponentDeclaration> templates) {
        this.ctx = ctx;
        this.roundEnv = roundEnv;
        this.root = root;
        this.allModules = allModules;
        this.templates = templates;
        this.rootSet = new ArrayList<>();
        this.stack = new ArrayDeque<>();

        for (int i = 0; i < sourceDeclarations.size(); i++) {
            var maybeRoot = sourceDeclarations.get(i);
            var isRoot = AnnotationUtils.isAnnotationPresent(maybeRoot.source(), CommonClassNames.root)
                || maybeRoot instanceof ComponentDeclaration.AnnotatedComponent ac && AnnotationUtils.isAnnotationPresent(ac.typeElement(), CommonClassNames.root);
            if (isRoot) {
                this.stack.push(new ResolutionFrame.Root(maybeRoot, i));
                this.rootSet.add(new DeclarationWithIndex(maybeRoot, i));
            }
        }
        this.declarations = new ComponentDeclarations(ctx);
        for (var sourceDeclaration : sourceDeclarations) {
            this.declarations.add(sourceDeclaration);
        }
        this.resolvedComponents = new ResolvedComponents();
        this.conditionByTag = new HashMap<>();
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
        this.conditionByTag = new HashMap<>(from.conditionByTag);
        this.stackCounts = Arrays.copyOf(from.stackCounts, from.stackCounts.length);
    }

    private void incStack(int idx) {
        if (idx >= stackCounts.length) {
            stackCounts = Arrays.copyOf(stackCounts, Math.max(idx + 1, stackCounts.length * 2));
        }
        stackCounts[idx]++;
    }

    private void decStack(int idx) {
        if (idx < stackCounts.length && stackCounts[idx] > 0) {
            stackCounts[idx]--;
        }
    }

    private int getStackCount(int idx) {
        return idx < stackCounts.length ? stackCounts[idx] : 0;
    }

    public sealed interface ResolutionFrame {
        int declarationIdx();

        record Root(ComponentDeclaration componentDeclaration, int componentDeclarationIdx) implements ResolutionFrame {
            @Override
            public int declarationIdx() {
                return componentDeclarationIdx;
            }
        }

        final class Component implements ResolutionFrame {
            private final ComponentDeclaration declaration;
            private final int declarationIdx;
            private final List<DependencyClaim> dependenciesToFind;
            private final List<ComponentDependency> resolvedDependencies;
            private int currentDependency;

            public Component(ComponentDeclaration declaration, int declarationIdx, List<DependencyClaim> dependenciesToFind) {
                this.declaration = declaration;
                this.declarationIdx = declarationIdx;
                this.dependenciesToFind = dependenciesToFind;
                this.resolvedDependencies = new ArrayList<>(dependenciesToFind.size());
                this.currentDependency = 0;
            }

            public ComponentDeclaration declaration() { return declaration; }
            @Override
            public int declarationIdx() { return declarationIdx; }
            public List<DependencyClaim> dependenciesToFind() { return dependenciesToFind; }
            public List<ComponentDependency> resolvedDependencies() { return resolvedDependencies; }

            public int currentDependency() { return currentDependency; }
            public void setCurrentDependency(int currentDependency) { this.currentDependency = currentDependency; }
        }
    }

    public ResolvedGraph build() {
        if (rootSet.isEmpty()) {
            throw new ProcessingErrorException(
                """
                @KoraApp has no root components.

                Fix:
                - Check that modules with @Root components are plugged-in.
                - Annotate at least one component or module method with @Root.
                - Check that root component is visible from this @KoraApp module set.
                """.stripTrailing(),
                root
            );
        }
        frame:
        while (!stack.isEmpty()) {
            var frame = stack.removeLast();
            decStack(frame.declarationIdx());

            if (frame instanceof ResolutionFrame.Root(var decl, var declIdx)) {
                if (resolvedComponents.getByDeclarationIndex(declIdx) != null) {
                    // resolved
                    continue;
                }

                var newFrame = new ResolutionFrame.Component(
                    decl, declIdx, ComponentDependencyHelper.parseDependencyClaims(ctx, decl)
                );
                incStack(newFrame.declarationIdx());
                stack.addLast(newFrame);
                var interceptors = findInterceptors(ctx, resolvedComponents, decl);
                for (int i = 0; i < interceptors.size(); i++) {
                    var interceptor = interceptors.get(i);
                    incStack(interceptor.declarationIdx());
                    stack.addLast(interceptor);
                }
                continue;
            }

            var componentFrame = (ResolutionFrame.Component) frame;
            var declaration = componentFrame.declaration();
            var dependenciesToFind = componentFrame.dependenciesToFind();
            var resolvedDependencies = componentFrame.resolvedDependencies();
            if (checkCycle(declaration, componentFrame.declarationIdx())) {
                breakCycleWithProxy(declaration);
                continue;
            }
            var componentCondition = declaration.condition();
            condition:
            if (componentCondition != null) {
                var resolvedCondition = this.conditionByTag.get(componentCondition);
                if (resolvedCondition != null) {
                    break condition;
                }
                var conditionDeclarations = declarations.getByType(CommonClassNames.graphCondition)
                    .stream()
                    .filter(d -> componentCondition.canonicalName().equals(d.declaration().tag()))
                    .toList();
                if (conditionDeclarations.isEmpty()) {
                    throw new ProcessingErrorException("""
                        Component condition cannot be resolved:
                          required condition tag: @Tag(%s.class)
                          component: %s

                        Fix:
                          - Add a GraphCondition component with this tag.
                          - Include a module that provides this GraphCondition.
                          - Check that @Conditional uses the intended tag.
                        """.formatted(componentCondition, declaration.declarationString()).stripTrailing(), declaration.source());
                }
                if (conditionDeclarations.size() > 1) {
                    var str = conditionDeclarations.stream().map(d -> "  - " + d.declaration().declarationString()).collect(Collectors.joining("\n"));
                    throw new ProcessingErrorException("""
                        Multiple GraphCondition components match condition tag:
                          required condition tag: @Tag(%s.class)
                          component: %s

                        Candidates:
                        %s

                        Fix:
                          - Keep only one GraphCondition for this tag.
                          - Use different @Tag(...) values for different conditions.
                        """.formatted(componentCondition, declaration.declarationString(), str).stripTrailing(), declaration.source());
                }
                var conditionDeclaration = conditionDeclarations.getFirst();
                resolvedCondition = this.resolvedComponents.getByDeclaration(conditionDeclaration);
                if (resolvedCondition == null) {
                    this.addResolveComponentFrame(componentFrame, conditionDeclaration);
                    continue frame;
                } else {
                    this.conditionByTag.put(componentCondition, resolvedCondition);
                }
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
                if (dependencyClaim.claimType() == GRAPH) {
                    resolvedDependencies.add(new ComponentDependency.GraphDependency(dependencyClaim));
                    continue dependency;
                }
                var dependencyDeclarations = GraphResolutionHelper.findDependencyDeclarations(ctx, this.declarations, dependencyClaim);
                if (!dependencyDeclarations.isEmpty()) {
                    final DeclarationWithIndex dependencyDeclaration;
                    if (dependencyDeclarations.size() == 1) {
                        dependencyDeclaration = dependencyDeclarations.getFirst();
                    } else {
                        var nonDefaultComponents = new ArrayList<DeclarationWithIndex>(dependencyDeclarations.size());
                        for (int i = 0; i < dependencyDeclarations.size(); i++) {
                            var d = dependencyDeclarations.get(i);
                            if (!d.declaration().isDefault()) {
                                nonDefaultComponents.add(d);
                            }
                        }
                        if (nonDefaultComponents.size() == 1) {
                            dependencyDeclaration = nonDefaultComponents.getFirst();
                        } else {
                            boolean allConditional = true;
                            for (int i = 0; i < nonDefaultComponents.size(); i++) {
                                if (nonDefaultComponents.get(i).declaration().condition() == null) {
                                    allConditional = false;
                                    break;
                                }
                            }
                            if (allConditional) {
                                var resolvedConditional = new ArrayList<ResolvedComponent>(nonDefaultComponents.size());
                                for (int i = 0; i < nonDefaultComponents.size(); i++) {
                                    var nonDefaultComponent = nonDefaultComponents.get(i);
                                    var resolved = resolvedComponents.getByDeclaration(nonDefaultComponent);
                                    if (resolved != null) {
                                        resolvedConditional.add(resolved);
                                    } else {
                                        componentFrame.setCurrentDependency(currentDependency);
                                        this.addResolveComponentFrame(componentFrame, nonDefaultComponent);
                                        continue frame;
                                    }
                                }
                                resolvedDependencies.add(GraphResolutionHelper.toOneOfDependency(ctx, resolvedConditional, dependencyClaim));
                                continue dependency;
                            }
                            throw new DuplicateDependencyException(ctx.elements, dependencyClaim, declaration, dependencyDeclarations.stream().map(DeclarationWithIndex::declaration).toList());
                        }
                    }
                    var resolved = resolvedComponents.getByDeclaration(dependencyDeclaration);
                    if (resolved != null) {
                        resolvedDependencies.add(GraphResolutionHelper.toDependency(ctx, resolved, dependencyClaim));
                        continue dependency;
                    } else {
                        componentFrame.setCurrentDependency(currentDependency);
                        this.addResolveComponentFrame(componentFrame, dependencyDeclaration);
                        continue frame;
                    }
                }
                var matchingTemplates = GraphResolutionHelper.findDependencyDeclarationsFromTemplate(ctx, declaration, templates, dependencyClaim);
                if (!matchingTemplates.isEmpty()) {
                    if (matchingTemplates.size() == 1) {
                        var template = matchingTemplates.getFirst();
                        var idx = this.declarations.add(template);
                        componentFrame.setCurrentDependency(currentDependency);
                        this.addResolveComponentFrame(componentFrame, new DeclarationWithIndex(template, idx));
                        continue frame;
                    }
                    UnresolvedDependencyException exception = null;
                    var results = new ArrayList<ResolvedGraph>(matchingTemplates.size());
                    var resolvedTemplates = new ArrayList<ComponentDeclaration>(matchingTemplates.size());
                    for (var template : matchingTemplates) {
                        var fork = new GraphBuilder(this);
                        var idx = fork.declarations.add(template);
                        componentFrame.setCurrentDependency(currentDependency);
                        fork.addResolveComponentFrame(componentFrame, new DeclarationWithIndex(template, idx));

                        try {
                            results.add(fork.build());
                            resolvedTemplates.add(template);
                        } catch (UnresolvedDependencyException e) {
                            if (exception != null) {
                                exception.addSuppressed(e);
                            } else {
                                exception = e;
                            }
                        }
                    }
                    if (results.size() == 1) {
                        return results.getFirst();
                    }
                    if (results.size() > 1) {
                        throw new DuplicateDependencyException(ctx.elements, dependencyClaim, declaration, resolvedTemplates);
                    }
                    throw exception;
                }
                if (dependencyClaim.claimType().isNullable()) {
                    resolvedDependencies.add(new ComponentDependency.NullDependency(dependencyClaim));
                    continue dependency;
                }
                if (dependencyClaim.type().getKind() == javax.lang.model.type.TypeKind.DECLARED) {
                    var declaredType = (DeclaredType) dependencyClaim.type();
                    var element = (TypeElement) declaredType.asElement();
                    if (element.getQualifiedName().contentEquals("java.util.Optional")) {
                        var optionalDeclaration = new ComponentDeclaration.OptionalComponent(dependencyClaim.type(), dependencyClaim.tag());
                        var declIdx = this.declarations.add(optionalDeclaration);

                        componentFrame.setCurrentDependency(currentDependency);
                        stack.addLast(componentFrame);
                        incStack(componentFrame.declarationIdx());

                        var nextFrame = new ResolutionFrame.Component(
                            optionalDeclaration, declIdx, List.of(ComponentDependencyHelper.parseClaim(componentFrame.declaration().source(), declaredType.getTypeArguments().get(0), dependencyClaim.tag(), true))
                        );
                        incStack(nextFrame.declarationIdx());
                        stack.addLast(nextFrame);
                        continue frame;
                    }
                }
                var extension = ctx.extensions.findExtension(roundEnv, dependencyClaim.type(), dependencyClaim.tag());
                if (extension != null) {
                    ExtensionResult extensionResult;
                    try {
                        extensionResult = Objects.requireNonNull(extension.generateDependency());
                    } catch (IOException e) {
                        throw new ProcessingErrorException("""
                            Extension failed to generate dependency:
                              dependency: %s
                              cause:      %s%s

                            Fix:
                              - Check earlier errors from the extension annotation processor.
                              - If no earlier errors exist, report this as a Kora extension bug.
                            """.formatted(
                            DependencySourceFormatter.type(dependencyClaim.type()),
                            e,
                            DependencySourceFormatter.requiredAtSection(declaration, dependencyClaim.source())
                        ).stripTrailing(), dependencyClaim.source() == null ? declaration.source() : dependencyClaim.source());
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
                    componentFrame.setCurrentDependency(currentDependency);
                    stack.addLast(componentFrame);
                    incStack(componentFrame.declarationIdx());
                    continue frame;
                }
                var hints = ctx.dependencyModuleHintProvider.findHints(dependencyClaim.type(), dependencyClaim.tag());
                var sameTypeDifferentTag = GraphResolutionHelper.findSameTypeDeclarationsWithDifferentTags(ctx, this.declarations, dependencyClaim);

                throw new UnresolvedDependencyException(
                    ctx.elements,
                    root,
                    declaration,
                    dependencyClaim,
                    new ArrayDeque<>(stack),
                    hints,
                    sameTypeDifferentTag
                );
            }

            resolvedComponents.add(componentFrame.declarationIdx, declaration, resolvedDependencies);
        }
        for (var component : resolvedComponents.components()) {
            for (var dependency : component.dependencies()) {
                if (dependency instanceof ComponentDependency.AllOfDependency allOf) {
                    var dependencyDeclarations = GraphResolutionHelper.findDependencyDeclarations(ctx, declarations, allOf.claim());
                    var dependencies = GraphResolutionHelper.findDependenciesForAllOf(ctx, allOf.claim(), dependencyDeclarations, resolvedComponents);
                    for (var resolvedDependency : dependencies) {
                        if (resolvedDependency.component().index() > component.index()) {
                            throw new ProcessingErrorException(
                                """
                                    All<T> dependency appeared in graph after the component that requests it.

                                    This is an internal graph ordering limitation.

                                    Fix:
                                      - Move the component that provides the All<T> item so it is reachable before the requesting component.
                                      - If this graph should be valid, please report this as a Kora bug with the dependency path.
                                    """.stripTrailing(),
                                resolvedDependency.component().declaration().source()
                            );
                        }
                    }
                    allOf.addResolved(dependencies);
                }
                if (dependency instanceof ComponentDependency.PromisedProxyParameterDependency proxy) {
                    var componentDeclarations = GraphResolutionHelper.findDependencyDeclarations(ctx, declarations, proxy.claim());
                    if (componentDeclarations.size() != 1) {
                        throw new IllegalStateException("Kora internal error: promised proxy dependency expected exactly one target declaration, got " + componentDeclarations.size() + " for " + proxy.claim());
                    }
                    var realDependency = Objects.requireNonNull(resolvedComponents.getByDeclaration(componentDeclarations.getFirst()));
                    proxy.setPromised(realDependency);
                }
            }
        }
        // todo All<T> may appear earlier than some indirectly resolved DefaultComponent components, we can try to reorder graph here

        // we should move conditions as early in graph as possible
        this.resolvedComponents.processConditions(this.conditionByTag);
        for (var component : this.resolvedComponents.components().reversed()) {
            component.processCondition();
        }

        return new ResolvedGraph(root, allModules, this.resolvedComponents.components(), conditionByTag);
    }

    private void addResolveComponentFrame(ResolutionFrame.Component currentFrame, DeclarationWithIndex declaration) {
        incStack(currentFrame.declarationIdx());
        stack.addLast(currentFrame);
        var newFrame = new ResolutionFrame.Component(
            declaration.declaration(),
            declaration.index(),
            ComponentDependencyHelper.parseDependencyClaims(ctx, declaration.declaration())
        );
        incStack(newFrame.declarationIdx());
        stack.addLast(newFrame);

        var interceptors = findInterceptors(ctx, resolvedComponents, declaration.declaration());
        for (int i = 0; i < interceptors.size(); i++) {
            var interceptor = interceptors.get(i);
            incStack(interceptor.declarationIdx());
            stack.addLast(interceptor);
        }
    }

    @Nullable
    private ComponentDependency processAllOf(ResolutionFrame.Component componentFrame, int currentDependency) {
        var dependencyClaim = componentFrame.dependenciesToFind().get(currentDependency);
        var dependencies = GraphResolutionHelper.findDependencyDeclarations(ctx, declarations, dependencyClaim);
        for (var dependency : dependencies) {
            if (dependency.declaration().isDefault() && dependencies.size() > 1) {
                continue;
            }
            var resolved = resolvedComponents.getByDeclaration(dependency);
            if (resolved != null) {
                continue;
            }
            componentFrame.setCurrentDependency(currentDependency);
            addResolveComponentFrame(componentFrame, dependency);
            return null;
        }
        if (dependencyClaim.claimType() == ALL_OF_ONE || dependencyClaim.claimType() == ALL_OF_VALUE || dependencyClaim.claimType() == ALL_OF_PROMISE) {
            return new ComponentDependency.AllOfDependency(dependencyClaim);
        }
        throw new IllegalStateException("Kora internal error: processAllOf called for non-All dependency claim: " + dependencyClaim);
    }

    private List<ResolutionFrame.Component> findInterceptors(ProcessingContext ctx, ResolvedComponents resolvedComponents, ComponentDeclaration declaration) {
        var interceptorDeclarations = GraphResolutionHelper.findInterceptorDeclarations(ctx, this.declarations.interceptors(), declaration.type());
        var result = new ArrayList<ResolutionFrame.Component>(interceptorDeclarations.size());
        for (int i = 0; i < interceptorDeclarations.size(); i++) {
            var declarationWithIndex = interceptorDeclarations.get(i);
            int idx = declarationWithIndex.index();
            if (resolvedComponents.getByDeclaration(declarationWithIndex) == null && getStackCount(idx) == 0) {
                result.add(new ResolutionFrame.Component(
                    declarationWithIndex.declaration(),
                    idx,
                    ComponentDependencyHelper.parseDependencyClaims(ctx, declarationWithIndex.declaration())
                ));
            }
        }
        return result;
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
            throw new IllegalStateException("Kora internal error: failed to write promised proxy component for " + typeElement.getQualifiedName(), e);
        }
        return new ComponentDeclaration.PromisedProxyComponent(typeElement, ClassName.get(packageElement.getQualifiedName().toString(), resultClassName));
    }

    private CircularDependencyException cycleException(ResolutionFrame.Component cycleStart, ResolutionFrame.Component requester, DependencyClaim claim, String note, @Nullable String fix) {
        // frames from the first occurrence of the component up to the requester form the cycle
        var cycle = new ArrayList<ComponentDeclaration>();
        var inCycle = false;
        for (var frame : stack) {
            if (frame == cycleStart) {
                inCycle = true;
            }
            if (inCycle && frame instanceof ResolutionFrame.Component component) {
                cycle.add(component.declaration());
            }
        }
        cycle.add(cycleStart.declaration());
        return new CircularDependencyException(ctx.elements, cycle, cycleStart.declaration(), requester.declaration(), claim, note, fix);
    }

    private void breakCycleWithProxy(ComponentDeclaration declaration) {
        var prevComponent = (ResolutionFrame.Component) Objects.requireNonNull(stack.peekLast());
        var dependencyClaim = prevComponent.dependenciesToFind().get(prevComponent.currentDependency());
        var dependencyClaimType = dependencyClaim.type();
        var dependencyClaimTypeElement = ctx.types.asElement(dependencyClaimType);

        var proxyDependencyClaim = new DependencyClaim(
            dependencyClaimType, CommonClassNames.promisedProxy.canonicalName(), dependencyClaim.claimType()
        );
        var existingDeclarations = GraphResolutionHelper.findDependencyDeclarations(ctx, this.declarations, proxyDependencyClaim);
        if (!existingDeclarations.isEmpty()) {
            if (existingDeclarations.size() > 1) {
                throw new IllegalStateException("Kora internal error: promised proxy declaration is ambiguous for " + proxyDependencyClaim + ", declarations: " + existingDeclarations);
            }
            var decl = existingDeclarations.getFirst();
            var resolved = resolvedComponents.getByDeclaration(decl);
            if (resolved == null) {
                throw new IllegalStateException("Kora internal error: promised proxy declaration was found but is not resolved: " + decl.declaration().declarationString());
            }
            prevComponent.resolvedDependencies().add(GraphResolutionHelper.toDependency(ctx, resolved, dependencyClaim));
            prevComponent.setCurrentDependency(prevComponent.currentDependency() + 1);
            return;
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
                    throw new IllegalStateException("Kora internal error: generated promised proxy template expected one declaration, got " + proxyComponentDeclarations.size() + " for " + proxyDependencyClaim);
                }
                proxyComponentDeclaration = proxyComponentDeclarations.getFirst();
                declIdx = this.declarations.add(proxyComponentDeclaration);
            } else {
                proxyComponentDeclaration = generatedDeclaration;
                declIdx = this.declarations.add(generatedDeclaration);
            }
        } else {
            if (proxyComponentDeclarations.size() > 1) {
                throw new IllegalStateException("Kora internal error: promised proxy template is ambiguous for " + proxyDependencyClaim + ", declarations: " + proxyComponentDeclarations);
            }
            proxyComponentDeclaration = proxyComponentDeclarations.getFirst();
            declIdx = this.declarations.add(proxyComponentDeclaration);
        }

        resolvedComponents.add(declIdx, proxyComponentDeclaration, List.of(new ComponentDependency.PromisedProxyParameterDependency(declaration, new DependencyClaim(
            declaration.type(),
            declaration.tag(),
            ONE_REQUIRED
        ))));
        var resolvedProxy = Objects.requireNonNull(this.resolvedComponents.getByDeclarationIndex(declIdx));
        prevComponent.resolvedDependencies().add(GraphResolutionHelper.toDependency(ctx, resolvedProxy, dependencyClaim));
        prevComponent.setCurrentDependency(prevComponent.currentDependency() + 1);
    }

    private boolean checkCycle(ComponentDeclaration declaration, int declarationIdx) {
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

        if (!(ctx.types.isAssignable(declaration.type(), dependencyClaimType)
            || ctx.serviceTypeHelper.isAssignableToUnwrapped(declaration.type(), dependencyClaimType)
            || ctx.serviceTypeHelper.isInterceptor(declaration.type())
            || ctx.serviceTypeHelper.isCondition(declaration.type()))) {
            throw new CircularDependencyException(ctx.elements, List.of(prevComponent.declaration(), declaration), declaration, prevComponent.declaration(), dependencyClaim, null, null);
        }

        if (getStackCount(declarationIdx) == 0) {
            return false;
        }

        ResolutionFrame.Component cycleStart = prevComponent;
        for (var inStackFrame : stack) {
            if (inStackFrame instanceof ResolutionFrame.Component componentFrame && componentFrame.declarationIdx() == declarationIdx) {
                cycleStart = componentFrame;
                break;
            }
        }

        if (dependencyClaim.type().getKind() != TypeKind.DECLARED) {
            throw cycleException(cycleStart, prevComponent, dependencyClaim, "Dependency type is not a class or interface, so Kora cannot break the cycle with a proxy.", null);
        }

        if (dependencyClaimTypeElement.getKind() != ElementKind.INTERFACE && (dependencyClaimTypeElement.getKind() != ElementKind.CLASS || dependencyClaimTypeElement.getModifiers().contains(Modifier.FINAL))) {
            throw cycleException(cycleStart, prevComponent, dependencyClaim, "Kora can break a cycle with a proxy only for interface or non-final class dependency, but %s is final.".formatted(DependencySourceFormatter.type(dependencyClaimType)),
                "Depend on an interface implemented by %s instead of the class itself, or make the class non-final, so Kora can break the cycle with a proxy.".formatted(DependencySourceFormatter.type(dependencyClaimType)));
        }

        if (!dependencyClaim.claimType().isProxyable()) {
            throw cycleException(cycleStart, prevComponent, dependencyClaim, "Cycle goes through All<T>, TypeRef<T> or Graph dependency, which cannot be replaced with a proxy.",
                "Use All<ValueOf<T>> or All<PromiseOf<T>> instead of All<T> to get the components lazily.");
        }

        return true;
    }
}
