package ru.tinkoff.kora.database.annotation.processor.vertx.extension;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.GenericTypeResolver;
import ru.tinkoff.kora.common.annotation.Generated;
import ru.tinkoff.kora.database.annotation.processor.DbEntityReadHelper;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;
import ru.tinkoff.kora.database.annotation.processor.vertx.VertxNativeTypes;
import ru.tinkoff.kora.database.annotation.processor.vertx.VertxTypes;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//VertxRowSetMapper<List<T>>
//VertxRowMapper<T>

public class VertxTypesExtension implements KoraExtension {

    private final Types types;
    private final Elements elements;
    private final Filer filer;
    private final DbEntityReadHelper entityHelper;

    public VertxTypesExtension(ProcessingEnvironment env) {
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.filer = env.getFiler();
        this.entityHelper = new DbEntityReadHelper(
            VertxTypes.RESULT_COLUMN_MAPPER,
            this.types,
            fd -> CodeBlock.of("this.$L.apply(_row, _$LColumn)", fd.mapperFieldName(), fd.fieldName()),
            fd -> {
                var nativeType = VertxNativeTypes.find(TypeName.get(fd.type()));
                if (nativeType != null) {
                    return nativeType.extract("_row", "_" + fd.fieldName() + "Column");
                }
                return null;
            },
            fd -> fd.nullable() || fd.type().getKind().isPrimitive()
                ? CodeBlock.of("")
                : CodeBlock.builder().beginControlFlow("if ($N == null)", fd.fieldName())
                .add("throw new $T($S);\n", NullPointerException.class, "Result field %s is not nullable but row %s has null".formatted(fd.fieldName(), fd.columnName()))
                .endControlFlow()
                .build()
        );
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror) {
        if (!(typeMirror instanceof DeclaredType dt)) {
            return null;
        }
        if (typeMirror.toString().startsWith("ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper<")) {
            return this.generateResultSetMapper(roundEnvironment, dt);
        }
        if (typeMirror.toString().startsWith("ru.tinkoff.kora.database.vertx.mapper.result.VertxRowMapper<")) {
            return this.generateResultRowMapper(roundEnvironment, dt);
        }
        return null;
    }

    @Nullable
    private KoraExtensionDependencyGenerator generateResultRowMapper(RoundEnvironment roundEnvironment, DeclaredType typeMirror) {
        var rowType = typeMirror.getTypeArguments().get(0);
        var entity = DbEntity.parseEntity(this.types, rowType);
        if (entity == null) {
            return null;
        }
        var mapperName = CommonUtils.getOuterClassesAsPrefix(entity.typeElement()) + entity.typeElement().getSimpleName() + "VertxRowMapper";
        var packageElement = this.elements.getPackageOf(entity.typeElement());

        return () -> {
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
            var type = TypeSpec.classBuilder(mapperName)
                .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", "$S", VertxTypesExtension.class.getCanonicalName()).build())
                .addSuperinterface(ParameterizedTypeName.get(
                    VertxTypes.ROW_MAPPER, TypeName.get(entity.typeMirror())
                ))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
            var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

            var apply = MethodSpec.methodBuilder("apply")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(VertxTypes.ROW, "_row")
                .returns(TypeName.get(entity.typeMirror()));
            apply.addCode(this.readColumnIds(entity, "_row.getColumnIndex"));
            var read = this.entityHelper.readEntity("_result", entity);
            read.enrich(type, constructor);
            apply.addCode(read.block());
            apply.addCode("return _result;\n");

            type.addMethod(constructor.build());
            type.addMethod(apply.build());
            JavaFile.builder(packageElement.getQualifiedName().toString(), type.build()).build().writeTo(this.filer);
            return ExtensionResult.nextRound();
        };
    }

