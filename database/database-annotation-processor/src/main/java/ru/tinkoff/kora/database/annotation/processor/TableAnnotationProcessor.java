package ru.tinkoff.kora.database.annotation.processor;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.common.annotation.Generated;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcNativeTypes;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcTypes;
import ru.tinkoff.kora.database.annotation.processor.jdbc.extension.JdbcTypesExtension;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class TableAnnotationProcessor extends AbstractKoraProcessor {
    private DbEntityReadHelper rowMapperGenerator;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("ru.tinkoff.kora.database.common.annotation.Table");
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.rowMapperGenerator = new DbEntityReadHelper(
            JdbcTypes.RESULT_COLUMN_MAPPER,
            this.types,
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

    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var annotation : annotations) {
            for (var element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element.getKind() != ElementKind.RECORD) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@Table only works on records");
                    continue;
                }
                try {
                    this.generate((TypeElement) element);
                } catch (ProcessingErrorException e) {
                    e.printError(processingEnv);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return false;
    }

    private void generate(TypeElement element) throws Exception {
        var entity = Objects.requireNonNull(DbEntity.parseEntity(this.types, element.asType()));

        this.generateRowMapper(entity);
        this.generateListResultSetMapper(entity);
    }

    private void generateListResultSetMapper(DbEntity entity) throws IOException {
        var mapperName = NameUtils.generatedType(entity.typeElement(), "ListJdbcResultSetMapper");
        var packageElement = this.elements.getPackageOf(entity.typeElement());
        var type = TypeSpec.classBuilder(mapperName)
            .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", "$S", JdbcTypesExtension.class.getCanonicalName()).build())
            .addSuperinterface(ParameterizedTypeName.get(
                JdbcTypes.RESULT_SET_MAPPER, ParameterizedTypeName.get(ClassName.get(List.class), TypeName.get(entity.typeMirror()))
            ))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(TypeName.get(ResultSet.class), "_rs")
            .addException(TypeName.get(SQLException.class))
            .addAnnotation(Nullable.class)
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
        JavaFile.builder(packageElement.getQualifiedName().toString(), type.build()).build().writeTo(this.processingEnv.getFiler());
    }

    private void generateRowMapper(DbEntity entity) throws IOException {
        var mapperName = NameUtils.generatedType(entity.typeElement(), JdbcTypes.ROW_MAPPER);
        var packageElement = this.elements.getPackageOf(entity.typeElement());
        var type = TypeSpec.classBuilder(mapperName)
            .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", "$S", JdbcTypesExtension.class.getCanonicalName()).build())
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
        JavaFile.builder(packageElement.getQualifiedName().toString(), type.build()).build().writeTo(this.processingEnv.getFiler());

    }


    private CodeBlock readColumnIds(DbEntity entity) {
        var b = CodeBlock.builder();
        for (var entityField : entity.columns()) {
            var fieldName = entityField.variableName();
            b.add("var _$LColumn = _rs.findColumn($S);\n", fieldName, entityField.columnName());
        }
        return b.build();
    }

}
