package ru.tinkoff.kora.json.annotation.processor.writer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.squareup.javapoet.*;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;
import ru.tinkoff.kora.json.annotation.processor.KnownType;
import ru.tinkoff.kora.json.annotation.processor.writer.JsonClassWriterMeta.FieldMeta;
import ru.tinkoff.kora.json.common.EnumJsonWriter;
import ru.tinkoff.kora.json.common.JsonObjectCodec;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.common.annotation.JsonDiscriminatorValue;

import javax.annotation.Nullable;
import javax.annotation.processing.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.function.Function;

public class JsonWriterGenerator {
    private final ProcessingEnvironment processingEnvironment;
    private final Types types;
    private final Elements elements;
    private final TypeElement writerErasure;
    private final DeclaredType enumType;

    public JsonWriterGenerator(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
        this.types = processingEnvironment.getTypeUtils();
        this.elements = processingEnvironment.getElementUtils();
        this.writerErasure = (TypeElement) this.types.asElement(
            this.types.erasure(this.elements.getTypeElement(JsonWriter.class.getCanonicalName()).asType())
        );
        this.enumType = this.types.getDeclaredType(
            this.elements.getTypeElement(Enum.class.getCanonicalName()),
            this.types.getWildcardType(null, null)
        );
    }

    @Nullable
    public TypeSpec generate(JsonClassWriterMeta meta) {
        var writerInterface = this.types.getDeclaredType(this.writerErasure, meta.typeMirror());

        var typeBuilder = TypeSpec.classBuilder(JsonUtils.jsonWriterName(meta.typeElement()))
            .addAnnotation(AnnotationSpec.builder(Generated.class)
                .addMember("value", CodeBlock.of("$S", JsonWriterGenerator.class.getCanonicalName()))
                .build())
            .addSuperinterface(writerInterface)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(meta.typeElement());
        if (this.types.isAssignable(meta.typeMirror(), this.enumType)) {
            return this.generateEnumWriter(meta, typeBuilder);
        }


        for (var typeParameter : meta.typeElement().getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }


        this.addWriters(typeBuilder, meta);
        for (var field : meta.fields()) {
            typeBuilder.addField(FieldSpec.builder(SerializedString.class, this.jsonNameStaticName(field), Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(CodeBlock.of("new $T($S)", SerializedString.class, field.jsonName()))
                .build());
        }

        var method = MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(TypeName.get(JsonGenerator.class), "_gen")
            .addParameter(ParameterSpec.builder(TypeName.get(meta.typeMirror()), "_object").addAnnotation(Nullable.class).build())
            .addAnnotation(Override.class);
        method.addCode("if (_object == null) {$>\n_gen.writeNull();\nreturn;$<\n}\n");
        method.addStatement("_gen.writeStartObject(_object)");

        if (meta.discriminatorField() != null) {
            var discriminatorValueAnnotation = meta.typeElement().getAnnotation(JsonDiscriminatorValue.class);
            var discriminatorFieldValue = discriminatorValueAnnotation == null ? meta.typeElement().getSimpleName().toString() : discriminatorValueAnnotation.value();
            method.addCode("_gen.writeFieldName($S);\n", meta.discriminatorField());
            method.addStatement("_gen.writeString($S);", discriminatorFieldValue);
        }
        for (var field : meta.fields()) {
            this.addWriteParam(method, field);
        }
        method.addStatement("_gen.writeEndObject()");

        typeBuilder.addMethod(method.build());
        return typeBuilder.build();
    }

    private TypeSpec generateEnumWriter(JsonClassWriterMeta meta, TypeSpec.Builder typeBuilder) {
        var delegateType = ParameterizedTypeName.get(ClassName.get(EnumJsonWriter.class), TypeName.get(meta.typeMirror()));

        typeBuilder.addField(delegateType, "delegate", Modifier.PRIVATE, Modifier.FINAL);
        typeBuilder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addCode("this.delegate = new $T<>($T.values(), v -> v.toString());\n", EnumJsonWriter.class, meta.typeMirror())
            .build());
        typeBuilder.addMethod(MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(TypeName.get(JsonGenerator.class), "_gen")
            .addParameter(ParameterSpec.builder(TypeName.get(meta.typeMirror()), "_object").addAnnotation(Nullable.class).build())
            .addAnnotation(Override.class)
                .addCode("this.delegate.write(_gen, _object);\n")
            .build()
        );
        return typeBuilder.build();
    }

