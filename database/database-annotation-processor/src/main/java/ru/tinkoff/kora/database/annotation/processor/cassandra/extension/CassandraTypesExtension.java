package ru.tinkoff.kora.database.annotation.processor.cassandra.extension;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.database.annotation.processor.DbEntityReadHelper;
import ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraNativeTypes;
import ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraTypes;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.Nullable;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;

import static ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraTypes.RESULT_SET;

//CassandraRowMapper<T>
//CassandraResultSetMapper<List<T>> TODO

public class CassandraTypesExtension implements KoraExtension {

    private final Types types;
    private final Elements elements;
    private final Filer filer;
    private final DbEntityReadHelper rowMapperGenerator;

    public CassandraTypesExtension(ProcessingEnvironment env) {
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.filer = env.getFiler();
        this.rowMapperGenerator = new DbEntityReadHelper(
            CassandraTypes.RESULT_COLUMN_MAPPER,
            this.types,
            fd -> CodeBlock.of("this.$L.apply(_row, _idx_$L)", fd.mapperFieldName(), fd.fieldName()),
            fd -> {
                var nativeType = CassandraNativeTypes.findNativeType(TypeName.get(fd.type()));
                if (nativeType != null) {
                    return nativeType.extract("_row", CodeBlock.of("_idx_$L", fd.fieldName()));
                } else {
                    return null;
                }
            },
            fd -> CodeBlock.of("""
                if (_row.isNull($S)) {
                  throw new $T($S);
                }
                """, fd.columnName(), NullPointerException.class, "Result field %s is not nullable but row has null".formatted(fd.fieldName()))
        );
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror) {
        if (!(typeMirror instanceof DeclaredType dt)) {
            return null;
        }
        if (typeMirror.toString().startsWith("ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper<")) {
            return this.generateResultSetMapper(roundEnvironment, dt);
        }
        if (typeMirror.toString().startsWith("ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper<")) {
            return this.generateResultRowMapper(roundEnvironment, dt);
        }
        if (typeMirror.toString().startsWith("ru.tinkoff.kora.database.cassandra.mapper.parameter.CassandraParameterColumnMapper<")) {
            return this.generateParameterColumnMapper(roundEnvironment, dt);
        }
        if (typeMirror.toString().startsWith("ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowColumnMapper<")) {
            return this.generateRowColumnMapper(roundEnvironment, dt);
        }
        return null;
    }

    private KoraExtensionDependencyGenerator generateRowColumnMapper(RoundEnvironment roundEnvironment, DeclaredType dt) {
        var entityType = dt.getTypeArguments().get(0);
        var element = this.types.asElement(entityType);
        if (CommonUtils.findDirectAnnotation(element, CassandraTypes.UDT_ANNOTATION) == null) {
            return null;
        }
        var mapperName = CommonUtils.getOuterClassesAsPrefix(element) + element.getSimpleName() + "_CassandraRowColumnMapper";
        var packageElement = this.elements.getPackageOf(element);

        return () -> {
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
            return ExtensionResult.nextRound();
        };
    }

    private KoraExtensionDependencyGenerator generateParameterColumnMapper(RoundEnvironment roundEnvironment, DeclaredType dt) {
        var entityType = dt.getTypeArguments().get(0);
        var element = this.types.asElement(entityType);
        if (CommonUtils.findDirectAnnotation(element, CassandraTypes.UDT_ANNOTATION) == null) {
            return null;
        }
        var mapperName = CommonUtils.getOuterClassesAsPrefix(element) + element.getSimpleName() + "_CassandraParameterColumnMapper";
        var packageElement = this.elements.getPackageOf(element);

        return () -> {
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
            return ExtensionResult.nextRound();
        };

    }

    @Nullable
    private KoraExtensionDependencyGenerator generateResultRowMapper(RoundEnvironment roundEnvironment, DeclaredType typeMirror) {
        var rowType = typeMirror.getTypeArguments().get(0);
        var entity = DbEntity.parseEntity(this.types, rowType);
        if (entity == null) {
            return null;
        }
        var mapperName = CommonUtils.getOuterClassesAsPrefix(entity.typeElement()) + entity.typeElement().getSimpleName() + "_CassandraRowMapper";
        var packageElement = this.elements.getPackageOf(entity.typeElement());

        return () -> {
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
            var type = TypeSpec.classBuilder(mapperName)
                .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", "$S", CassandraTypesExtension.class.getCanonicalName()).build())
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
            for (var field : entity.entityFields()) {
                apply.addCode("var _idx_$L = _row.firstIndexOf($S);\n", field.element().getSimpleName(), field.columnName());
            }
            apply.addCode(read.block());
            apply.addCode("return _result;\n");

            type.addMethod(constructor.build());
            type.addMethod(apply.build());
            JavaFile.builder(packageElement.getQualifiedName().toString(), type.build()).build().writeTo(this.filer);
            return ExtensionResult.nextRound();
        };

    }

    @Nullable
    private KoraExtensionDependencyGenerator generateResultSetMapper(RoundEnvironment roundEnvironment, DeclaredType typeMirror) {
        //CassandraResultSetMapper<List<T>>
        var listType = typeMirror.getTypeArguments().get(0);
        if (!(listType instanceof DeclaredType dt)) {
            return null;
        }
        var listTypeName = (ParameterizedTypeName) TypeName.get(listType);
        if (listTypeName.rawType.canonicalName().equals("java.util.List")) {
            var rowType = dt.getTypeArguments().get(0);
            return this.listResultSetMapper(typeMirror, listTypeName, (DeclaredType) rowType);
        } else {
            return null;
        }
    }

    private KoraExtensionDependencyGenerator listResultSetMapper(DeclaredType typeMirror, ParameterizedTypeName listType, DeclaredType rowTypeMirror) {
        var packageElement = this.elements.getPackageOf(this.types.asElement(rowTypeMirror));
        var rowType = TypeName.get(rowTypeMirror);
        var entity = DbEntity.parseEntity(this.types, rowTypeMirror);
        if (entity == null) {
            return null;
        }
        var rowTypeElement = this.types.asElement(rowTypeMirror);
        var mapperName = CommonUtils.getOuterClassesAsPrefix(rowTypeElement) + ((ClassName) rowType).simpleName() + "_ListCassandraResultSetMapper";

        return () -> {
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
            var type = TypeSpec.classBuilder(mapperName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", "$S", CassandraTypesExtension.class.getCanonicalName()).build())
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
            for (var field : entity.entityFields()) {
                apply.addCode("var _idx_$L = _rs.getColumnDefinitions().firstIndexOf($S);\n", field.element().getSimpleName(), field.columnName());
            }
            apply.addCode("var _result = new $T<$T>(_rs.getAvailableWithoutFetching());\n", ArrayList.class, rowTypeMirror);
            apply.beginControlFlow("for (var _row : _rs)");
            apply.addCode(read.block());
            apply.addCode("_result.add(_rowValue);\n");
            apply.endControlFlow();
            apply.addCode("return _result;\n");

            var typeSpec = type.addMethod(apply.build())
                .addMethod(constructor.build())
                .build();
            JavaFile.builder(packageElement.getQualifiedName().toString(), typeSpec).build().writeTo(this.filer);
            return ExtensionResult.nextRound();
        };
    }
}
