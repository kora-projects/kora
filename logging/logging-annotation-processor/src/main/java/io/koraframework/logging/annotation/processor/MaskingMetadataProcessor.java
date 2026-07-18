package io.koraframework.logging.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.logging.annotation.processor.aop.LogAspectClassNames;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;

public final class MaskingMetadataProcessor {

    private static final ClassName MAP = ClassName.get(Map.class);
    private static final ClassName CLASS = ClassName.get(Class.class);
    private static final ClassName MASK_MODE = LogAspectClassNames.mask.nestedClass("Mode");

    private final ProcessingEnvironment env;
    private final Elements elements;
    private final Types types;

    public MaskingMetadataProcessor(ProcessingEnvironment env) {
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

        var visited = new LinkedHashMap<String, MaskingClassMeta>();
        this.visit(root, visited);

        var rootType = TypeName.get(root.asType());
        var metadataType = ParameterizedTypeName.get(LogAspectClassNames.maskingMetadata, rootType);
        var type = TypeSpec.classBuilder(className)
            .addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.FINAL)
            .addAnnotation(CommonClassNames.component)
            .addAnnotation(AnnotationUtils.generated(MaskingMetadataProcessor.class))
            .addSuperinterface(metadataType)
            .addField(FieldSpec.builder(
                ParameterizedTypeName.get(MAP, ParameterizedTypeName.get(CLASS, WildcardTypeName.subtypeOf(Object.class)), LogAspectClassNames.maskingClassMeta),
                "metadata",
                javax.lang.model.element.Modifier.PRIVATE,
                javax.lang.model.element.Modifier.FINAL
            ).build())
            .addMethod(this.constructor(visited.values()))
            .addMethod(this.metadataMethod())
            .build();

