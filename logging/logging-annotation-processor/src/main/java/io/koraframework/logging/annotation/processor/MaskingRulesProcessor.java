package io.koraframework.logging.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.logging.annotation.processor.aop.LogAspectClassNames;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;

public final class MaskingRulesProcessor {

    private final ProcessingEnvironment env;
    private final Elements elements;
    private final Types types;

    public MaskingRulesProcessor(ProcessingEnvironment env) {
        this.env = env;
        this.elements = env.getElementUtils();
        this.types = env.getTypeUtils();
    }

    public void generate(TypeElement root) {
        var packageName = this.elements.getPackageOf(root).getQualifiedName().toString();
        var className = metadataName(root);
        if (this.elements.getTypeElement(packageName + "." + className) != null) {
            return;
        }

        var rules = new ArrayList<MaskingRuleMeta>();
        this.visit(root, new ArrayList<>(), new HashSet<>(), rules);
        var strategies = this.strategies(rules);

        var rootType = TypeName.get(root.asType());
        var rulesType = ParameterizedTypeName.get(LogAspectClassNames.maskingRules, rootType);
        var typeBuilder = TypeSpec.interfaceBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(CommonClassNames.module)
            .addAnnotation(AnnotationUtils.generated(MaskingRulesProcessor.class))
            .addMethod(this.factoryMethod(root, rulesType, rules, strategies));

        CommonUtils.safeWriteTo(this.env, JavaFile.builder(packageName, typeBuilder.build()).build());
    }

    private MethodSpec factoryMethod(TypeElement root, TypeName rulesType, List<MaskingRuleMeta> rules, Map<String, StrategyMeta> strategies) {
        var method = MethodSpec.methodBuilder(CommonUtils.decapitalize(root.getSimpleName().toString()) + "MaskingRules")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(CommonClassNames.defaultComponent)
            .returns(rulesType);
        for (var strategy : strategies.values()) {
            method.addParameter(TypeName.get(strategy.type()), strategy.fieldName());
        }
        method.addStatement("return $L", this.rulesCode(root, rules, strategies));
        return method.build();
    }

    private CodeBlock rulesCode(TypeElement root, List<MaskingRuleMeta> rules, Map<String, StrategyMeta> strategies) {
        var code = CodeBlock.builder()
            .add("$T.builder($T.class)", LogAspectClassNames.maskingRules, TypeName.get(root.asType()));
        for (var rule : rules) {
            var strategy = strategies.get(rule.strategy().toString());
            code.add("\n.mask($S, $N)", String.join(".", rule.path()), strategy.fieldName());
        }
        return code.add("\n.build()").build();
    }

    private Map<String, StrategyMeta> strategies(List<MaskingRuleMeta> rules) {
        var strategies = new LinkedHashMap<String, StrategyMeta>();
        for (var rule : rules) {
            strategies.computeIfAbsent(rule.strategy().toString(), k -> new StrategyMeta(rule.strategy(), "strategy" + strategies.size()));
        }
        return strategies;
    }

    private void visit(TypeElement type, List<String> path, Set<String> branch, List<MaskingRuleMeta> rules) {
        if (!this.isJsonOrMasked(type)) {
            return;
        }
        var key = type.getQualifiedName().toString();
        if (!branch.add(key)) {
            return;
        }

        var typeMask = findMask(type);
        for (var field : this.parse(type)) {
            var fieldPath = new ArrayList<>(path);
            fieldPath.add(field.jsonName());
            if (field.mask() != null) {
                rules.add(new MaskingRuleMeta(fieldPath, this.maskStrategy(field.mask(), typeMask), false));
                continue;
            }
            this.visitFieldType(field.type(), fieldPath, branch, rules);
        }
        branch.remove(key);
    }

    private void visitFieldType(TypeMirror type, List<String> path, Set<String> branch, List<MaskingRuleMeta> rules) {
        if (type.getKind() == TypeKind.ARRAY && type instanceof ArrayType arrayType) {
            this.visitFieldType(arrayType.getComponentType(), path, branch, rules);
            return;
        }
        if (type.getKind() != TypeKind.DECLARED || !(type instanceof DeclaredType declaredType)) {
            return;
        }
        if (CommonUtils.isCollection(type)) {
            if (!declaredType.getTypeArguments().isEmpty()) {
                this.visitFieldType(declaredType.getTypeArguments().get(0), path, branch, rules);
            }
            return;
        }
        if (CommonUtils.isMap(type)) {
            if (declaredType.getTypeArguments().size() >= 2) {
                var valuePath = new ArrayList<>(path);
                valuePath.add("*");
                this.visitFieldType(declaredType.getTypeArguments().get(1), valuePath, branch, rules);
            }
            return;
        }

        var element = this.types.asElement(type);
        if (element instanceof TypeElement typeElement) {
            this.visit(typeElement, path, branch, rules);
        }
    }

