package io.koraframework.database.annotation.processor.cassandra;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.GeneratedHolder;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.database.annotation.processor.DbEntityReadHelper;
import io.koraframework.database.annotation.processor.cassandra.extension.CassandraTypesExtension;
import io.koraframework.database.annotation.processor.entity.DbEntity;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.koraframework.database.annotation.processor.cassandra.CassandraTypes.RESULT_SET;

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

    /**
     * All the mappers of an entity are written as nested classes of one holder: the number of generated source files matters for compilation time
     */
    public void generate(DbEntity entity) throws IOException {
        var holderName = holderName(entity.typeElement());
        var holder = GeneratedHolder.classBuilder(holderName, CassandraTypesExtension.class)
            .addOriginatingElement(entity.typeElement())
            .addType(this.generateRowMapper(entity))
            .addType(this.generateResultSetMapper(entity))
            .addType(this.generateListResultSetMapper(entity))
            .build();
        JavaFile.builder(holderName.packageName(), holder).build().writeTo(this.filer);
    }

    public TypeSpec generateRowMapper(DbEntity entity) {

        var type = TypeSpec.classBuilder("RowMapper")
            .addOriginatingElement(entity.typeElement())
            .addAnnotation(AnnotationUtils.generated(CassandraTypesExtension.class))
            .addSuperinterface(ParameterizedTypeName.get(
                CassandraTypes.ROW_MAPPER, TypeName.get(entity.typeMirror())
            ))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
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

        return type.build();
    }

    public TypeSpec generateResultSetMapper(DbEntity entity) {
        var rowTypeName = TypeName.get(entity.typeMirror());

        var type = TypeSpec.classBuilder("ResultSetMapper")
            .addOriginatingElement(entity.typeElement())
            .addAnnotation(AnnotationUtils.generated(CassandraTypesExtension.class))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(
                CassandraTypes.RESULT_SET_MAPPER, rowTypeName
            ));
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        var apply = MethodSpec.methodBuilder("apply")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .returns(rowTypeName.annotated(CommonClassNames.nullableAnnotation))
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

        return type.addMethod(apply.build())
            .addMethod(constructor.build())
            .build();
    }

    public TypeSpec generateListResultSetMapper(DbEntity entity) {
        var listType = ParameterizedTypeName.get(ClassName.get(List.class), TypeName.get(entity.typeMirror()));

        var type = TypeSpec.classBuilder("ListResultSetMapper")
            .addOriginatingElement(entity.typeElement())
            .addAnnotation(AnnotationUtils.generated(CassandraTypesExtension.class))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
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

        return type.addMethod(apply.build())
            .addMethod(constructor.build())
            .build();
    }

    /**
     * Mappers of an entity are generated as nested classes of a single holder, e.g. <code>$Entity_Cassandra.RowMapper</code>
     */
    public static final String HOLDER_POSTFIX = "Cassandra";
    public static final String ROW_MAPPER_NAME = "RowMapper";
    public static final String RESULT_SET_MAPPER_NAME = "ResultSetMapper";
    public static final String LIST_RESULT_SET_MAPPER_NAME = "ListResultSetMapper";

    public ClassName holderName(Element rowTypeElement) {
        return GeneratedHolder.name(this.elements, rowTypeElement, HOLDER_POSTFIX);
    }
}
