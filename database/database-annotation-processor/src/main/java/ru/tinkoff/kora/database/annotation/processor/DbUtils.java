package ru.tinkoff.kora.database.annotation.processor;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.database.annotation.processor.model.QueryParameter;

import javax.annotation.Nullable;
import javax.lang.model.element.*;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;


public class DbUtils {
    public static final ClassName QUERY_ANNOTATION = ClassName.get("ru.tinkoff.kora.database.common.annotation", "Query");
    public static final ClassName REPOSITORY_ANNOTATION = ClassName.get("ru.tinkoff.kora.database.common.annotation", "Repository");
    public static final ClassName BATCH_ANNOTATION = ClassName.get("ru.tinkoff.kora.database.common.annotation", "Batch");
    public static final ClassName COLUMN_ANNOTATION = ClassName.get("ru.tinkoff.kora.database.common.annotation", "Column");
    public static final ClassName ID_ANNOTATION = ClassName.get("ru.tinkoff.kora.database.common.annotation", "Id");
    public static final ClassName TABLE_ANNOTATION = ClassName.get("ru.tinkoff.kora.database.common.annotation", "Table");
    public static final ClassName SUB_ENTITY_OF_ANNOTATION = ClassName.get("ru.tinkoff.kora.database.common.annotation", "SubEntityOf");
    public static final ClassName ENTITY_CONSTRUCTOR_ANNOTATION = ClassName.get("ru.tinkoff.kora.database.common.annotation", "EntityConstructor");
    public static final ClassName QUERY_CONTEXT = ClassName.get("ru.tinkoff.kora.database.common", "QueryContext");
    public static final ClassName UPDATE_COUNT = ClassName.get("ru.tinkoff.kora.database.common", "UpdateCount");

    public static List<ExecutableElement> findQueryMethods(Types types, Elements elements, TypeElement repositoryElement) {
        return DbUtils.collectInterfaces(types, repositoryElement).stream()
            .flatMap(type -> type.getEnclosedElements()
                .stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
                .filter(e -> e.getModifiers().contains(Modifier.PUBLIC) || e.getModifiers().contains(Modifier.PROTECTED))
                .filter(e -> e.getModifiers().contains(Modifier.ABSTRACT)))
            .map(ExecutableElement.class::cast)
            .filter(e -> CommonUtils.findAnnotation(elements, e, QUERY_ANNOTATION) != null)
            .toList();
    }

    public static MethodSpec.Builder queryMethodBuilder(ExecutableElement method, ExecutableType methodType) {
        var b = CommonUtils.overridingKeepAop(method, methodType)
            .addModifiers(Modifier.PUBLIC);
        for (var thrownType : method.getThrownTypes()) {
            b.addException(TypeName.get(thrownType));
        }
        return b;
    }

    public static CodeBlock getTag(TypeElement repositoryElement) {
        var repositoryAnnotation = CommonUtils.findDirectAnnotation(repositoryElement, DbUtils.REPOSITORY_ANNOTATION);
        var executorTagAnnotation = CommonUtils.<AnnotationMirror>parseAnnotationValueWithoutDefault(repositoryAnnotation, "executorTag");
        if (executorTagAnnotation == null) {
            return null;
        }
        var tagValue = CommonUtils.<List<TypeMirror>>parseAnnotationValueWithoutDefault(executorTagAnnotation, "value");
        return CommonUtils.writeTagAnnotationValue(Objects.requireNonNull(tagValue).toArray(TypeMirror[]::new));
    }

    static Set<TypeElement> collectInterfaces(Types types, TypeElement typeElement) {
        var result = new HashSet<TypeElement>();
        collectInterfaces(types, result, typeElement);
        return result;
    }

    private static void collectInterfaces(Types types, Set<TypeElement> collectedElements, TypeElement typeElement) {
        if (collectedElements.add(typeElement)) {
            if (typeElement.asType().getKind() == TypeKind.ERROR) {
                throw new ProcessingErrorException("Element is error: %s".formatted(typeElement.toString()), typeElement);
            }
            for (var directlyImplementedInterface : typeElement.getInterfaces()) {
                var interfaceElement = (TypeElement) types.asElement(directlyImplementedInterface);
                collectInterfaces(types, collectedElements, interfaceElement);
            }
        }
    }

    public static String parameterMapperName(ExecutableElement method, VariableElement parameter, String... names) {
        var sb = new StringBuilder("$" + method.getSimpleName() + "_" + parameter.getSimpleName());
        for (var name : names) {
            sb.append("_").append(name);
        }
        return sb.append("_parameterMapper").toString();
    }

    public static String resultMapperName(ExecutableElement method) {
        var sb = new StringBuilder("$" + method.getSimpleName());
        return sb.append("_resultMapper").toString();
    }


