package ru.tinkoff.kora.database.annotation.processor.entity;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.database.annotation.processor.EntityUtils;

import javax.annotation.Nullable;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public record DbEntity(TypeMirror typeMirror, TypeElement typeElement, EntityType entityType, List<EntityField> entityFields) {
    public enum EntityType {
        RECORD, BEAN, DATA_CLASS
    }

    public record EntityField(VariableElement element, TypeMirror typeMirror, String columnName) {}


    public static DbEntity parseEntity(Types types, TypeMirror typeMirror) {
        var typeElement = (TypeElement) types.asElement(typeMirror);
        if (typeElement == null) {
            return null;
        }
        if (isRecord(typeElement)) {
            return parseRecordEntity(typeElement);
        }
        var javaBeanEntity = parseJavaBean(types, typeMirror, typeElement);
        if (javaBeanEntity != null) {
            return javaBeanEntity;
        }
        // todo kotlin?
        return null;
    }

    private static DbEntity parseRecordEntity(TypeElement typeElement) {
        var nameConverter = CommonUtils.getNameConverter(typeElement);
        var fields = typeElement.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .map(e -> {
                var element = (VariableElement) e;
                var type = element.asType();
                var columnName = EntityUtils.parseColumnName(element, nameConverter);
                return new DbEntity.EntityField(element, type, columnName);
            })
            .toList();
        return new DbEntity(typeElement.asType(), typeElement, EntityType.RECORD, fields);
    }

    private static boolean isRecord(TypeElement typeElement) {
        var superclass = typeElement.getSuperclass();
        if (superclass == null) {
            return false;
        }
        return superclass.toString().equals(Record.class.getCanonicalName());
    }

    @Nullable
    private static DbEntity parseJavaBean(Types types, TypeMirror typeMirror, TypeElement typeElement) {
        var nameConverter = CommonUtils.getNameConverter(typeElement);
        var methods = typeElement.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.METHOD && e.getModifiers().contains(Modifier.PUBLIC))
            .map(ExecutableElement.class::cast)
            .collect(Collectors.toMap(e -> e.getSimpleName().toString(), Function.identity(), (e1, e2) -> e1));

        var fields = typeElement.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .map(VariableElement.class::cast)
            .<DbEntity.EntityField>mapMulti((field, sink) -> {
                var fieldType = field.asType();
                var fieldName = field.getSimpleName().toString();
                var getterName = "get" + CommonUtils.capitalize(fieldName);
                var setterName = "set" + CommonUtils.capitalize(fieldName);
                var getter = methods.get(getterName);
                var setter = methods.get(setterName);
                if (getter == null || setter == null) {
                    return;
                }
                if (!getter.getParameters().isEmpty()) {
                    return;
                }
                if (setter.getParameters().size() != 1 || setter.getReturnType().getKind() != TypeKind.VOID) {
                    return;
                }
                if (!types.isSameType(getter.getReturnType(), fieldType)) {
                    return;
                }
                if (!types.isSameType(setter.getParameters().get(0).asType(), fieldType)) {
                    return;
                }
                var columnName = EntityUtils.parseColumnName(field, nameConverter);
                sink.accept(new DbEntity.EntityField(field, fieldType, columnName));
            })
            .toList();
        if (fields.isEmpty()) {
            return null;
        }
        return new DbEntity(typeMirror, typeElement, EntityType.BEAN, fields);
    }

}
