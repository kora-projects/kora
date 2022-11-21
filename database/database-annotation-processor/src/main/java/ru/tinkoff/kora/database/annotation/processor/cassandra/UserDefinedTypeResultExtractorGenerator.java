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
        var element = types.asElement(type);
        var typeName = TypeName.get(type);
        var packageName = elements.getPackageOf(element);
        var typeSpec = TypeSpec.classBuilder(CommonUtils.getOuterClassesAsPrefix(element) + element.getSimpleName() + "_CassandraRowColumnMapper")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(CassandraTypes.RESULT_COLUMN_MAPPER, typeName));
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        var entity = Objects.requireNonNull(DbEntity.parseEntity(this.types, type));

        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(CassandraTypes.GETTABLE_BY_NAME, "_row")
            .addParameter(int.class, "_index")
            .returns(typeName);
        apply.addStatement("var _object = _row.getUdtValue(_index)");
        apply.addCode("\n");
        for (var entityField : entity.entityFields()) {
            var fieldName = entityField.element().getSimpleName().toString();
            var nativeType = CassandraNativeTypes.findNativeType(TypeName.get(entityField.typeMirror()));
            if (nativeType != null) {
                apply.addStatement("var $N = $L", fieldName, nativeType.extract("_object", CodeBlock.of("_object.firstIndexOf($S)", entityField.columnName())));
            } else {
                var mapperName = "_" + fieldName + "_mapper";
                // todo mapping annotation support?
                var mapperType = ParameterizedTypeName.get(CassandraTypes.RESULT_COLUMN_MAPPER, TypeName.get(entityField.typeMirror()));
                constructor.addParameter(mapperType, mapperName);
                constructor.addStatement("this.$N = $N", mapperName, mapperName);

                typeSpec.addField(mapperType, mapperName, Modifier.PRIVATE, Modifier.FINAL);
                apply.addStatement("var $N = this.$N.apply(_object, _object.firstIndexOf($S))", fieldName, mapperName, entityField.columnName());
            }
        }
        apply.addCode("\n");

        if (entity.entityType() == DbEntity.EntityType.RECORD) {
            apply.addCode("return new $T(", typeName);
            for (int i = 0; i < entity.entityFields().size(); i++) {
                if (i > 0) {
                    apply.addCode(", ");
                }
                var entityField = entity.entityFields().get(i);
                var fieldName = entityField.element().getSimpleName().toString();
                apply.addCode("$N", fieldName);
            }
            apply.addCode(");\n");

        } else {
            apply.addStatement("var _result = new $T()", typeName);
            for (var entityField : entity.entityFields()) {
                var fieldName = entityField.element().getSimpleName().toString();
                apply.addStatement("_result.set$L($N)", CommonUtils.capitalize(fieldName), fieldName);
            }
        }

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

