package io.koraframework.database.annotation.processor.jdbc;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.database.annotation.processor.DbEntityReadHelper;
import io.koraframework.database.annotation.processor.entity.DbEntity;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class JdbcEntityGenerator {
    private final DbEntityReadHelper rowMapperGenerator;
    private final Elements elements;
    private final Filer filer;

    public JdbcEntityGenerator(Types types, Elements elements, Filer filer) {
        this.rowMapperGenerator = new DbEntityReadHelper(
            JdbcTypes.RESULT_COLUMN_MAPPER,
            types,
            fd -> CodeBlock.of("this.$L.apply(_rs, _$LColumn)", fd.mapperFieldName(), fd.fieldName()),
            fd -> {
                var nativeType = JdbcNativeTypes.findNativeType(TypeName.get(fd.type()));
                if (nativeType != null) {
                    return nativeType.extract("_rs", CodeBlock.of("_$LColumn", fd.fieldName()));
                } else {
                    return null;
                }
            },
            fd -> CodeBlock.builder().beginControlFlow("if (_rs.wasNull())")
                .add(fd.nullable()
                    ? CodeBlock.of("$N = null;\n", fd.fieldName())
                    : CodeBlock.of("throw new $T($S);\n", NullPointerException.class, "Result field %s is not nullable but row %s has null".formatted(fd.fieldName(), fd.columnName())))
                .endControlFlow()
                .build()
        );
        this.elements = elements;
        this.filer = filer;
    }

    public ClassName listJdbcResultSetMapperName(TypeElement entityTypeElement) {
        var mapperName = NameUtils.generatedType(entityTypeElement, "ListJdbcResultSetMapper");
        var packageElement = this.elements.getPackageOf(entityTypeElement);

        return ClassName.get(packageElement.getQualifiedName().toString(), mapperName);
    }

    public ClassName resultSetMapperName(TypeElement entityTypeElement) {
        var mapperName = NameUtils.generatedType(entityTypeElement, JdbcTypes.RESULT_SET_MAPPER);
        var packageElement = this.elements.getPackageOf(entityTypeElement);

        return ClassName.get(packageElement.getQualifiedName().toString(), mapperName);
    }

    public ClassName rowMapperName(TypeElement entityTypeElement) {
        var mapperName = NameUtils.generatedType(entityTypeElement, JdbcTypes.ROW_MAPPER);
        var packageElement = this.elements.getPackageOf(entityTypeElement);

        return ClassName.get(packageElement.getQualifiedName().toString(), mapperName);
    }


    public void generateListResultSetMapper(DbEntity entity) throws IOException {
        if (entity.hasEmbeddedCollection()) {
            generateAggregatingListResultSetMapper(entity);
            return;
        }

        var mapperClassName = listJdbcResultSetMapperName(entity.typeElement());

        var type = TypeSpec.classBuilder(mapperClassName)
            .addOriginatingElement(entity.typeElement())
            .addAnnotation(AnnotationUtils.generated(JdbcEntityGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(
                JdbcTypes.RESULT_SET_MAPPER, ParameterizedTypeName.get(ClassName.get(List.class), TypeName.get(entity.typeMirror()))
            ))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(TypeName.get(ResultSet.class), "_rs")
            .addException(TypeName.get(SQLException.class))
            .returns(ParameterizedTypeName.get(ClassName.get(List.class), TypeName.get(entity.typeMirror())));
        apply.addCode("if (!_rs.next()) {\n  return $T.of();\n}\n", List.class);
        apply.addCode(this.readColumnIds(entity));
        var row = this.rowMapperGenerator.readEntity("_row", entity);
        row.enrich(type, constructor);
        apply.addCode("var _result = new $T<$T>();\n", ArrayList.class, entity.typeMirror());
        apply.addCode("do {$>\n");
        apply.addCode(row.block());
        apply.addCode("_result.add(_row);\n");
        apply.addCode("$<\n} while (_rs.next());\n");
        apply.addCode("return _result;\n");

        type.addMethod(constructor.build());
        type.addMethod(apply.build());


        JavaFile.builder(mapperClassName.packageName(), type.build()).build().writeTo(this.filer);
    }

    private void generateAggregatingListResultSetMapper(DbEntity entity) throws IOException {
        var collections = entity.embeddedCollections();
        if (collections.size() != 1) {
            var errorElement = collections.isEmpty()
                ? entity.rootErrorElement()
                : collections.get(1).element();
            throw new ProcessingErrorException(embeddedCollectionCountError(entity, collections.size()), errorElement);
        }
        var rootIdColumns = entity.rootIdColumns();
        if (rootIdColumns.isEmpty()) {
            throw new ProcessingErrorException(missingRootIdError(entity), entity.rootErrorElement());
        }
        var collection = collections.get(0);
        var mapperClassName = listJdbcResultSetMapperName(entity.typeElement());

        var type = TypeSpec.classBuilder(mapperClassName)
            .addOriginatingElement(entity.typeElement())
            .addAnnotation(AnnotationUtils.generated(JdbcEntityGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(
                JdbcTypes.RESULT_SET_MAPPER, ParameterizedTypeName.get(ClassName.get(List.class), TypeName.get(entity.typeMirror()))
            ))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(TypeName.get(ResultSet.class), "_rs")
            .addException(TypeName.get(SQLException.class))
            .returns(ParameterizedTypeName.get(ClassName.get(List.class), TypeName.get(entity.typeMirror())));
        apply.addCode("if (!_rs.next()) {\n  return $T.of();\n}\n", List.class);
        apply.addCode(this.readColumnIds(entity));
        var row = this.rowMapperGenerator.readEntity("_row", entity);
        row.enrich(type, constructor);
        apply.addCode("var _result = new $T<$T>();\n", ArrayList.class, entity.typeMirror());
        apply.addCode("var _index = new $T<$T<$T>, $T>();\n", LinkedHashMap.class, List.class, Object.class, entity.typeMirror());
        apply.addCode("do {$>\n");
        apply.addCode(row.block());
        var key = CodeBlock.builder();
        key.add("$T<$T> _key = $T.of(", List.class, Object.class, List.class);
        for (int i = 0; i < rootIdColumns.size(); i++) {
            if (i > 0) {
                key.add(", ");
            }
            key.add("$N", rootIdColumns.get(i).variableName());
        }
        key.add(");\n");
        apply.addCode(key.build());
        apply.addCode("var _existing = _index.get(_key);\n");
        apply.addCode("if (_existing == null) {$>\n");
        apply.addCode("_index.put(_key, _row);\n");
        apply.addCode("_result.add(_row);\n");
        apply.addCode("$<\n} else {$>\n");
        apply.addCode("_existing.$L.addAll(_row.$L);\n", collection.recordAccessor(), collection.recordAccessor());
        apply.addCode("$<\n}\n");
        apply.addCode("$<\n} while (_rs.next());\n");
        apply.addCode("return _result;\n");

        type.addMethod(constructor.build());
        type.addMethod(apply.build());

        JavaFile.builder(mapperClassName.packageName(), type.build()).build().writeTo(this.filer);
    }

    public void generateRowMapper(DbEntity entity) throws IOException {
        var mapperName = rowMapperName(entity.typeElement());
        var type = TypeSpec.classBuilder(mapperName)
            .addOriginatingElement(entity.typeElement())
            .addAnnotation(AnnotationUtils.generated(JdbcEntityGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(
                JdbcTypes.ROW_MAPPER, TypeName.get(entity.typeMirror())
            ))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(TypeName.get(ResultSet.class), "_rs")
            .addException(TypeName.get(SQLException.class))
            .returns(TypeName.get(entity.typeMirror()));
        apply.addCode(this.readColumnIds(entity));
        var read = this.rowMapperGenerator.readEntity("_result", entity);
        read.enrich(type, constructor);
        apply.addCode(read.block());
        apply.addCode("return _result;\n");

        type.addMethod(constructor.build());
        type.addMethod(apply.build());
        JavaFile.builder(mapperName.packageName(), type.build()).build().writeTo(this.filer);
    }


    private CodeBlock readColumnIds(DbEntity entity) {
        var b = CodeBlock.builder();
        for (var entityField : entity.columns()) {
            var fieldName = entityField.variableName();
            b.add("var _$LColumn = _rs.findColumn($S);\n", fieldName, entityField.columnName());
        }
        return b.build();
    }

    public void generateResultSetMapper(DbEntity entity) throws IOException {
        var mapperName = resultSetMapperName(entity.typeElement());
        var type = TypeSpec.classBuilder(mapperName)
            .addOriginatingElement(entity.typeElement())
            .addAnnotation(AnnotationUtils.generated(JdbcEntityGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(
                JdbcTypes.RESULT_SET_MAPPER, TypeName.get(entity.typeMirror())
            ))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(TypeName.get(ResultSet.class), "_rs")
            .addException(TypeName.get(SQLException.class))
            .returns(TypeName.get(entity.typeMirror()).annotated(CommonClassNames.nullableAnnotation));
        apply.addCode("if (!_rs.next()) {\n  return null;\n}\n");
        apply.addCode(this.readColumnIds(entity));
        var read = this.rowMapperGenerator.readEntity("_result", entity);
        read.enrich(type, constructor);
        apply.addCode(read.block());
        apply.addCode("if (_rs.next()) {\n  throw new IllegalStateException($S);\n}\n", "ResultSet was expected to return zero or one row but got two or more");
        apply.addCode("return _result;\n");

        type.addMethod(constructor.build());
        type.addMethod(apply.build());
        JavaFile.builder(mapperName.packageName(), type.build()).build().writeTo(this.filer);
    }

    private static String embeddedCollectionCountError(DbEntity entity, int collectionCount) {
        return """
            Invalid JDBC one-to-many entity mapper for `%s`.

            `JdbcResultSetMapper<List<%s>>` supports exactly one `@Embedded` collection field, but found %d.
            Multiple collection joins cannot be safely aggregated into a single result shape.

            Fix: keep one `@Embedded` collection in this entity, split the query into separate repository methods, or map the result manually with a custom mapper.
            """.formatted(entity.typeElement(), entity.typeElement().getSimpleName(), collectionCount);
    }

    private static String missingRootIdError(DbEntity entity) {
        return """
            Invalid JDBC one-to-many entity mapper for `%s`.

            The root entity must have an `@Id` column outside the embedded collection.
            Root fields: %s

            Fix: add `@Id` to one of the root fields, or to a field inside an embedded root object.
            Do not put the only `@Id` on the `@Embedded` collection element.
            """.formatted(entity.typeElement(), entity.rootFieldsDescription());
    }
}