    private KoraExtensionDependencyGenerator generateResultSetMapper(RoundEnvironment roundEnvironment, DeclaredType typeMirror) {
        //VertxRowSetMapper<T>
        //VertxRowSetMapper<List<T>>
        var resultTypeMirror = typeMirror.getTypeArguments().get(0);
        var resultType = TypeName.get(resultTypeMirror);
        if (resultType.toString().startsWith("java.util.List<")) {
            var rowType = ((DeclaredType) resultTypeMirror).getTypeArguments().get(0);
            var dbEntity = DbEntity.parseEntity(this.types, rowType);
            if (dbEntity != null) {
                return this.entityListRowSetMapper(rowType, dbEntity);
            } else {
                return () -> {
                    var listResultSetMapper = this.elements.getTypeElement(VertxTypes.ROW_SET_MAPPER.canonicalName()).getEnclosedElements()
                        .stream()
                        .filter(e -> e.getKind() == ElementKind.METHOD && e.getModifiers().contains(Modifier.STATIC))
                        .map(ExecutableElement.class::cast)
                        .filter(m -> m.getSimpleName().contentEquals("listResultSetMapper"))
                        .findFirst()
                        .orElseThrow();
                    var tp = (TypeVariable) listResultSetMapper.getTypeParameters().get(0).asType();
                    var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, rowType), listResultSetMapper.asType());
                    return ExtensionResult.fromExecutable(listResultSetMapper, executableType);
                };
            }
        }
        var dbEntity = DbEntity.parseEntity(this.types, resultTypeMirror);
        if (dbEntity != null) {
            return this.rowSetMapper(resultTypeMirror, dbEntity);
        } else {
            return () -> {
                var singleResultSetMapper = this.elements.getTypeElement(VertxTypes.ROW_SET_MAPPER.canonicalName()).getEnclosedElements()
                    .stream()
                    .filter(e -> e.getKind() == ElementKind.METHOD && e.getModifiers().contains(Modifier.STATIC))
                    .map(ExecutableElement.class::cast)
                    .filter(m -> m.getSimpleName().contentEquals("singleRowSetMapper"))
                    .findFirst()
                    .orElseThrow();
                var tp = (TypeVariable) singleResultSetMapper.getTypeParameters().get(0).asType();
                var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, resultTypeMirror), singleResultSetMapper.asType());
                return ExtensionResult.fromExecutable(singleResultSetMapper, executableType);
            };
        }
    }

    private KoraExtensionDependencyGenerator rowSetMapper(TypeMirror rowTypeMirror, DbEntity dbEntity) {
        var packageElement = this.elements.getPackageOf(this.types.asElement(rowTypeMirror));
        var rowType = TypeName.get(rowTypeMirror);
        var rowTypeElement = this.types.asElement(rowTypeMirror);
        var mapperName = CommonUtils.getOuterClassesAsPrefix(rowTypeElement) + ((ClassName) rowType).simpleName() + "VertxRowSetMapper";

        return () -> {
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
            var readEntity = this.entityHelper.readEntity("_rowValue", dbEntity);
            var constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
            var type = TypeSpec.classBuilder(mapperName)
                .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", "$S", VertxTypesExtension.class.getCanonicalName()).build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ParameterizedTypeName.get(
                    VertxTypes.ROW_SET_MAPPER, rowType
                ));
            readEntity.enrich(type, constructor);

            var typeSpec = type
                .addMethod(MethodSpec.methodBuilder("apply")
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addAnnotation(Override.class)
                    .returns(rowType)
                    .addParameter(VertxTypes.ROW_SET, "_rs")
                    .addCode("if (_rs.rowCount() < 1) return null;\n")
                    .addCode("var _row = _rs.iterator().next();\n")
                    .addCode(this.readColumnIds(dbEntity, "_row.getColumnIndex"))
                    .addCode(readEntity.block())
                    .addCode("return _rowValue;\n")
                    .build())
                .addMethod(constructor.build())
                .build();
            JavaFile.builder(packageElement.getQualifiedName().toString(), typeSpec).build().writeTo(this.filer);
            return ExtensionResult.nextRound();
        };
    }

    private KoraExtensionDependencyGenerator entityListRowSetMapper(TypeMirror rowTypeMirror, DbEntity dbEntity) {
        var packageElement = this.elements.getPackageOf(this.types.asElement(rowTypeMirror));
        var rowType = TypeName.get(rowTypeMirror);
        var rowTypeElement = this.types.asElement(rowTypeMirror);
        var mapperName = CommonUtils.getOuterClassesAsPrefix(rowTypeElement) + ((ClassName) rowType).simpleName() + "ListVertxRowSetMapper";
        var returnType = ParameterizedTypeName.get(ClassName.get(List.class), rowType);

        return () -> {
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
            var readEntity = this.entityHelper.readEntity("_rowValue", dbEntity);
            var constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
            var type = TypeSpec.classBuilder(mapperName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", "$S", VertxTypesExtension.class.getCanonicalName()).build())
                .addSuperinterface(ParameterizedTypeName.get(
                    VertxTypes.ROW_SET_MAPPER, returnType
                ));
            readEntity.enrich(type, constructor);


            var typeSpec = type
                .addMethod(MethodSpec.methodBuilder("apply")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .returns(returnType)
                    .addParameter(VertxTypes.ROW_SET, "_rs")
                    .addCode(this.readColumnIds(dbEntity, "_rs.columnsNames().indexOf"))
                    .addCode("var _result = new $T<$T>(_rs.rowCount());\n", ArrayList.class, rowType)
                    .addCode("for (var _row : _rs) {$>\n")
                    .addCode(readEntity.block())
                    .addCode("_result.add(_rowValue);\n")
                    .addCode("$<\n}\n")
                    .addCode("return _result;\n")
                    .build())
                .addMethod(constructor.build())
                .build();
            JavaFile.builder(packageElement.getQualifiedName().toString(), typeSpec).build().writeTo(this.filer);
            return ExtensionResult.nextRound();
        };
    }

    private CodeBlock readColumnIds(DbEntity entity, String findCode) {
        var b = CodeBlock.builder();
        for (var entityField : entity.columns()) {
            var fieldName = entityField.variableName();
            b.add("var _$LColumn = $L($S);\n", fieldName, findCode, entityField.columnName());
        }
        return b.build();
    }
}
