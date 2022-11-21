package ru.tinkoff.kora.database.annotation.processor.model;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.common.naming.NameConverter;
import ru.tinkoff.kora.database.annotation.processor.DbUtils;
import ru.tinkoff.kora.database.annotation.processor.EntityUtils;

import javax.annotation.Nullable;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.getNameConverter;

public record TemplateModel(
    TypeElement type, String tableName, LinkedHashMap<String, ColumnModel> columns) {

    public static record ColumnModel(
        VariableElement field, TypeMirror type, String columnName, boolean isId
    ) {}

    public static record TemplateParam(@Nullable String paramName, String template, String rawTemplate) {}

    /**
     * {table_name} <br/>
     * {id_field} <br/>
     * {id_column} <br/>
     * {id_field_*} <br/>
     * {id_column_*} <br/>
     * {columns} <br/>
     * {non_id_columns} <br/>
     * <br/>
     * {non_id_placeholders} <br/>
     * {update_columns} <br/>
     */
    public Map<String, String> getTemplateParams(@Nullable String entityParamName) {
        var result = new HashMap<String, String>();
        result.put("table_name", tableName());
        var nonIdColumns = new ArrayList<ColumnModel>();
        var columns = new ArrayList<String>();
        {
            int idColumnsCounter = 0;
            for (var column : columns().values()) {
                columns.add(column.columnName());
                if (column.isId()) {
                    idColumnsCounter++;
                    result.put("id_field_" + idColumnsCounter, column.field().getSimpleName().toString());
                    result.put("id_column_" + idColumnsCounter, column.columnName());
                    if (idColumnsCounter == 1) {
                        result.put("id_field", column.field().getSimpleName().toString());
                        result.put("id_column", column.columnName());
                    }
                } else {
                    nonIdColumns.add(column);
                }
            }
        }
        result.put("columns", String.join(", ", columns));
        result.put("non_id_columns", nonIdColumns.stream().map(ColumnModel::columnName).collect(Collectors.joining(", ")));
        if (entityParamName != null) {
            var nonIdPlaceholders = new ArrayList<String>();
            for (var nonIdColumn : nonIdColumns) {
                nonIdPlaceholders.add(":%s.%s".formatted(entityParamName, nonIdColumn.field().getSimpleName()));
            }
            result.put("non_id_placeholders", String.join(", ", nonIdPlaceholders));
            var updateColumn = new ArrayList<String>();
            for (var nonIdColumn : nonIdColumns) {
                updateColumn.add("%s = :%s.%s".formatted(nonIdColumn.columnName(), entityParamName, nonIdColumn.field().getSimpleName()));
            }
            result.put("update_columns", String.join(", ", updateColumn));
        }
        return result;
    }

    public static TemplateModel parseEntityModel(Elements elements, Types types, TypeElement type) {
        var fullEntity = findFullEntity(elements, types, type);

        var targetType = fullEntity.orElse(type);

        var tableName = parseTableName(targetType);
        NameConverter columnsNameConverter = getNameConverter(targetType);

        var columns = parseColumns(elements, types, targetType, columnsNameConverter);

        NameConverter finalColumnsNameConverter = columnsNameConverter;
        var resultColumns = fullEntity
            .map((e) -> parseColumns(elements, types, type, finalColumnsNameConverter).keySet())
            .map((partialTypeColumns) -> columns.keySet().stream()
                .filter(c -> partialTypeColumns.contains(c) || columns.get(c).isId())
                .collect(Collectors.toMap((k) -> k, columns::get, (v1, v2) -> v1, LinkedHashMap::new))
            )
            .orElse(columns);

        return new TemplateModel(type, tableName, resultColumns);
    }

    public static List<TemplateParam> detectTemplateParams(String sql) {
        var processedTemplates = new HashSet<String>();
        var result = new ArrayList<TemplateParam>();
        var paramRegex = Pattern.compile("(?<raw>\\{((?<paramName>[\\w_][\\w\\d_]*)\\.)?(?<template>[\\w\\d_]+)})");
        var matcher = paramRegex.matcher(sql);
        while (matcher.find()) {
            String rawTemplate = matcher.group("raw");
            if (processedTemplates.contains(rawTemplate)) {
                continue;
            }

            processedTemplates.add(rawTemplate);
            result.add(new TemplateParam(matcher.group("paramName"), matcher.group("template"), rawTemplate));
        }

        return result;
    }

    private static Optional<TypeElement> findFullEntity(Elements elements, Types types, TypeElement type) {
        return type.getInterfaces()
            .stream()
            .filter((iface) -> types.erasure(iface).toString().equals(DbUtils.SUB_ENTITY_OF_ANNOTATION.canonicalName()))
            .findFirst()
            .map((subEntityOf) -> ((DeclaredType) subEntityOf).getTypeArguments().get(0))
            .filter((fullType) -> fullType instanceof DeclaredType)
            .map((fullType) -> ((TypeElement) ((DeclaredType) fullType).asElement()));
    }

    private static String parseTableName(TypeElement type) {
        var table = CommonUtils.findDirectAnnotation(type, DbUtils.TABLE_ANNOTATION);
        if (table != null) {
            return CommonUtils.parseAnnotationValueWithoutDefault(table, "value").toString();
        }
        return type.getSimpleName().toString();
    }

    private static LinkedHashMap<String, ColumnModel> parseColumns(Elements elements, Types types, TypeElement type, NameConverter columnsNameConverter) {
        var constructor = EntityUtils.findEntityConstructor(type);

        return constructor.getParameters().isEmpty()
            ? parseMutableEntityColumns(elements, types, type, columnsNameConverter)
            : parseImmutableEntityColumns(elements, types, constructor, type, columnsNameConverter);
    }

    private static LinkedHashMap<String, ColumnModel> parseMutableEntityColumns(Elements elements, Types types, TypeElement type, NameConverter columnsNameConverter) {
        var fields = type.getEnclosedElements()
            .stream()
            .filter(f -> f.getKind() == ElementKind.FIELD)
            .map(VariableElement.class::cast)
            .toList();
        var result = new LinkedHashMap<String, ColumnModel>();
        for (var field : fields) {
            var columnType = field.asType();
            var columnName = EntityUtils.parseColumnName(field, columnsNameConverter);
            var isId = CommonUtils.findDirectAnnotation(field, DbUtils.ID_ANNOTATION) != null;
            result.put(field.getSimpleName().toString(), new ColumnModel(field, columnType, columnName, isId));
        }
        return result;
    }

    private static LinkedHashMap<String, ColumnModel> parseImmutableEntityColumns(Elements elements, Types types, ExecutableElement constructor, TypeElement type, NameConverter columnsNameConverter) {
        var constructorParameters = constructor.getParameters();
        var result = new LinkedHashMap<String, ColumnModel>(constructorParameters.size());
        for (var constructorParameter : constructorParameters) {
            var field = findField(type, constructorParameter);
            var columnType = field.asType();
            var columnName = EntityUtils.parseColumnName(constructorParameter, columnsNameConverter);
            // column _must_ be simple type
            var isId = CommonUtils.findDirectAnnotation(constructorParameter, DbUtils.ID_ANNOTATION) != null;
            result.put(field.getSimpleName().toString(), new ColumnModel(field, columnType, columnName, isId));
        }
        return result;
    }

    private static VariableElement findField(TypeElement type, VariableElement constructorParameter) {
        return type.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .map(VariableElement.class::cast)
            .filter(v -> v.getSimpleName().equals(constructorParameter.getSimpleName()))
            .findFirst()
            .orElseThrow();
    }

}
