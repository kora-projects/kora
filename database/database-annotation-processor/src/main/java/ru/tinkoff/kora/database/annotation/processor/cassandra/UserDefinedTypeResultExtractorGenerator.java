package ru.tinkoff.kora.database.annotation.processor.cassandra;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

public class UserDefinedTypeResultExtractorGenerator {
    private final ProcessingEnvironment processingEnv;
    private final Elements elements;
    private final Types types;

    public UserDefinedTypeResultExtractorGenerator(ProcessingEnvironment processingEnvironment) {
        this.processingEnv = processingEnvironment;
        this.elements = processingEnvironment.getElementUtils();
        this.types = processingEnvironment.getTypeUtils();
    }

    public void generate(TypeMirror type) {
        this.generateMapper(type);
        this.generateListMapper(type);
    }

    public void generateMapper(TypeMirror type) {
        var element = types.asElement(type);
        var typeName = TypeName.get(type);
        var packageName = elements.getPackageOf(element);
        var typeSpec = TypeSpec.classBuilder(CommonUtils.getOuterClassesAsPrefix(element) + element.getSimpleName() + "_CassandraRowColumnMapper")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(CassandraTypes.RESULT_COLUMN_MAPPER, typeName));
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        var entity = Objects.requireNonNull(DbEntity.parseEntity(this.types, type));
        this.addMappers(typeSpec, constructor, entity);

        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(CassandraTypes.GETTABLE_BY_NAME, "_row")
            .addParameter(int.class, "_index")
            .returns(typeName);
        apply.addStatement("var _object = _row.getUdtValue(_index)");
        apply.beginControlFlow("if (_object == null)").addStatement("return null").endControlFlow();
        apply.addStatement("var _type = ($T) _row.getType(_index)", CassandraTypes.USER_DEFINED_TYPE);
        this.readIndexes(apply, entity);
        this.readFields(apply, entity);
        apply.addCode(this.buildEntity(entity));
        apply.addStatement("return _result");

        typeSpec.addMethod(apply.build());
        typeSpec.addMethod(constructor.build());

        try {
            var javaFile = JavaFile.builder(packageName.getQualifiedName().toString(), typeSpec.build()).build();
            CommonUtils.safeWriteTo(this.processingEnv, javaFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void generateListMapper(TypeMirror type) {
        var element = types.asElement(type);
        var typeName = TypeName.get(type);
        var packageName = elements.getPackageOf(element);
        var typeSpec = TypeSpec.classBuilder(CommonUtils.getOuterClassesAsPrefix(element) + element.getSimpleName() + "_List_CassandraRowColumnMapper")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(CassandraTypes.RESULT_COLUMN_MAPPER, ParameterizedTypeName.get(CommonClassNames.list, typeName)));
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        var entity = Objects.requireNonNull(DbEntity.parseEntity(this.types, type));
        this.addMappers(typeSpec, constructor, entity);

        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(CassandraTypes.GETTABLE_BY_NAME, "_row")
            .addParameter(int.class, "_index")
            .returns(ParameterizedTypeName.get(CommonClassNames.list, typeName));
        apply.addStatement("var _list = _row.getList(_index, $T.class)", CassandraTypes.UDT_VALUE);
        apply.beginControlFlow("if (_list == null)").addStatement("return null").endControlFlow();
        apply.addStatement("var _listType = ($T) _row.getType(_index)", CassandraTypes.LIST_TYPE);
        apply.addStatement("var _type = ($T) _listType.getElementType()", CassandraTypes.USER_DEFINED_TYPE);
        this.readIndexes(apply, entity);
        apply.addStatement("var _resultList = new $T<$T>(_list.size())", ArrayList.class, typeName);
        apply.beginControlFlow("for (var _object : _list)");
        this.readFields(apply, entity);
        apply.addCode(this.buildEntity(entity));
        apply.addStatement("_resultList.add(_result)");
        apply.endControlFlow();
        apply.addStatement("return _resultList");

        typeSpec.addMethod(apply.build());
        typeSpec.addMethod(constructor.build());

        try {
            var javaFile = JavaFile.builder(packageName.getQualifiedName().toString(), typeSpec.build()).build();
            CommonUtils.safeWriteTo(this.processingEnv, javaFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private CodeBlock buildEntity(DbEntity entity) {
        var b = CodeBlock.builder();
        var typeName = TypeName.get(entity.typeMirror());
        if (entity.entityType() == DbEntity.EntityType.RECORD) {
            b.add("var _result = new $T(", typeName);
            for (int i = 0; i < entity.entityFields().size(); i++) {
                if (i > 0) {
                    b.add(", ");
                }
                var entityField = entity.entityFields().get(i);
                var fieldName = entityField.element().getSimpleName().toString();
                b.add("$N", fieldName);
            }
            b.add(");\n");

        } else {
            b.addStatement("var _result = new $T()", typeName);
            for (var entityField : entity.entityFields()) {
                var fieldName = entityField.element().getSimpleName().toString();
                b.addStatement("_result.set$L($N)", CommonUtils.capitalize(fieldName), fieldName);
            }
        }
        return b.build();
    }

    private void readFields(MethodSpec.Builder apply, DbEntity entity) {
        for (var entityField : entity.entityFields()) {
            var fieldName = entityField.element().getSimpleName().toString();
            var index = CodeBlock.of("$N", "_index_of_" + entityField.element().getSimpleName());
            var nativeType = CassandraNativeTypes.findNativeType(TypeName.get(entityField.typeMirror()));
            if (nativeType != null) {
                apply.addStatement("var $N = $L", fieldName, nativeType.extract("_object", index));
            } else {
                var mapperName = "_" + fieldName + "_mapper";
                apply.addStatement("var $N = this.$N.apply(_object, $L)", fieldName, mapperName, index);
            }
        }
        apply.addCode("\n");
    }

    private void addMappers(TypeSpec.Builder typeSpec, MethodSpec.Builder constructor, DbEntity entity) {
        for (var entityField : entity.entityFields()) {
            var nativeType = CassandraNativeTypes.findNativeType(TypeName.get(entityField.typeMirror()));
            if (nativeType == null) {
                var mapperName = "_" + entityField.element().getSimpleName() + "_mapper";
                // todo mapping annotation support?
                var mapperType = ParameterizedTypeName.get(CassandraTypes.RESULT_COLUMN_MAPPER, TypeName.get(entityField.typeMirror()));
                constructor.addParameter(mapperType, mapperName);
                constructor.addStatement("this.$N = $N", mapperName, mapperName);
                typeSpec.addField(mapperType, mapperName, Modifier.PRIVATE, Modifier.FINAL);
            }
        }
    }

    private void readIndexes(MethodSpec.Builder apply, DbEntity entity) {
        for (var entityField : entity.entityFields()) {
            apply.addStatement("var $N = _type.firstIndexOf($S)", "_index_of_" + entityField.element().getSimpleName(), entityField.columnName());
        }
        apply.addCode("\n");
    }
}