        CommonUtils.safeWriteTo(this.env, JavaFile.builder(packageName, type).build());
    }

    private MethodSpec constructor(Collection<MaskingClassMeta> metas) {
        var method = MethodSpec.constructorBuilder()
            .addModifiers(javax.lang.model.element.Modifier.PUBLIC);
        method.addCode("this.metadata = $L;\n", this.metadataCode(metas));
        return method.build();
    }

    private CodeBlock metadataCode(Collection<MaskingClassMeta> metas) {
        if (metas.isEmpty()) {
            return CodeBlock.of("$T.of()", MAP);
        }

        var code = CodeBlock.builder().add("$T.ofEntries(\n$>", MAP);
        var iterator = metas.iterator();
        while (iterator.hasNext()) {
            var meta = iterator.next();
            code.add("$T.entry($T.class, new $T($L))", MAP, TypeName.get(meta.type().asType()), LogAspectClassNames.maskingClassMeta, this.fieldsCode(meta));
            if (iterator.hasNext()) {
                code.add(",\n");
            }
        }
        return code.add("$<\n)").build();
    }

    private MethodSpec metadataMethod() {
        return MethodSpec.methodBuilder("metadata")
            .addAnnotation(Override.class)
            .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
            .returns(LogAspectClassNames.maskingClassMeta)
            .addParameter(ParameterizedTypeName.get(CLASS, WildcardTypeName.subtypeOf(Object.class)), "type")
            .addStatement("return this.metadata.get(type)")
            .build();
    }

    private CodeBlock fieldsCode(MaskingClassMeta meta) {
        var fields = new ArrayList<CodeBlock>();
        var typeMask = findMask(meta.type());
        for (var field : meta.fields()) {
            var mask = field.mask();
            if (mask != null) {
                fields.add(CodeBlock.of("$S, $T.mask($L)", field.jsonName(), LogAspectClassNames.maskingFieldMeta, this.maskRuleCode(mask, typeMask)));
                continue;
            }
            var nestedType = this.nestedType(field.type());
            if (nestedType == null) {
                continue;
            }
            if (CommonUtils.isCollection(field.type())) {
                fields.add(CodeBlock.of("$S, $T.collection($T.class)", field.jsonName(), LogAspectClassNames.maskingFieldMeta, TypeName.get(nestedType.asType())));
            } else if (CommonUtils.isMap(field.type())) {
                fields.add(CodeBlock.of("$S, $T.mapValue($T.class)", field.jsonName(), LogAspectClassNames.maskingFieldMeta, TypeName.get(nestedType.asType())));
            } else {
                fields.add(CodeBlock.of("$S, $T.object($T.class)", field.jsonName(), LogAspectClassNames.maskingFieldMeta, TypeName.get(nestedType.asType())));
            }
        }
        if (fields.isEmpty()) {
            return CodeBlock.of("$T.of()", MAP);
        }

        var code = CodeBlock.builder().add("$T.ofEntries(\n$>", MAP);
        for (int i = 0; i < fields.size(); i++) {
            code.add("$T.entry($L)", MAP, fields.get(i));
            if (i < fields.size() - 1) {
                code.add(",\n");
            }
        }
        return code.add("$<\n)").build();
    }

    private CodeBlock maskRuleCode(AnnotationMirror mask, AnnotationMirror typeMask) {
        var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(mask, "value");
        if (value == null) {
            value = AnnotationUtils.parseAnnotationValue(this.elements, typeMask, "value");
        }
        if (value == null) {
            value = "***";
        }

        var mode = this.mode(mask);
        if (mode == null) {
            mode = this.mode(typeMask);
        }
        if (mode == null) {
            mode = "FULL";
        }

        var keep = AnnotationUtils.<Integer>parseAnnotationValueWithoutDefault(mask, "keep");
        if (keep == null) {
            keep = AnnotationUtils.parseAnnotationValue(this.elements, typeMask, "keep");
        }
        if (keep == null) {
            keep = 4;
        }

        return CodeBlock.of("$T.replacement($S, $T.$L, $L)", LogAspectClassNames.maskRule, value, MASK_MODE, mode, keep);
    }

    private String mode(AnnotationMirror mask) {
        var mode = AnnotationUtils.<VariableElement>parseAnnotationValueWithoutDefault(mask, "mode");
        return mode == null ? null : mode.getSimpleName().toString();
    }

    private void visit(TypeElement type, Map<String, MaskingClassMeta> visited) {
        var key = type.getQualifiedName().toString();
        if (visited.containsKey(key)) {
            return;
        }
        if (!this.isJsonOrMasked(type)) {
            return;
        }
        var meta = this.parse(type);
        visited.put(key, meta);
        for (var field : meta.fields()) {
            if (field.mask() != null) {
                continue;
            }
            for (var nested : this.nestedTypes(field.type())) {
                this.visit(nested, visited);
            }
        }
    }

    private MaskingClassMeta parse(TypeElement type) {
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
                if (field.getModifiers().contains(javax.lang.model.element.Modifier.STATIC)) {
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
        return new MaskingClassMeta(type, fields);
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
        return CommonUtils.findMethods(type, m -> m.contains(javax.lang.model.element.Modifier.PUBLIC))
            .stream()
            .filter(m -> m.getParameters().isEmpty())
            .filter(m -> m.getSimpleName().contentEquals(component.getSimpleName()))
            .findFirst()
            .orElse(null);
    }

    private ExecutableElement findAccessor(TypeElement type, VariableElement field) {
        var name = field.getSimpleName().toString();
        var capitalized = CommonUtils.capitalize(name);
        return CommonUtils.findMethods(type, m -> m.contains(javax.lang.model.element.Modifier.PUBLIC))
            .stream()
            .filter(m -> m.getParameters().isEmpty())
            .filter(m -> m.getSimpleName().contentEquals(name) || m.getSimpleName().contentEquals("get" + capitalized))
            .findFirst()
            .orElse(null);
    }

    private TypeElement nestedType(TypeMirror type) {
        var nestedTypes = this.nestedTypes(type);
        if (nestedTypes.size() != 1) {
            return null;
        }
        return nestedTypes.get(0);
    }

    private List<TypeElement> nestedTypes(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED || !(type instanceof DeclaredType declaredType)) {
            return List.of();
        }
        if (CommonUtils.isCollection(type)) {
            if (declaredType.getTypeArguments().isEmpty()) {
                return List.of();
            }
            return this.nestedTypes(declaredType.getTypeArguments().get(0));
        } else if (CommonUtils.isMap(type)) {
            if (declaredType.getTypeArguments().size() < 2) {
                return List.of();
            }
            return this.nestedTypes(declaredType.getTypeArguments().get(1));
        }

        var element = this.types.asElement(type);
        if (!(element instanceof TypeElement typeElement)) {
            return List.of();
        }
        if (!this.isJsonOrMasked(typeElement)) {
            return List.of();
        }
        return List.of(typeElement);
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
        return NameUtils.generatedType(type, "MaskingMetadata");
    }

    private record MaskingClassMeta(TypeElement type, List<MaskingField> fields) {}

    private record MaskingField(String jsonName, TypeMirror type, AnnotationMirror mask) {}
}
