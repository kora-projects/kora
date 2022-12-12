package ru.tinkoff.kora.config.annotation.processor;

import com.squareup.javapoet.*;
import com.typesafe.config.Config;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.common.Module;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Generated;
import ru.tinkoff.kora.config.annotation.processor.exception.NewRoundWantedException;
import ru.tinkoff.kora.config.common.ConfigRoot;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ConfigRootModuleGenerator {
    private final ProcessingEnvironment processingEnv;
    private final Elements elements;
    private final Types types;
    private final TypeElement configParserType;

    public ConfigRootModuleGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.configParserType = (TypeElement) this.types.asElement(this.types.erasure(this.elements.getTypeElement(ConfigValueExtractor.class.getCanonicalName()).asType()));
    }

    public final JavaFile generateModule(RoundEnvironment roundEnv, TypeElement element) {
        var packageName = elements.getPackageOf(element).getQualifiedName().toString();
        var typeName = element.getSimpleName() + "Module";

        var typeBuilder = TypeSpec.interfaceBuilder(typeName)
            .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", CodeBlock.of("$S", ConfigRootModuleGenerator.class.getCanonicalName())).build())
            .addModifiers(Modifier.PUBLIC);
        {
            var configRoot = element.getAnnotationMirrors().stream()
                .filter(a -> a.getAnnotationType().toString().equals(ConfigRoot.class.getCanonicalName()))
                .findFirst()
                .get();
            var i = configRoot.getElementValues().entrySet().iterator();
            if (i.hasNext()) typeBuilder.addAnnotation(AnnotationSpec.builder(Module.class)
                .build()
            );
        }

        var parserType = this.types.getDeclaredType(this.configParserType, element.asType());

        var rootConfigTypeName = TypeName.get(element.asType());
        var configMethod = MethodSpec.methodBuilder(CommonUtils.decapitalize(element.getSimpleName().toString()))
            .returns(TypeName.get(element.asType()))
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(ParameterSpec.builder(TypeName.get(Config.class), "config").build())
            .addParameter(ParameterSpec.builder(TypeName.get(parserType), "configParser").build())
            .addStatement("return configParser.extract(config.root())")
            .build();
        typeBuilder.addMethod(configMethod);

        for (var field : collectFieldsAccessors(element)) {
            TypeMirror returnType = field.accessor.getReturnType();
            if(returnType.getKind() == TypeKind.ERROR && !roundEnv.processingOver()) {
                throw new NewRoundWantedException(element);
            }
            var methodBuilder = MethodSpec.methodBuilder(field.name + "ConfigValue")
                .returns(TypeName.get(returnType))
                .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(rootConfigTypeName, "config").build())
                .addStatement("return config.$L()", field.accessor.getSimpleName().toString());

            if (field.tags.size() != 0) {
                var tagsBlock = CodeBlock.builder().add("{");
                for (int i = 0; i < field.tags.size(); i++) {
                    TypeMirror tag = field.tags.get(i);
                    var trailingComma = i + 1 == field.tags.size() ? "" : ", ";
                    tagsBlock.add("$T.class$L", tag, trailingComma);
                }

                tagsBlock.add("}");

                methodBuilder.addAnnotation(
                    AnnotationSpec.builder(Tag.class).addMember("value", tagsBlock.build()).build()
                );
            }

            typeBuilder.addMethod(methodBuilder.build());
        }

        var type = typeBuilder.build();

        return JavaFile.builder(packageName, type).build();
    }

    private List<FieldMeta> collectFieldsAccessors(TypeElement element) {
        if (element.getKind() == ElementKind.CLASS) {
            return collectPojoFields(element);
        }

        if (element.getKind() == ElementKind.RECORD) {
            return collectRecordFields(element);
        }

        return List.of();
    }

    private List<FieldMeta> collectPojoFields(TypeElement element) {
        Map<String, Element> fields = element.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .collect(Collectors.toMap(
                f -> "get" + CommonUtils.capitalize(f.getSimpleName().toString()),
                Function.identity()
            ));
        var constructorParams = getPojoConstructorParams(element);
        return collectFieldsMeta(element, fields, constructorParams);
    }

    private Map<String, Element> getPojoConstructorParams(TypeElement element) {
        return getElementConstructorParams(element)
            .collect(Collectors.toMap(
                f -> "get" + CommonUtils.capitalize(f.getSimpleName().toString()),
                Function.identity()
            ));
    }

    private List<FieldMeta> collectRecordFields(TypeElement element) {
        Map<String, Element> fields = element.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .collect(Collectors.toMap(
                f -> f.getSimpleName().toString(),
                Function.identity()
            ));

        var constructorParams = getRecordConstructorParams(element);

        return collectFieldsMeta(element, fields, constructorParams);
    }

    private Map<String, Element> getRecordConstructorParams(TypeElement element) {
        return getElementConstructorParams(element)
            .collect(Collectors.toMap(
                f -> f.getSimpleName().toString(),
                Function.identity()
            ));
    }

    private Stream<? extends VariableElement> getElementConstructorParams(TypeElement element) {
        return element.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR && e.getModifiers().contains(Modifier.PUBLIC))
            .findFirst()
            .map(ExecutableElement.class::cast)
            .map(ExecutableElement::getParameters)
            .orElse(new ArrayList<>()).stream();
    }

    private List<FieldMeta> collectFieldsMeta(TypeElement element, Map<String, Element> fields, Map<String, Element> constructorParams) {
        return element.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .map(ExecutableElement.class::cast)
            .filter(m -> m.getParameters().size() == 0 && fields.containsKey(m.getSimpleName().toString()))
            .map(getter -> {
                var field = fields.get(getter.getSimpleName().toString());
                var parameter = constructorParams.get(getter.getSimpleName().toString());
                var tags = CommonUtils.parseTagValue(field);
                if (tags.length == 0) {
                    tags = CommonUtils.parseTagValue(getter);
                }
                if (tags.length == 0) {
                    tags = CommonUtils.parseTagValue(parameter);
                }

                return new FieldMeta(field.getSimpleName().toString(), getter, List.of(tags));
            })
            .collect(Collectors.toList());
    }

    private record FieldMeta(String name, ExecutableElement accessor, List<TypeMirror> tags) {}
}
