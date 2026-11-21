package io.koraframework.kora.app.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.kora.app.annotation.processor.component.*;
import io.koraframework.kora.app.annotation.processor.declaration.*;
import io.koraframework.kora.app.annotation.processor.interceptor.*;

import javax.lang.model.element.*;
import java.util.*;
import java.util.function.*;

public class GraphFileGenerator {
    private final ProcessingContext ctx;
    private final Element classElement;
    private final List<TypeElement> allModules;
    private final ComponentInterceptors interceptors;
    private final List<ResolvedComponent> components;
    private final Map<ClassName, ResolvedComponent> conditions;

    private static final int COMPONENTS_PER_PARTITION = 500;

    public GraphFileGenerator(ProcessingContext ctx, Element classElement, List<TypeElement> allModules, ComponentInterceptors interceptors, List<ResolvedComponent> components, Map<ClassName, ResolvedComponent> conditions) {
        this.ctx = ctx;
        this.classElement = classElement;
        this.allModules = allModules;
        this.interceptors = interceptors;
        this.components = components;
        this.conditions = conditions;
    }

    private static final class CacheEntry<T> {
        final T value;
        int count = 0;
        Integer assignedIndex = null;

        CacheEntry(T value) {this.value = value;}
    }

    private static final class PartitionCache {
        final Map<String, CacheEntry<CodeBlock>> types = new LinkedHashMap<>();
        final Map<String, CacheEntry<String>> tags = new LinkedHashMap<>();
    }

    private PartitionCache buildPartitionCache(ClassName typeRefClassName, int startIdx, int endIdx) {
        var cache = new PartitionCache();
        for (int k = startIdx; k < endIdx; k++) {
            var comp = components.get(k);
            TypeName compTypeName = TypeName.get(comp.type()).box();
            String typeKey = compTypeName.toString();
            CodeBlock typeCode = buildTypeInitializer(typeRefClassName, compTypeName);

            cache.types.computeIfAbsent(typeKey, _ -> new CacheEntry<>(typeCode)).count++;
            if (comp.tag() != null) {
                cache.tags.computeIfAbsent(comp.tag(), _ -> new CacheEntry<>(comp.tag())).count++;
            }
        }
        int typeIdx = 0;
        for (var entry : cache.types.values()) {
            if (entry.count > 1) entry.assignedIndex = typeIdx++;
        }
        int tagIdx = 0;
        for (var entry : cache.tags.values()) {
            if (entry.count > 1) entry.assignedIndex = tagIdx++;
        }
        return cache;
    }

    private CodeBlock buildTypeInitializer(ClassName typeRefClassName, TypeName typeName) {
        if (typeName instanceof ParameterizedTypeName ptn) {
            var builder = CodeBlock.builder();
            builder.add("$T.of($T.class", typeRefClassName, ptn.rawType());
            for (var typeArg : ptn.typeArguments()) {
                builder.add(", $L", buildTypeInitializer(typeRefClassName, typeArg));
            }
            builder.add(")");
            return builder.build();
        } else if (typeName instanceof WildcardTypeName wtn) {
            var koraWildcardClassName = ClassName.get("io.koraframework.application.graph", "KoraWildcardType");
            var reflectTypeClass = ClassName.get("java.lang.reflect", "Type");

            if (!wtn.lowerBounds().isEmpty()) {
                return CodeBlock.of("new $T(null, new $T[]{$L})",
                    koraWildcardClassName, reflectTypeClass, buildTypeInitializer(typeRefClassName, wtn.lowerBounds().getFirst()));
            } else if (!wtn.upperBounds().isEmpty() && !wtn.upperBounds().getFirst().equals(ClassName.get(Object.class))) {
                return CodeBlock.of("new $T(new $T[]{$L}, null)",
                    koraWildcardClassName, reflectTypeClass, buildTypeInitializer(typeRefClassName, wtn.upperBounds().getFirst()));
            } else {
                return CodeBlock.of("new $T(null, null)", koraWildcardClassName);
            }
        } else if (typeName instanceof ArrayTypeName atn) {
            return CodeBlock.of("$T.class", atn);
        } else {
            return CodeBlock.of("$T.class", typeName);
        }
    }

