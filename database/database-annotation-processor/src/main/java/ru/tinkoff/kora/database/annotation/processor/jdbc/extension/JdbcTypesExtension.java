package ru.tinkoff.kora.database.annotation.processor.jdbc.extension;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.GenericTypeResolver;
import ru.tinkoff.kora.common.annotation.Generated;
import ru.tinkoff.kora.database.annotation.processor.DbEntityReadHelper;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcNativeTypes;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcTypes;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.Nullable;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// JdbcRowMapper<T>
public class JdbcTypesExtension implements KoraExtension {
    private static final ClassName LIST_CLASS_NAME = ClassName.get(List.class);

    private final Types types;
    private final Elements elements;
    private final Filer filer;
    private final DbEntityReadHelper rowMapperGenerator;

    public JdbcTypesExtension(ProcessingEnvironment env) {
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.filer = env.getFiler();
        this.rowMapperGenerator = new DbEntityReadHelper(
            JdbcTypes.RESULT_COLUMN_MAPPER,
            this.types,
            fd -> CodeBlock.of("this.$L.apply(_rs, _$LColumn)", fd.mapperFieldName(), fd.fieldName()),
            fd -> {
                var nativeType = JdbcNativeTypes.findNativeType(TypeName.get(fd.type()));
                if (nativeType != null) {
                    var cb = CodeBlock.builder().add(nativeType.extract("_rs", CodeBlock.of("_$LColumn", fd.fieldName())));
                    if (fd.nullable()) {
                        cb.add(";\n");
                        cb.beginControlFlow("if (_rs.wasNull())");
                        cb.addStatement("$L = null", fd.fieldName());
                        cb.endControlFlow();
                    }
                    return cb.build();
                } else {
                    return null;
                }
            },
            fd -> CodeBlock.of("""
                if (_rs.wasNull()) {
                  throw new $T($S);
                }
                """, NullPointerException.class, "Result field %s is not nullable but row has null".formatted(fd.fieldName()))
        );

    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror) {
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return null;
        }
        var typeName = TypeName.get(typeMirror);
        if (!(typeName instanceof ParameterizedTypeName ptn)) {
            return null;
        }
        if (Objects.equals(ptn.rawType, JdbcTypes.ROW_MAPPER)) {
            var rowTypeMirror = declaredType.getTypeArguments().get(0);
            var entity = DbEntity.parseEntity(this.types, rowTypeMirror);
            if (entity != null) {
                return this.entityRowMapper(entity);
            }
            return null;
        }

        if (Objects.equals(ptn.rawType, JdbcTypes.RESULT_SET_MAPPER)) {
            var resultTypeName = ptn.typeArguments.get(0);
            var resultTypeMirror = declaredType.getTypeArguments().get(0);
            if (resultTypeName instanceof ParameterizedTypeName rptn && rptn.rawType.equals(LIST_CLASS_NAME) && resultTypeMirror instanceof DeclaredType resultDeclaredType) {
                var rowTypeMirror = resultDeclaredType.getTypeArguments().get(0);
                var entity = DbEntity.parseEntity(this.types, rowTypeMirror);
                if (entity != null) {
                    return this.entityResultListSetMapper(entity);
                } else {
                    return () -> {
                        var listResultSetMapper = this.elements.getTypeElement(JdbcTypes.RESULT_SET_MAPPER.canonicalName()).getEnclosedElements()
                            .stream()
                            .filter(e -> e.getKind() == ElementKind.METHOD && e.getModifiers().contains(Modifier.STATIC))
                            .map(ExecutableElement.class::cast)
                            .filter(m -> m.getSimpleName().contentEquals("listResultSetMapper"))
                            .findFirst()
                            .orElseThrow();
                        var tp = (TypeVariable) listResultSetMapper.getTypeParameters().get(0).asType();
                        var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, rowTypeMirror), listResultSetMapper.asType());
                        return ExtensionResult.fromExecutable(listResultSetMapper, executableType);
                    };
                }
            }
        }
        return null;
    }

    private KoraExtension.KoraExtensionDependencyGenerator entityRowMapper(DbEntity entity) {
        return () -> {
            var mapperName = CommonUtils.getOuterClassesAsPrefix(entity.typeElement()) + entity.typeElement().getSimpleName() + "_JdbcRowMapper";
            var packageElement = this.elements.getPackageOf(entity.typeElement());
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
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
            JavaFile.builder(packageElement.getQualifiedName().toString(), type.build()).build().writeTo(this.filer);
            return ExtensionResult.nextRound();
        };
    }

    private KoraExtension.KoraExtensionDependencyGenerator entityResultListSetMapper(DbEntity entity) {
        return () -> {
            var mapperName = CommonUtils.getOuterClassesAsPrefix(entity.typeElement()) + entity.typeElement().getSimpleName() + "_ListJdbcResultSetMapper";
            var packageElement = this.elements.getPackageOf(entity.typeElement());
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
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
            JavaFile.builder(packageElement.getQualifiedName().toString(), type.build()).build().writeTo(this.filer);
            return ExtensionResult.nextRound();
        };
    }

    private CodeBlock readColumnIds(DbEntity entity) {
        var b = CodeBlock.builder();
        for (var entityField : entity.entityFields()) {
            var fieldName = entityField.element().getSimpleName().toString();
            b.add("var _$LColumn = _rs.findColumn($S);\n", fieldName, entityField.columnName());
        }
        return b.build();
    }
}
