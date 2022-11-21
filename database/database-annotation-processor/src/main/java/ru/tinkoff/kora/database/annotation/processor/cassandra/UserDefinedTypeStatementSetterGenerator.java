package ru.tinkoff.kora.database.annotation.processor.cassandra;


import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
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
        var element = this.types.asElement(typeMirror);
        var packageName = this.elements.getPackageOf(element);
        var typeSpec = TypeSpec.classBuilder(CommonUtils.getOuterClassesAsPrefix(element) + element.getSimpleName().toString() + "_CassandraParameterColumnMapper")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(CassandraTypes.PARAMETER_COLUMN_MAPPER, TypeName.get(typeMirror)));
        var entity = Objects.requireNonNull(DbEntity.parseEntity(this.types, typeMirror));
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(CassandraTypes.SETTABLE_BY_NAME, "_stmt")
            .addParameter(int.class, "_index")
            .addParameter(TypeName.get(typeMirror), "_value");

        apply.addStatement("var _type = ($T) _stmt.getType(_index)", CassandraTypes.USER_DEFINED_TYPE);
        apply.addStatement("var _object = _type.newValue()");
        apply.addCode("\n");
        for (var entityField : entity.entityFields()) {
            var fieldName = entityField.element().getSimpleName().toString();
            var nativeType = CassandraNativeTypes.findNativeType(TypeName.get(entityField.typeMirror()));
            if (nativeType != null) {
                if (entity.entityType() == DbEntity.EntityType.RECORD) {
                    apply.addStatement(nativeType.bind("_object", "_value." + fieldName + "()", CodeBlock.of("_type.firstIndexOf($S)", entityField.columnName())));
                } else {
                    apply.addStatement(nativeType.bind("_object", "_value.get" + CommonUtils.capitalize(fieldName) + "()", CodeBlock.of("_type.firstIndexOf($S)", entityField.columnName())));
                }
            } else {
                var mapperName = "_" + fieldName + "_mapper";
                // todo mapping annotation support?
                var mapperType = ParameterizedTypeName.get(CassandraTypes.PARAMETER_COLUMN_MAPPER, TypeName.get(entityField.typeMirror()));
                constructor.addParameter(mapperType, mapperName);
                constructor.addStatement("this.$N = $N", mapperName, mapperName);

                typeSpec.addField(mapperType, mapperName, Modifier.PRIVATE, Modifier.FINAL);
                if (entity.entityType() == DbEntity.EntityType.RECORD) {
                    apply.addStatement("this.$N.apply(_object, _type.firstIndexOf($S), _value.$N())", mapperName, entityField.columnName(), fieldName);
                } else {
                    apply.addStatement("this.$N.apply(_object, _type.firstIndexOf($S), _value.get$N())", mapperName, entityField.columnName(), CommonUtils.capitalize(fieldName));
                }
            }
        }
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
}
