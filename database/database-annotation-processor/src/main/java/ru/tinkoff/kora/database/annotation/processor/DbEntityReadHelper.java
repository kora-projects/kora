package ru.tinkoff.kora.database.annotation.processor;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;

import javax.annotation.Nullable;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class DbEntityReadHelper {
    private final ClassName fieldMapperName;
    private final Types types;
    private final Function<FieldData, CodeBlock> mapperCallGenerator;
    private final Function<FieldData, CodeBlock> nativeTypeExtractGenerator;
    private final Function<FieldData, CodeBlock> nullCheckGenerator;

    public DbEntityReadHelper(ClassName fieldMapperName, Types types, Function<FieldData, CodeBlock> mapperCallGenerator, Function<FieldData, CodeBlock> nativeTypeExtractGenerator, Function<FieldData, CodeBlock> nullCheckGenerator) {
        this.fieldMapperName = fieldMapperName;
        this.types = types;
        this.mapperCallGenerator = mapperCallGenerator;
        this.nativeTypeExtractGenerator = nativeTypeExtractGenerator;
        this.nullCheckGenerator = nullCheckGenerator;
    }

    public record RequiredField(FieldSpec field, @Nullable ParameterSpec constructorParam) {}

    public record ReadEntityCodeBlock(CodeBlock block, List<RequiredField> requiredFields) {
        public void enrich(TypeSpec.Builder type, MethodSpec.Builder constructor) {
            for (var requiredField : requiredFields) {
                type.addField(requiredField.field);
                if (requiredField.constructorParam() != null) {
                    constructor.addParameter(requiredField.constructorParam);
                    constructor.addCode("this.$L = $L;\n", requiredField.field.name, requiredField.constructorParam().name);
                }
            }
        }
    }

    public record FieldData(TypeMirror type, String mapperFieldName, String columnName, String fieldName, boolean nullable) {}

    public ReadEntityCodeBlock readEntity(String variableName, DbEntity entity) {
        var b = CodeBlock.builder();
        var fields = new ArrayList<RequiredField>();
        for (var entityField : entity.columns()) {
            var mapping = CommonUtils.parseMapping(entityField.element());
            var mapper = mapping.getMapping(this.fieldMapperName);
            var fieldName = entityField.variableName();
            var mapperFieldName = "_" + fieldName + "Mapper";
            var fieldData = new FieldData(entityField.type(), mapperFieldName, entityField.columnName(), fieldName, entityField.isNullable());
            var mapperTypeParameter = TypeName.get(entityField.type()).box();
            var type = entityField.isNullable() ? TypeName.get(fieldData.type()).box() : TypeName.get(fieldData.type());
            if (mapper != null) {
                var mapperType = mapper.mapperClass() != null
                    ? TypeName.get(mapper.mapperClass())
                    : ParameterizedTypeName.get(this.fieldMapperName, mapperTypeParameter);
                if (mapper.mapperClass() != null && (mapper.mapperTags() == null || mapper.mapperTags().isEmpty())) {
                    if (CommonUtils.hasDefaultConstructorAndFinal(this.types, mapper.mapperClass())) {
                        fields.add(new RequiredField(FieldSpec.builder(TypeName.get(mapper.mapperClass()), mapperFieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                            .initializer("new $T()", mapper.mapperClass())
                            .build(), null));
                    } else {
                        var tag = mapper.toTagAnnotation();
                        var param = ParameterSpec.builder(mapperType, mapperFieldName);
                        if (tag != null) {
                            param.addAnnotation(tag);
                        }
                        fields.add(new RequiredField(
                            FieldSpec.builder(mapperType, mapperFieldName, Modifier.PRIVATE, Modifier.FINAL).build(),
                            param.build()
                        ));
                    }
                }
                b.add("$T $L = $L;\n", type, fieldName, this.mapperCallGenerator.apply(fieldData));
            } else {
                var extractNative = this.nativeTypeExtractGenerator.apply(fieldData);
                if (extractNative != null) {
                    b.add("$T $L = $L;\n", type, fieldName, extractNative);
                } else {
                    var mapperType = ParameterizedTypeName.get(this.fieldMapperName, mapperTypeParameter);
                    fields.add(new RequiredField(
                        FieldSpec.builder(mapperType, mapperFieldName, Modifier.PRIVATE, Modifier.FINAL).build(),
                        ParameterSpec.builder(mapperType, mapperFieldName).build()
                    ));
                    b.add("$T $L = $L;\n", type, fieldName, this.mapperCallGenerator.apply(fieldData));
                }
            }
            b.add(this.nullCheckGenerator.apply(fieldData));
        }
        b.add(entity.buildEmbeddedFields());
        b.add(entity.buildInstance(variableName));
        return new ReadEntityCodeBlock(b.build(), fields);
    }

}