    public JavaFile generate() {
        var packageElement = (PackageElement) classElement.getEnclosingElement();
        var implClass = ClassName.get(packageElement.getQualifiedName().toString(), "$" + classElement.getSimpleName() + "Impl");
        var graphName = classElement.getSimpleName() + "Graph";

        ctx.components = this.components;
        ctx.componentsPerPartition = COMPONENTS_PER_PARTITION;

        var classBuilder = TypeSpec.classBuilder(graphName)
            .addAnnotation(AnnotationUtils.generated(KoraAppProcessor.class))
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Supplier.class), CommonClassNames.applicationGraphDraw))
            .addField(CommonClassNames.applicationGraphDraw, "graphDraw", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addField(implClass, "impl", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

        var expectedNodes = components.size();
        var expectedCreateEdges = 0;
        var expectedRefreshEdges = 0;
        var expectedInterceptorEdges = 0;

        for (var component : components) {
            var createDeps = getCreateDependencies(component);
            var refreshDeps = getRefreshDependencies(component, createDeps);
            expectedCreateEdges += countDependencies(createDeps);
            expectedRefreshEdges += refreshDeps.toString().equals("null")
                ? countDependencies(createDeps)
                : countDependencies(refreshDeps);
            expectedInterceptorEdges += interceptors.interceptorsFor(component).size();
        }

        var downstreamMap = invertGraphToDownstream();
        var expectedDownstreamEdges = 0;
        for (var consumers : downstreamMap) {
            expectedDownstreamEdges += consumers.size();
        }

        var totalPartitions = (expectedNodes + COMPONENTS_PER_PARTITION - 1) / COMPONENTS_PER_PARTITION;

        var baseStaticBlock = CodeBlock.builder()
            .addStatement("impl = new $T()", implClass)
            .addStatement("graphDraw = new $T($T.class, (short) $L, (short) $L, (short) $L, (short) $L, (short) $L)",
                CommonClassNames.applicationGraphDraw, classElement,
                expectedNodes, expectedCreateEdges, expectedRefreshEdges, expectedInterceptorEdges, expectedDownstreamEdges);

        for (var p = 0; p < totalPartitions; p++) {
            var partitionClassName = ClassName.get(packageElement.getQualifiedName() + "." + graphName, "Partition" + p);
            baseStaticBlock.addStatement("$T.INSTANCE.init(graphDraw, impl)", partitionClassName);
        }
        classBuilder.addStaticBlock(baseStaticBlock.build());

        var typeRefClassName = ClassName.get("io.koraframework.application.graph", "TypeRef");
        for (var p = 0; p < totalPartitions; p++) {
            ctx.currentPartition = p;
            var startIdx = p * COMPONENTS_PER_PARTITION;
            var endIdx = Math.min(expectedNodes, startIdx + COMPONENTS_PER_PARTITION);

            var partitionClassBuilder = TypeSpec.classBuilder("Partition" + p)
                .addAnnotation(AnnotationUtils.generated(KoraAppProcessor.class))
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get("io.koraframework.application.graph.Graph", "Factory"), ClassName.get(Object.class)))
                .addField(FieldSpec.builder(ClassName.get("", "Partition" + p), "INSTANCE", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new Partition" + p + "()").build())
                .addField(implClass, "applicationImpl", Modifier.PRIVATE, Modifier.STATIC);

            for (var i = startIdx; i < endIdx; i++) {
                var component = components.get(i);
                var componentTypeName = TypeName.get(component.type()).box();
                partitionClassBuilder.addField(FieldSpec.builder(
                    ParameterizedTypeName.get(CommonClassNames.node, componentTypeName),
                    component.fieldName(), Modifier.STATIC).build());
            }

            var initMethodBuilder = MethodSpec.methodBuilder("init")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(CommonClassNames.applicationGraphDraw, "graphDraw")
                .addParameter(implClass, "impl")
                .addStatement("applicationImpl = impl");

            var partitionCache = buildPartitionCache(typeRefClassName, startIdx, endIdx);
            for (var entry : partitionCache.types.values()) {
                if (entry.assignedIndex != null) {
                    initMethodBuilder.addStatement("var type_$L = $L", entry.assignedIndex, entry.value);
                }
            }
            for (var entry : partitionCache.tags.values()) {
                if (entry.assignedIndex != null) {
                    initMethodBuilder.addStatement("var tag_$L = $T.class", entry.assignedIndex, ClassName.bestGuess(entry.value));
                }
            }

            var createMethodSwitch = CodeBlock.builder().beginControlFlow("switch (index)");

            for (var i = startIdx; i < endIdx; i++) {
                var component = components.get(i);
//                var typeCode = buildTypeInitializer(typeRefClassName, componentTypeName);
                var componentTypeName = TypeName.get(component.type()).box();
                var typeEntry = partitionCache.types.get(componentTypeName.toString());
                Object typeExpression;
                if (typeEntry != null && typeEntry.assignedIndex != null) {
                    typeExpression = "type_" + typeEntry.assignedIndex;
                } else {
                    typeExpression = buildTypeInitializer(typeRefClassName, componentTypeName);
                }

                var tagCode = CodeBlock.of("null");
                if (component.tag() != null) {
                    var tagEntry = partitionCache.tags.get(component.tag());
                    tagCode = (tagEntry != null && tagEntry.assignedIndex != null) ? CodeBlock.of("tag_$L", tagEntry.assignedIndex) : CodeBlock.of("$L.class", component.tag());
                }

                var createDeps = getCreateDependencies(component);
                var refreshDeps = getRefreshDependencies(component, createDeps);
                var interceptorsFor = interceptors.interceptorsFor(component);

                CodeBlock interceptorBuilder;
                if (interceptorsFor.isEmpty()) {
                    interceptorBuilder = CodeBlock.of("null");
                } else {
                    var ib = CodeBlock.builder().add("new io.koraframework.application.graph.internal.NodeImpl<?>[]{");
                    for (var j = 0; j < interceptorsFor.size(); j++) {
                        if (j > 0) ib.add(", ");
                        ib.add("($T<?>) $L", ClassName.get("io.koraframework.application.graph.internal", "NodeImpl"), resolveNodePath(interceptorsFor.get(j).component(), p));
                    }
                    ib.add("}");
                    interceptorBuilder = ib.build();
                }

                var conditionCode = buildConditionLambda(component, p);

                var consumers = downstreamMap.get(i);
                CodeBlock downstreamCode;
                if (consumers.isEmpty()) {
                    downstreamCode = CodeBlock.of("null");
                } else {
                    var cb = CodeBlock.builder().add("new short[]{");
                    for (var c = 0; c < consumers.size(); c++) {
                        if (c > 0) cb.add(", ");
                        cb.add("$L", consumers.get(c));
                    }
                    cb.add("}");
                    downstreamCode = cb.build();
                }

                initMethodBuilder.addStatement("$L = graphDraw.addNode($L, $L, $L, $L, $L, $L, $L, INSTANCE)",
                    component.fieldName(), typeExpression, tagCode, conditionCode, createDeps, refreshDeps, downstreamCode, interceptorBuilder
                );
                var declaration = component.declaration();
                var dependenciesCode = generateDependenciesCode(ctx, component, 0, p);
                var innerCodeBlock = CodeBlock.builder();

                switch (declaration) {
                    case ComponentDeclaration.AnnotatedComponent annotatedComponent -> {
                        innerCodeBlock.add("new $T", ClassName.get(annotatedComponent.typeElement()));
                        if (!annotatedComponent.typeVariables().isEmpty()) {
                            innerCodeBlock.add("<");
                            for (var j = 0; j < annotatedComponent.typeVariables().size(); j++) {
                                if (j > 0) innerCodeBlock.add(", ");
                                innerCodeBlock.add("$T", annotatedComponent.typeVariables().get(j));
                            }
                            innerCodeBlock.add(">");
                        }
                        innerCodeBlock.add("($L)", dependenciesCode);
                    }
                    case ComponentDeclaration.FromModuleComponent moduleComponent -> {
                        if (moduleComponent.module() instanceof ModuleDeclaration.ClassModule || moduleComponent.module() instanceof ModuleDeclaration.FactoryModule) {
                            var customDepsCode = generateDependenciesCode(ctx, component, 1, p);
                            var moduleInstDep = component.dependencies().getFirst();
                            var moduleInstCode = resolveDependencyPath(ctx, moduleInstDep, p);
                            if (moduleInstDep instanceof ComponentDependency.SingleDependency) {
                                innerCodeBlock.add("g.get($L).", moduleInstCode);
                            } else {
                                innerCodeBlock.add("$L.", moduleInstCode);
                            }
                            if (!moduleComponent.typeVariables().isEmpty()) {
                                innerCodeBlock.add("<");
                                for (var j = 0; j < moduleComponent.typeVariables().size(); j++) {
                                    if (j > 0) innerCodeBlock.add(", ");
                                    innerCodeBlock.add("$T", moduleComponent.typeVariables().get(j));
                                }
                                innerCodeBlock.add(">");
                            }
                            innerCodeBlock.add("$L($L)", moduleComponent.method().getSimpleName(), customDepsCode);
                        } else {
                            if (moduleComponent.module() instanceof ModuleDeclaration.AnnotatedModule(var element)) {
                                innerCodeBlock.add("applicationImpl.module$L.", allModules.indexOf(element));
                            } else {
                                innerCodeBlock.add("applicationImpl.");
                            }
                            if (!moduleComponent.typeVariables().isEmpty()) {
                                innerCodeBlock.add("<");
                                for (var j = 0; j < moduleComponent.typeVariables().size(); j++) {
                                    if (j > 0) innerCodeBlock.add(", ");
                                    innerCodeBlock.add("$T", moduleComponent.typeVariables().get(j));
                                }
                                innerCodeBlock.add(">");
                            }
                            innerCodeBlock.add("$L($L)", moduleComponent.method().getSimpleName(), dependenciesCode);
                        }
                    }
                    case ComponentDeclaration.FromExtensionComponent extension -> innerCodeBlock.add(extension.generator().apply(dependenciesCode));
                    case ComponentDeclaration.PromisedProxyComponent promisedProxyComponent -> {
                        if (promisedProxyComponent.typeElement().getTypeParameters().isEmpty()) {
                            innerCodeBlock.add("new $T($L)", promisedProxyComponent.className(), dependenciesCode);
                        } else {
                            innerCodeBlock.add("new $T<>($L)", promisedProxyComponent.className(), dependenciesCode);
                        }
                    }
                    case ComponentDeclaration.OptionalComponent _ -> innerCodeBlock.add("$T.ofNullable($L)", Optional.class, dependenciesCode);
                    default -> throw new IllegalStateException("Unsupported component declaration context");
                }

                createMethodSwitch.addStatement("case $L: return $L", i, innerCodeBlock.build());
            }

            createMethodSwitch.addStatement("default: throw new $T()", IllegalArgumentException.class);
            createMethodSwitch.endControlFlow();

            var createMethodBuilder = MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(Object.class)
                .addParameter(int.class, "index")
                .addParameter(ClassName.get("io.koraframework.application.graph", "RefreshableGraph"), "g")
                .addException(ClassName.get(Exception.class))
                .addCode(createMethodSwitch.build());

            var factoryGetLegacyMethod = MethodSpec.methodBuilder("get")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(Object.class)
                .addParameter(ClassName.get("io.koraframework.application.graph", "RefreshableGraph"), "g")
                .addException(ClassName.get(Exception.class))
                .addStatement("throw new $T($S)", UnsupportedOperationException.class, "Use get(graph, index)");

            var factoryGetIndexedMethod = MethodSpec.methodBuilder("get")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(Object.class)
                .addParameter(ClassName.get("io.koraframework.application.graph", "RefreshableGraph"), "g")
                .addParameter(int.class, "index")
                .addException(ClassName.get(Exception.class))
                .addStatement("return create(index, g)");

            partitionClassBuilder.addMethod(initMethodBuilder.build());
            partitionClassBuilder.addMethod(createMethodBuilder.build());
            partitionClassBuilder.addMethod(factoryGetLegacyMethod.build());
            partitionClassBuilder.addMethod(factoryGetIndexedMethod.build());

            classBuilder.addType(partitionClassBuilder.build());
        }

        var supplierMethodBuilder = MethodSpec.methodBuilder("get")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(CommonClassNames.applicationGraphDraw)
            .addStatement("return graphDraw");
        classBuilder.addMethod(supplierMethodBuilder.build());

        var supplierStaticMethodBuilder = MethodSpec.methodBuilder("graph")
            .returns(CommonClassNames.applicationGraphDraw)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addStatement("return graphDraw");
        classBuilder.addMethod(supplierStaticMethodBuilder.build());

        return JavaFile.builder(packageElement.getQualifiedName().toString(), classBuilder.build())
            .addStaticImport(CommonClassNames.nodeWithMapper, "node")
            .addStaticImport(CommonClassNames.nodeWithMapper, "unwrap")
            .addStaticImport(CommonClassNames.all, "all")
            .addStaticImport(CommonClassNames.all, "allValues")
            .addStaticImport(CommonClassNames.all, "allPromises")
            .build();
    }

    private CodeBlock generateDependenciesCode(ProcessingContext ctx, ResolvedComponent component, int startFrom, int currentPartition) {
        var resolvedDependencies = component.dependencies();
        if (resolvedDependencies.isEmpty()) {
            return CodeBlock.of("");
        }
        var b = CodeBlock.builder();
        for (int i = startFrom, dependenciesSize = resolvedDependencies.size(); i < dependenciesSize; i++) {
            if (i > startFrom) {
                b.add(", ");
            }
            var resolvedDependency = resolvedDependencies.get(i);
            var depCode = resolvedDependency.write(ctx);
            b.add("$L", depCode);
        }
        return b.build();
    }

    private CodeBlock resolveDependencyPath(ProcessingContext ctx, ComponentDependency dependency, int currentPartition) {
        ResolvedComponent targetComponent = switch (dependency) {
            case ComponentDependency.SingleDependency single -> switch (single) {
                case ComponentDependency.TargetDependency target -> target.component();
                case ComponentDependency.ValueOfDependency valueOf -> valueOf.component();
                case ComponentDependency.WrappedTargetDependency wrapped -> wrapped.component();
                default -> null;
            };
            default -> null;
        };
        if (targetComponent != null) {
            var globalIndex = components.indexOf(targetComponent);
            var targetPartition = globalIndex / COMPONENTS_PER_PARTITION;
            if (targetPartition == currentPartition) {
                return CodeBlock.of("$L", targetComponent.fieldName());
            }
            return CodeBlock.of("Partition$L.$L", targetPartition, targetComponent.fieldName());
        }
        var depCode = dependency.write(ctx);
        var depStr = depCode.toString();
        for (var comp : components) {
            var globalIdx = components.indexOf(comp);
            var targetPartition = globalIdx / COMPONENTS_PER_PARTITION;
            if (targetPartition != currentPartition) {
                depStr = depStr.replaceAll("\\b" + comp.fieldName() + "\\b", "Partition" + targetPartition + "." + comp.fieldName());
            }
        }
        return CodeBlock.of(depStr);
    }

    private int countDependencies(CodeBlock codeBlock) {
        var str = codeBlock.toString();
        if (str.equals("null") || str.isEmpty()) return 0;
        return str.split(",").length;
    }

    private String resolveNodePath(ResolvedComponent comp, int currentPartition) {
        var idx = components.indexOf(comp);
        if (idx == -1) {
            return comp.fieldName();
        }
        var targetPartition = idx / COMPONENTS_PER_PARTITION;
        if (targetPartition == currentPartition) {
            return comp.fieldName();
        }
        return "Partition" + targetPartition + "." + comp.fieldName();
    }

    private CodeBlock buildConditionLambda(ResolvedComponent component, int currentPartition) {
        if (component.parentConditions().isEmpty() && component.declaration().condition() == null) {
            return CodeBlock.of("null");
        } else if (component.parentConditions().isEmpty()) {
            var condComp = Objects.requireNonNull(conditions.get(component.declaration().condition()));
            return CodeBlock.of("g -> g.condition($L).eval()", resolveNodePath(condComp, currentPartition));
        } else if (component.declaration().condition() == null) {
            return CodeBlock.of("g -> $L.eval()", parentCondition(component, currentPartition));
        } else {
            var condComp = Objects.requireNonNull(conditions.get(component.declaration().condition()));
            return CodeBlock.of("g -> $T.and($L, g.condition($L)).eval()", CommonClassNames.graphCondition, parentCondition(component, currentPartition), resolveNodePath(condComp, currentPartition));
        }
    }

    private CodeBlock parentCondition(ResolvedComponent component, int currentPartition) {
        var parentConditions = component.parentConditions();
        var iterator = parentConditions.iterator();
        var condComp = Objects.requireNonNull(conditions.get(iterator.next()));
        var cb = CodeBlock.builder().add("g.condition($L)", resolveNodePath(condComp, currentPartition));
        while (iterator.hasNext()) {
            var nextComp = Objects.requireNonNull(conditions.get(iterator.next()));
            cb = CodeBlock.builder().add("$T.and($L, g.condition($L))", CommonClassNames.graphCondition, cb.build(), resolveNodePath(nextComp, currentPartition));
        }
        return cb.build();
    }

    private List<List<Integer>> invertGraphToDownstream() {
        int n = components.size();
        var downstreamMap = new ArrayList<List<Integer>>(n);
        for (int i = 0; i < n; i++) {
            downstreamMap.add(new ArrayList<>());
        }

        for (int consumerIdx = 0; consumerIdx < n; consumerIdx++) {
            var component = components.get(consumerIdx);
            var upstreamIndices = collectUpstreamIndices(component);
            for (int providerIdx : upstreamIndices) {
                downstreamMap.get(providerIdx).add(consumerIdx);
            }
        }
        return downstreamMap;
    }

    private Set<Integer> collectUpstreamIndices(ResolvedComponent component) {
        var indices = new LinkedHashSet<Integer>();
        if (component.declaration().condition() != null) {
            var condComponent = conditions.get(component.declaration().condition());
            int idx = components.indexOf(condComponent);
            if (idx != -1) indices.add(idx);
        }
        for (var parentConditionTag : component.parentConditions()) {
            var condComponent = conditions.get(parentConditionTag);
            int idx = components.indexOf(condComponent);
            if (idx != -1) indices.add(idx);
        }

        var interceptorsFor = interceptors.interceptorsFor(component);
        for (var interceptor : interceptorsFor) {
            int idx = components.indexOf(interceptor.component());
            if (idx != -1) {
                indices.add(idx);
            }
        }

        for (var dependency : component.dependencies()) {
            switch (dependency) {
                case ComponentDependency.SingleDependency single -> {
                    var target = switch (single) {
                        case ComponentDependency.TargetDependency t -> t.component();
                        case ComponentDependency.ValueOfDependency v -> v.component();
                        case ComponentDependency.WrappedTargetDependency w -> w.component();
                        default -> null;
                    };
                    if (target != null) {
                        int idx = components.indexOf(target);
                        if (idx != -1) indices.add(idx);
                    }
                }
                case ComponentDependency.AllOfDependency allOf -> {
                    for (var subDep : allOf.getResolvedDependencies()) {
                        int idx = components.indexOf(subDep.component());
                        if (idx != -1) indices.add(idx);
                    }
                }
                case ComponentDependency.OneOfDependency oneOf -> {
                    for (var subDep : oneOf.dependencies()) {
                        int idx = components.indexOf(subDep.component());
                        if (idx != -1) indices.add(idx);
                    }
                }
                case ComponentDependency.PromisedProxyParameterDependency promised -> {
                    if (promised.realDependency != null) {
                        int idx = components.indexOf(promised.realDependency);
                        if (idx != -1) indices.add(idx);
                    }
                }
                default -> {}
            }
        }
        return indices;
    }

    private CodeBlock getCreateDependencies(ResolvedComponent component) {
        var indexList = new LinkedHashSet<String>();

        if (component.declaration().condition() != null) {
            var condition = Objects.requireNonNull(this.conditions.get(component.declaration().condition()));
            int idx = components.indexOf(condition);
            indexList.add(String.valueOf(idx));
        }
        for (var parentConditionTag : component.parentConditions()) {
            var condition = Objects.requireNonNull(this.conditions.get(parentConditionTag));
            int idx = components.indexOf(condition);
            indexList.add(String.valueOf(idx));
        }
        for (var dependency : component.dependencies()) {
            switch (dependency) {
                case ComponentDependency.NullDependency _, ComponentDependency.PromisedProxyParameterDependency _ -> {}
                case ComponentDependency.SingleDependency singleDependency -> {
                    switch (singleDependency) {
                        case ComponentDependency.PromiseOfDependency _ -> {}
                        case ComponentDependency.TargetDependency targetDependency when targetDependency.claim().claimType() != DependencyClaim.DependencyClaimType.NODE_OF -> {
                            int idx = components.indexOf(targetDependency.component());
                            indexList.add(String.valueOf(idx));
                        }
                        case ComponentDependency.TargetDependency _ -> {}
                        case ComponentDependency.ValueOfDependency valueOfDependency -> {
                            int idx = components.indexOf(valueOfDependency.component());
                            indexList.add(String.valueOf(idx));
                        }
                        case ComponentDependency.WrappedTargetDependency wrappedTargetDependency -> {
                            int idx = components.indexOf(wrappedTargetDependency.component());
                            indexList.add(String.valueOf(idx));
                        }
                    }
                }
                case ComponentDependency.AllOfDependency allOfDependency -> {
                    if (allOfDependency.claim().claimType() != DependencyClaim.DependencyClaimType.ALL_OF_PROMISE) {
                        for (var d : allOfDependency.getResolvedDependencies()) {
                            int idx = components.indexOf(d.component());
                            indexList.add(String.valueOf(idx));
                        }
                    }
                }
                case ComponentDependency.OneOfDependency oneOfDependency -> {
                    for (var singleDependency : oneOfDependency.dependencies()) {
                        switch (singleDependency) {
                            case ComponentDependency.PromiseOfDependency _ -> {}
                            case ComponentDependency.TargetDependency targetDependency -> {
                                int idx = components.indexOf(targetDependency.component());
                                indexList.add(String.valueOf(idx));
                            }
                            case ComponentDependency.ValueOfDependency valueOfDependency -> {
                                int idx = components.indexOf(valueOfDependency.component());
                                indexList.add(String.valueOf(idx));
                            }
                            case ComponentDependency.WrappedTargetDependency wrappedTargetDependency -> {
                                int idx = components.indexOf(wrappedTargetDependency.component());
                                indexList.add(String.valueOf(idx));
                            }
                        }
                    }
                }
                default -> {}
            }
        }

        if (indexList.isEmpty()) {
            return CodeBlock.of("null");
        }

        var b = CodeBlock.builder().add("new short[]{");
        int i = 0;
        for (var idx : indexList) {
            if (i++ > 0) b.add(", ");
            b.add("$L", idx);
        }
        b.add("}");
        return b.build();
    }

    private CodeBlock getRefreshDependencies(ResolvedComponent component, CodeBlock createDeps) {
        var indexList = new LinkedHashSet<String>();

        if (component.declaration().condition() != null) {
            var condition = Objects.requireNonNull(this.conditions.get(component.declaration().condition()));
            int idx = components.indexOf(condition);
            indexList.add(String.valueOf(idx));
        }
        for (var parentConditionTag : component.parentConditions()) {
            var condition = Objects.requireNonNull(this.conditions.get(parentConditionTag));
            int idx = components.indexOf(condition);
            indexList.add(String.valueOf(idx));
        }
        for (var dependency : component.dependencies()) {
            switch (dependency) {
                case ComponentDependency.NullDependency _, ComponentDependency.PromisedProxyParameterDependency _ -> {}
                case ComponentDependency.SingleDependency singleDependency -> {
                    switch (singleDependency) {
                        case ComponentDependency.PromiseOfDependency _ -> {}
                        case ComponentDependency.TargetDependency targetDependency when targetDependency.claim().claimType() != DependencyClaim.DependencyClaimType.NODE_OF -> {
                            int idx = components.indexOf(targetDependency.component());
                            indexList.add(String.valueOf(idx));
                        }
                        case ComponentDependency.TargetDependency _ -> {}
                        case ComponentDependency.ValueOfDependency valueOfDependency -> {
                            int idx = components.indexOf(valueOfDependency.component());
                            indexList.add(String.valueOf(idx));
                        }
                        case ComponentDependency.WrappedTargetDependency wrappedTargetDependency -> {
                            int idx = components.indexOf(wrappedTargetDependency.component());
                            indexList.add(String.valueOf(idx));
                        }
                    }
                }
                case ComponentDependency.AllOfDependency allOfDependency -> {
                    var claimType = allOfDependency.claim().claimType();
                    if (claimType != DependencyClaim.DependencyClaimType.ALL_OF_PROMISE && claimType != DependencyClaim.DependencyClaimType.ALL_OF_VALUE) {
                        for (var d : allOfDependency.getResolvedDependencies()) {
                            int idx = components.indexOf(d.component());
                            indexList.add(String.valueOf(idx));
                        }
                    }
                }
                case ComponentDependency.OneOfDependency oneOfDependency -> {
                    for (var singleDependency : oneOfDependency.dependencies()) {
                        switch (singleDependency) {
                            case ComponentDependency.PromiseOfDependency _ -> {}
                            case ComponentDependency.TargetDependency targetDependency -> {
                                int idx = components.indexOf(targetDependency.component());
                                indexList.add(String.valueOf(idx));
                            }
                            case ComponentDependency.ValueOfDependency valueOfDependency -> {
                                int idx = components.indexOf(valueOfDependency.component());
                                indexList.add(String.valueOf(idx));
                            }
                            case ComponentDependency.WrappedTargetDependency wrappedTargetDependency -> {
                                int idx = components.indexOf(wrappedTargetDependency.component());
                                indexList.add(String.valueOf(idx));
                            }
                        }
                    }
                }
                default -> {}
            }
        }
        var b = CodeBlock.builder().add("new short[]{");
        int i = 0;
        for (var idx : indexList) {
            if (i++ > 0) b.add(", ");
            b.add("$L", idx);
        }
        b.add("}");
        CodeBlock refreshDeps = b.build();

        if (createDeps != null && createDeps.toString().equals(refreshDeps.toString())) {
            return CodeBlock.of("null");
        }

        return refreshDeps;
    }
}