    private TypeMirror maskStrategy(AnnotationMirror mask, AnnotationMirror typeMask) {
        var strategyType = AnnotationUtils.<TypeMirror>parseAnnotationValueWithoutDefault(mask, "value");
        if (strategyType != null) {
            return strategyType;
        }
        if (typeMask != null) {
            return AnnotationUtils.parseAnnotationValue(this.elements, typeMask, "value");
        }
        return this.elements.getTypeElement(LogAspectClassNames.maskingFull.canonicalName()).asType();
    }

    private List<MaskingField> parse(TypeElement type) {
        var fields = new ArrayList<MaskingField>();
        var nameConverter = CommonUtils.getNameConverter(type);
        if (type.getKind() == ElementKind.RECORD) {
            for (var enclosed : type.getEnclosedElements()) {
                if (!(enclosed instanceof RecordComponentElement component)) {
                    continue;
                }
                if (AnnotationUtils.findAnnotation(component, LogAspectClassNames.jsonSkip) != null) {
                    continue;
                }
                var accessor = this.findRecordAccessor(type, component);
                var jsonName = this.jsonName(component, nameConverter);
                var mask = findMask(component, accessor);
                fields.add(new MaskingField(jsonName, component.asType(), mask));
            }
        } else {
            for (var enclosed : type.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.FIELD || !(enclosed instanceof VariableElement field)) {
                    continue;
                }
                if (field.getModifiers().contains(Modifier.STATIC)) {
                    continue;
                }
                if (AnnotationUtils.findAnnotation(field, LogAspectClassNames.jsonSkip) != null) {
                    continue;
                }
                var jsonName = this.jsonName(field, nameConverter);
                var accessor = this.findAccessor(type, field);
                var mask = accessor == null ? findMask(field) : findMask(field, accessor);
                fields.add(new MaskingField(jsonName, field.asType(), mask));
            }
        }
        return fields;
    }

    private String jsonName(Element field, CommonUtils.NameConverter nameConverter) {
        var jsonField = AnnotationUtils.findAnnotation(field, LogAspectClassNames.jsonField);
        if (jsonField != null) {
            var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(jsonField, "value");
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        var name = field.getSimpleName().toString();
        return nameConverter == null ? name : nameConverter.convert(name);
    }

    private ExecutableElement findRecordAccessor(TypeElement type, RecordComponentElement component) {
        return CommonUtils.findMethods(type, m -> m.contains(Modifier.PUBLIC))
            .stream()
            .filter(m -> m.getParameters().isEmpty())
            .filter(m -> m.getSimpleName().contentEquals(component.getSimpleName()))
            .findFirst()
            .orElse(null);
    }

    private ExecutableElement findAccessor(TypeElement type, VariableElement field) {
        var name = field.getSimpleName().toString();
        var capitalized = CommonUtils.capitalize(name);
        return CommonUtils.findMethods(type, m -> m.contains(Modifier.PUBLIC))
            .stream()
            .filter(m -> m.getParameters().isEmpty())
            .filter(m -> m.getSimpleName().contentEquals(name) || m.getSimpleName().contentEquals("get" + capitalized))
            .findFirst()
            .orElse(null);
    }

    private boolean isJsonOrMasked(TypeElement typeElement) {
        return AnnotationUtils.findAnnotation(typeElement, LogAspectClassNames.json) != null
            || AnnotationUtils.findAnnotation(typeElement, LogAspectClassNames.jsonWriter) != null
            || AnnotationUtils.findAnnotation(typeElement, LogAspectClassNames.mask) != null;
    }

    private static AnnotationMirror findMask(Element... elements) {
        for (var element : elements) {
            var mask = AnnotationUtils.findAnnotation(element, LogAspectClassNames.mask);
            if (mask != null) {
                return mask;
            }
        }
        return null;
    }

    public static String metadataName(TypeElement type) {
        return NameUtils.generatedType(type, "MaskingRulesModule");
    }

    private record MaskingField(String jsonName, TypeMirror type, AnnotationMirror mask) {}

    private record MaskingRuleMeta(List<String> path, TypeMirror strategy, boolean fieldOnly) {}

    private record StrategyMeta(TypeMirror type, String fieldName) {}
}
