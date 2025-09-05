package ru.tinkoff.kora.database.annotation.processor.cassandra;

import com.palantir.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.database.annotation.processor.DbEntityReadHelper;
import ru.tinkoff.kora.database.annotation.processor.cassandra.extension.CassandraTypesExtension;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraTypes.RESULT_SET;

public class CassandraEntityGenerator {
    private final Elements elements;
    private final Filer filer;
    private final DbEntityReadHelper rowMapperGenerator;

    public CassandraEntityGenerator(Types types, Elements elements, Filer filer) {
        this.elements = elements;
        this.filer = filer;
        this.rowMapperGenerator = new DbEntityReadHelper(
            CassandraTypes.RESULT_COLUMN_MAPPER,
            types,
            fd -> CodeBlock.of("this.$L.apply(_row, _idx_$L)", fd.mapperFieldName(), fd.fieldName()),
            fd -> {
                var nativeType = CassandraNativeTypes.findNativeType(TypeName.get(fd.type()));
                if (nativeType != null) {
                    return nativeType.extract("_row", CodeBlock.of("_idx_$L", fd.fieldName()));
                } else {
                    return null;
                }
            },
            fd -> CodeBlock.builder()
                .beginControlFlow("if (_row.isNull(_idx_$L))", fd.fieldName())
                .add(fd.nullable()
                    ? CodeBlock.of("$N = null;\n", fd.fieldName())
                    : CodeBlock.of("throw new $T($S);\n", NullPointerException.class, "Result field %s is not nullable but row %s has null".formatted(fd.fieldName(), fd.columnName()))
                )
                .endControlFlow()
                .build()
        );
    }

    public void generateRowMapper(DbEntity entity) throws IOException {
        var mapperName = rowMapperName(entity.typeElement());
        var packageElement = this.elements.getPackageOf(entity.typeElement());

        var type = TypeSpec.classBuilder(mapperName)
            .addOriginatingElement(entity.typeElement())
            .addAnnotation(AnnotationUtils.generated(CassandraTypesExtension.class))
            .addSuperinterface(ParameterizedTypeName.get(
                CassandraTypes.ROW_MAPPER, TypeName.get(entity.typeMirror())
            ))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        var apply = MethodSpec.methodBuilder("apply")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(CassandraTypes.ROW, "_row")
            .returns(TypeName.get(entity.typeMirror()));
        var read = this.rowMapperGenerator.readEntity("_result", entity);
        read.enrich(type, constructor);
        for (var field : entity.columns()) {
            apply.addCode("var _idx_$L = _row.firstIndexOf($S);\n", field.variableName(), field.columnName());
        }
        apply.addCode(read.block());
        apply.addCode("return _result;\n");

        type.addMethod(constructor.build());
        type.addMethod(apply.build());

        JavaFile.builder(packageElement.getQualifiedName().toString(), type.build()).build().writeTo(this.filer);
    }

    public void generateResultSetMapper(DbEntity entity) throws IOException {
        var rowTypeElement = entity.typeElement();
        var packageElement = this.elements.getPackageOf(rowTypeElement);
        var mapperName = this.resultSetMapperName(rowTypeElement);
        var rowTypeName = TypeName.get(entity.typeMirror());

        var type = TypeSpec.classBuilder(mapperName)
            .addOriginatingElement(entity.typeElement())
            .addAnnotation(AnnotationUtils.generated(CassandraTypesExtension.class))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(
                CassandraTypes.RESULT_SET_MAPPER, rowTypeName
            ));
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        var apply = MethodSpec.methodBuilder("apply")
            .addAnnotation(Override.class)
            .addAnnotation(CommonClassNames.nullable)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .returns(rowTypeName)
            .addParameter(RESULT_SET, "_rs");
        apply.addStatement("var _it = _rs.iterator()");
        apply.beginControlFlow("if (!_it.hasNext())");
        apply.addStatement("return null");
        apply.endControlFlow();
        for (var field : entity.columns()) {
            apply.addCode("var _idx_$L = _rs.getColumnDefinitions().firstIndexOf($S);\n", field.variableName(), field.columnName());
        }
        apply.addStatement("var _row = _it.next()");
        var read = this.rowMapperGenerator.readEntity("_result", entity);
        read.enrich(type, constructor);
        apply.addCode(read.block());
        // TODO in 2.0 we should check next and throw exception if result set has more then one result
        apply.addCode("return _result;\n");

        var typeSpec = type.addMethod(apply.build())
            .addMethod(constructor.build())
            .build();
        JavaFile.builder(packageElement.getQualifiedName().toString(), typeSpec).build().writeTo(this.filer);
    }

    public void generateListResultSetMapper(DbEntity entity) throws IOException {
        var rowTypeElement = entity.typeElement();
        var packageElement = this.elements.getPackageOf(rowTypeElement);
        var mapperName = this.listResultSetMapperName(rowTypeElement);
        var listType = ParameterizedTypeName.get(ClassName.get(List.class), TypeName.get(entity.typeMirror()));

        var type = TypeSpec.classBuilder(mapperName)
            .addOriginatingElement(entity.typeElement())
            .addAnnotation(AnnotationUtils.generated(CassandraTypesExtension.class))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(
                CassandraTypes.RESULT_SET_MAPPER, listType
            ));
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        var apply = MethodSpec.methodBuilder("apply")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .returns(listType)
            .addParameter(RESULT_SET, "_rs");
        var read = this.rowMapperGenerator.readEntity("_rowValue", entity);
        read.enrich(type, constructor);
        for (var field : entity.columns()) {
            apply.addCode("var _idx_$L = _rs.getColumnDefinitions().firstIndexOf($S);\n", field.variableName(), field.columnName());
        }
        apply.addCode("var _result = new $T<$T>(_rs.getAvailableWithoutFetching());\n", ArrayList.class, entity.typeMirror());
        apply.beginControlFlow("for (var _row : _rs)");
        apply.addCode(read.block());
        apply.addCode("_result.add(_rowValue);\n");
        apply.endControlFlow();
        apply.addCode("return _result;\n");

        var typeSpec = type.addMethod(apply.build())
            .addMethod(constructor.build())
            .build();
        JavaFile.builder(packageElement.getQualifiedName().toString(), typeSpec).build().writeTo(this.filer);
    }

    public ClassName rowMapperName(Element rowTypeElement) {
        var packageElement = this.elements.getPackageOf(rowTypeElement);
        return ClassName.get(packageElement.getQualifiedName().toString(), NameUtils.generatedType(rowTypeElement, CassandraTypes.ROW_MAPPER));
    }

    public ClassName resultSetMapperName(Element rowTypeElement) {
        var packageElement = this.elements.getPackageOf(rowTypeElement);
        return ClassName.get(packageElement.getQualifiedName().toString(), NameUtils.generatedType(rowTypeElement, CassandraTypes.RESULT_SET_MAPPER));
    }

    public ClassName listResultSetMapperName(Element rowTypeElement) {
        var packageElement = this.elements.getPackageOf(rowTypeElement);
        return ClassName.get(packageElement.getQualifiedName().toString(), NameUtils.generatedType(rowTypeElement, "ListCassandraResultSetMapper"));
    }
}