    private void addWriters(TypeSpec.Builder typeBuilder, JsonClassWriterMeta classMeta) {
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        for (var field : classMeta.fields()) {
            if (field.writer() != null) {
                var fieldName = this.writerFieldName(field);
                var fieldType = TypeName.get(field.writer());
                var writerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                var writerElement = (TypeElement) this.types.asElement(field.writer());
                if (writerElement.getModifiers().contains(Modifier.FINAL)) {
                    var constructors = writerElement.getEnclosedElements().stream()
                        .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                        .toList();
                    if (constructors.size() == 1) {
                        writerField.addModifiers(Modifier.STATIC);
                        writerField.initializer("new $T()", field.writer());
                        typeBuilder.addField(writerField.build());
                        continue;
                    }
                }

                typeBuilder.addField(writerField.build());
                constructor.addParameter(fieldType, fieldName);
                constructor.addStatement("this.$L = $L", fieldName, fieldName);
            } else if (field.writerTypeMeta() instanceof WriterFieldType.UnknownWriterFieldType) {
                var fieldName = this.writerFieldName(field);
                var fieldType = ParameterizedTypeName.get(ClassName.get(JsonWriter.class), TypeName.get(field.typeMirror()));
                var writerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);

                typeBuilder.addField(writerField.build());
                constructor.addParameter(fieldType, fieldName);
                constructor.addStatement("this.$L = $L", fieldName, fieldName);
            }
        }
        typeBuilder.addMethod(constructor.build());
    }

    private String writerFieldName(JsonClassWriterMeta.FieldMeta field) {
        return field.accessor().getSimpleName() + "Writer";
    }

    private void addWriteParam(MethodSpec.Builder method, FieldMeta field) {
        if (!field.typeMirror().getKind().isPrimitive()) {
            method.addCode("if (_object.$L != null) {$>\n", field.accessor());
        }
        method.addCode("_gen.writeFieldName($L);\n", this.jsonNameStaticName(field));
        if (field.writer() == null && field.writerTypeMeta() instanceof WriterFieldType.KnownWriterFieldType typeMeta) {
            method.addCode(this.writeKnownType(field, typeMeta.knownType(), CodeBlock.of("_object.$L", field.accessor())));
        } else {
            method.addStatement("$L.write(_gen, _object.$L)", this.writerFieldName(field), field.accessor());
        }
        if (!field.typeMirror().getKind().isPrimitive()) {
            method.addCode("$<\n}\n");
        }
    }

    private String jsonNameStaticName(FieldMeta field) {
        return "_" + field.field().getSimpleName().toString() + "_optimized_field_name";
    }

    private CodeBlock writeKnownType(FieldMeta field, KnownType.KnownTypesEnum knownType, CodeBlock value) {
        Function<String, CodeBlock> addNullable = m -> CodeBlock.of("""
            if ($L != null) {
              _gen.$L($L);
            } else {
              _gen.writeNull();
            }
            """, value, m, value);

        return switch (knownType) {
            case STRING -> CodeBlock.of("_gen.writeString($L);\n", value);
            case BOOLEAN_OBJECT, BOOLEAN_PRIMITIVE -> CodeBlock.of("_gen.writeBoolean($L);\n", value);
            case INTEGER_OBJECT, BIG_INTEGER, BIG_DECIMAL, DOUBLE_OBJECT, FLOAT_OBJECT, LONG_OBJECT, SHORT_OBJECT,
                INTEGER_PRIMITIVE, DOUBLE_PRIMITIVE, FLOAT_PRIMITIVE, LONG_PRIMITIVE, SHORT_PRIMITIVE -> CodeBlock.of("_gen.writeNumber($L);\n", value);
            case BINARY -> addNullable.apply("writeBinary");
            case LOCAL_DATE, LOCAL_DATE_TIME, OFFSET_DATE_TIME -> CodeBlock.of("_gen.writeString($L.toString());", value);
            case UUID -> CodeBlock.of("_gen.writeString($L.toString());\n", value);
        };
    }
}
