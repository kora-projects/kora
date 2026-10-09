package io.koraframework.database.annotation.processor.cassandra;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.database.annotation.processor.entity.DbEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * @return mappers to be nested into the holder generated for the type
     */
    public List<TypeSpec> generate(TypeElement element, TypeMirror type) {
        return List.of(this.generateMapper(element, type), this.generateListMapper(element, type));
    }

    public TypeSpec generateMapper(TypeElement element, TypeMirror type) {
        var typeName = TypeName.get(type);
        var packageName = elements.getPackageOf(element);

        var typeSpec = TypeSpec.classBuilder("RowColumnMapper")
            .addOriginatingElement(element)
            .addAnnotation(AnnotationUtils.generated(UserDefinedTypeResultExtractorGenerator.class))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
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
        apply.addCode(entity.buildInstance("_result"));
        apply.addStatement("return _result");

        typeSpec.addMethod(apply.build());
        typeSpec.addMethod(constructor.build());

        return typeSpec.build();
    }

    public TypeSpec generateListMapper(TypeElement element, TypeMirror type) {
        var typeName = TypeName.get(type);
        var packageName = elements.getPackageOf(element);
        var typeSpec = TypeSpec.classBuilder("ListRowColumnMapper")
            .addOriginatingElement(element)
            .addAnnotation(AnnotationUtils.generated(UserDefinedTypeResultExtractorGenerator.class))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
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
        apply.addCode(entity.buildInstance("_result"));
        apply.addStatement("_resultList.add(_result)");
        apply.endControlFlow();
        apply.addStatement("return _resultList");

        typeSpec.addMethod(apply.build());
        typeSpec.addMethod(constructor.build());

        return typeSpec.build();
    }


    private void readFields(MethodSpec.Builder apply, DbEntity entity) {
        for (var entityField : entity.columns()) {
            var fieldName = entityField.element().getSimpleName().toString();
            var index = CodeBlock.of("$N", "_index_of_" + entityField.element().getSimpleName());
            var nativeType = CassandraNativeTypes.findNativeType(TypeName.get(entityField.type()));
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
        for (var entityField : entity.columns()) {
            var nativeType = CassandraNativeTypes.findNativeType(TypeName.get(entityField.type()));
            if (nativeType == null) {
                var mapperName = "_" + entityField.element().getSimpleName() + "_mapper";
                // todo mapping annotation support?
                var mapperType = ParameterizedTypeName.get(CassandraTypes.RESULT_COLUMN_MAPPER, TypeName.get(entityField.type()));
                constructor.addParameter(mapperType, mapperName);
                constructor.addStatement("this.$N = $N", mapperName, mapperName);
                typeSpec.addField(mapperType, mapperName, Modifier.PRIVATE, Modifier.FINAL);
            }
        }
    }

    private void readIndexes(MethodSpec.Builder apply, DbEntity entity) {
        for (var entityField : entity.columns()) {
            apply.addStatement("var $N = _type.firstIndexOf($S)", "_index_of_" + entityField.element().getSimpleName(), entityField.columnName());
        }
        apply.addCode("\n");
    }
}