    public record Mapper(@Nullable TypeMirror typeMirror, TypeName typeName, String name, @Nullable Function<CodeBlock, CodeBlock> wrapper) {
        public Mapper(TypeName typeName, String name) {
            this(null, typeName, name, null);
        }

        public Mapper(TypeMirror typeMirror, TypeName typeName, String name) {
            this(typeMirror, typeName, name, null);
        }
    }

    public static void addMappers(Types types, TypeSpec.Builder type, MethodSpec.Builder constructor, List<Mapper> mappers) {
        for (var mapper : mappers) {
            if (mapper.typeMirror == null) {
                type.addField(mapper.typeName, mapper.name, Modifier.PRIVATE, Modifier.FINAL);
                constructor.addParameter(mapper.typeName, mapper.name);
                constructor.addCode("this.$L = $L;\n", mapper.name, CodeBlock.of("$L", mapper.name));
            } else {
                if (CommonUtils.hasDefaultConstructorAndFinal(types, mapper.typeMirror)) {
                    if (mapper.wrapper != null) {
                        type.addField(FieldSpec.builder(mapper.typeName, mapper.name, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                            .initializer(mapper.wrapper.apply(CodeBlock.of("new $T()", mapper.typeMirror)))
                            .build());
                    } else {
                        type.addField(FieldSpec.builder(TypeName.get(mapper.typeMirror), mapper.name, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                            .initializer(CodeBlock.of("new $T()", mapper.typeMirror))
                            .build());
                    }
                } else {
                    constructor.addParameter(TypeName.get(mapper.typeMirror), mapper.name);
                    if (mapper.wrapper != null) {
                        type.addField(mapper.typeName, mapper.name, Modifier.PRIVATE, Modifier.FINAL);
                        constructor.addCode("this.$L = $L;\n", mapper.name, mapper.wrapper.apply(CodeBlock.of("$L", mapper.name)));
                    } else {
                        type.addField(TypeName.get(mapper.typeMirror), mapper.name, Modifier.PRIVATE, Modifier.FINAL);
                        constructor.addCode("this.$L = $L;\n", mapper.name, CodeBlock.of("$L", mapper.name));
                    }
                }
            }
        }
    }

    public static List<DbUtils.Mapper> parseParameterMappers(ExecutableElement method, List<QueryParameter> parameters, QueryWithParameters query, Predicate<TypeName> nativeTypePredicate, ClassName parameterColumnMapper) {
        var mappers = new ArrayList<Mapper>();
        for (var parameter : parameters) {
            if (parameter instanceof QueryParameter.ConnectionParameter) {
                continue;
            }
            if (parameter instanceof QueryParameter.BatchParameter bp) {
                parameter = bp.parameter();
            }
            var parameterType = parameter.type();
            var mappings = CommonUtils.parseMapping(parameter.variable());
            var mapping = mappings.getMapping(parameterColumnMapper);
            if (mapping != null) {
                var mapperName = DbUtils.parameterMapperName(method, parameter.variable());
                var mapperType = ParameterizedTypeName.get(parameterColumnMapper, TypeName.get(parameterType));
                mappers.add(new DbUtils.Mapper(mapping.mapperClass(), mapperType, mapperName, c -> c));
                continue;
            }
            if (parameter instanceof QueryParameter.SimpleParameter sp) {
                if (!nativeTypePredicate.test(TypeName.get(parameter.type()))) {
                    var mapperName = DbUtils.parameterMapperName(method, parameter.variable());
                    var mapperType = ParameterizedTypeName.get(parameterColumnMapper, TypeName.get(parameterType));
                    mappers.add(new DbUtils.Mapper(mapperType, mapperName));
                }
                continue;
            }
            if (parameter instanceof QueryParameter.EntityParameter ep) {
                for (var entityField : ep.entity().entityFields()) {
                    var queryParam = query.find(parameter.name() + "." + entityField.element().getSimpleName());
                    if (queryParam == null || queryParam.sqlIndexes().isEmpty()) {
                        continue;
                    }
                    var mapperName = DbUtils.parameterMapperName(method, parameter.variable(), entityField.element().getSimpleName().toString());
                    var mapperType = ParameterizedTypeName.get(parameterColumnMapper, TypeName.get(entityField.typeMirror()).box());
                    var fieldMappings = CommonUtils.parseMapping(entityField.element());
                    var fieldMapping = fieldMappings.getMapping(parameterColumnMapper);
                    if (fieldMapping != null) {
                        mappers.add(new DbUtils.Mapper(fieldMapping.mapperClass(), mapperType, mapperName));
                        continue;
                    }
                    if (!nativeTypePredicate.test(TypeName.get(entityField.typeMirror()))) {
                        mappers.add(new DbUtils.Mapper(mapperType, mapperName));
                    }
                }
                continue;
            }
        }
        return mappers;
    }

}
