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

public class UserDefinedTypeStatementSetterGenerator {

    private final Elements elements;
    private final Types types;
    private final ProcessingEnvironment processingEnv;

    public UserDefinedTypeStatementSetterGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
    }

    public void generate(TypeMirror typeMirror) {
        this.generateMapper(typeMirror);
        this.generateListMapper(typeMirror);
    }

    public void generateMapper(TypeMirror typeMirror) {
        var element = this.types.asElement(typeMirror);
        var packageName = this.elements.getPackageOf(element);
        var typeSpec = TypeSpec.classBuilder(CommonUtils.getOuterClassesAsPrefix(element) + element.getSimpleName().toString() + "_CassandraParameterColumnMapper")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(CassandraTypes.PARAMETER_COLUMN_MAPPER, TypeName.get(typeMirror)));
        var entity = Objects.requireNonNull(DbEntity.parseEntity(this.types, typeMirror));
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        this.addMappers(typeSpec, constructor, entity);
        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(CassandraTypes.SETTABLE_BY_NAME, "_stmt")
            .addParameter(int.class, "_index")
            .addParameter(TypeName.get(typeMirror), "_value");
        apply.beginControlFlow("if (_value == null)").addStatement("_stmt.setToNull(_index)").addStatement("return").endControlFlow();

        apply.addStatement("var _type = ($T) _stmt.getType(_index)", CassandraTypes.USER_DEFINED_TYPE);
        this.readIndexes(apply, entity);
        apply.addStatement("var _object = _type.newValue()");
        apply.addCode("\n");
        this.setFields(apply, entity);
        apply.addCode("\n");
        apply.addStatement("_stmt.setUdtValue(_index, _object)");

        typeSpec.addMethod(apply.build());
        typeSpec.addMethod(constructor.build());
        try {
            var javaFile = JavaFile.builder(packageName.getQualifiedName().toString(), typeSpec.build()).build();
            CommonUtils.safeWriteTo(this.processingEnv, javaFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void generateListMapper(TypeMirror typeMirror) {
        var element = this.types.asElement(typeMirror);
        var packageName = this.elements.getPackageOf(element);
        var listType = ParameterizedTypeName.get(CommonClassNames.list, TypeName.get(typeMirror));
        var typeSpec = TypeSpec.classBuilder(CommonUtils.getOuterClassesAsPrefix(element) + element.getSimpleName().toString() + "_List_CassandraParameterColumnMapper")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(CassandraTypes.PARAMETER_COLUMN_MAPPER, listType));
        var entity = Objects.requireNonNull(DbEntity.parseEntity(this.types, typeMirror));
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        this.addMappers(typeSpec, constructor, entity);
        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(CassandraTypes.SETTABLE_BY_NAME, "_stmt")
            .addParameter(int.class, "_index")
            .addParameter(listType, "_listValue");
        apply.beginControlFlow("if (_listValue == null)").addStatement("_stmt.setToNull(_index)").addStatement("return").endControlFlow();

        apply.addStatement("var _listType = ($T) _stmt.getType(_index)", CassandraTypes.LIST_TYPE);
        apply.addStatement("var _type = ($T) _listType.getElementType()", CassandraTypes.USER_DEFINED_TYPE);
        this.readIndexes(apply, entity);
        apply.addStatement("var _listResult = new $T<$T>(_listValue.size())", ArrayList.class, CassandraTypes.UDT_VALUE);
        apply.beginControlFlow("for (var _value : _listValue)");
        apply.addStatement("var _object = _type.newValue()");
        this.setFields(apply, entity);
        apply.addStatement("_listResult.add(_object)");
        apply.endControlFlow();
        apply.addCode("\n");
        apply.addStatement("_stmt.setList(_index, _listResult, $T.class)", CassandraTypes.UDT_VALUE);

        typeSpec.addMethod(apply.build());
        typeSpec.addMethod(constructor.build());
        try {
            var javaFile = JavaFile.builder(packageName.getQualifiedName().toString(), typeSpec.build()).build();
            CommonUtils.safeWriteTo(this.processingEnv, javaFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readIndexes(MethodSpec.Builder apply, DbEntity entity) {
        for (var entityField : entity.entityFields()) {
            var fieldName = entityField.element().getSimpleName().toString();
            apply.addStatement("var $N = _type.firstIndexOf($S)", "_index_of_" + fieldName, entityField.columnName());
        }
    }

    private void setFields(MethodSpec.Builder apply, DbEntity entity) {
        for (var entityField : entity.entityFields()) {
            var fieldName = entityField.element().getSimpleName().toString();
            var index = CodeBlock.of("$N", "_index_of_" + fieldName);
            var nativeType = CassandraNativeTypes.findNativeType(TypeName.get(entityField.typeMirror()));
            if (nativeType != null) {
                if (entity.entityType() == DbEntity.EntityType.RECORD) {
                    apply.addStatement(nativeType.bind("_object", "_value." + fieldName + "()", index));
                } else {
                    apply.addStatement(nativeType.bind("_object", "_value.get" + CommonUtils.capitalize(fieldName) + "()", index));
                }
            } else {
                var mapperName = "_" + fieldName + "_mapper";
                if (entity.entityType() == DbEntity.EntityType.RECORD) {
                    apply.addStatement("this.$N.apply(_object, $L, _value.$N())", mapperName, index, fieldName);
                } else {
                    apply.addStatement("this.$N.apply(_object, $L, _value.get$N())", mapperName, index, CommonUtils.capitalize(fieldName));
                }
            }
        }
    }

    private void addMappers(TypeSpec.Builder typeSpec, MethodSpec.Builder constructor, DbEntity entity) {
        for (var entityField : entity.entityFields()) {
            var fieldName = entityField.element().getSimpleName().toString();
            var nativeType = CassandraNativeTypes.findNativeType(TypeName.get(entityField.typeMirror()));
            if (nativeType == null) {
                var mapperName = "_" + fieldName + "_mapper";
                // todo mapping annotation support?
                var mapperType = ParameterizedTypeName.get(CassandraTypes.PARAMETER_COLUMN_MAPPER, TypeName.get(entityField.typeMirror()));
                constructor.addParameter(mapperType, mapperName);
                constructor.addStatement("this.$N = $N", mapperName, mapperName);
                typeSpec.addField(mapperType, mapperName, Modifier.PRIVATE, Modifier.FINAL);
            }
        }
    }
}
