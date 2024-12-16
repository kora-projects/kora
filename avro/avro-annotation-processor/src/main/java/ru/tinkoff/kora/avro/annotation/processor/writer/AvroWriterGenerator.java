package ru.tinkoff.kora.avro.annotation.processor.writer;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.avro.annotation.processor.AvroTypes;
import ru.tinkoff.kora.avro.annotation.processor.AvroUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class AvroWriterGenerator {

    private final ProcessingEnvironment env;

    public AvroWriterGenerator(ProcessingEnvironment processingEnvironment) {
        this.env = processingEnvironment;
    }

    public void generateBinary(TypeElement element) {
        var typeName = TypeName.get(element.asType());
        var typeBuilder = TypeSpec.classBuilder(AvroUtils.writerBinaryName(element))
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                .addMember("value", CodeBlock.of("$S", AvroWriterGenerator.class.getCanonicalName()))
                .build())
            .addSuperinterface(ParameterizedTypeName.get(AvroTypes.writer, typeName))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(element);

        for (var typeParameter : element.getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }

        typeBuilder.addField(FieldSpec.builder(ArrayTypeName.of(TypeName.BYTE), "EMPTY")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("new byte[]{}")
            .build());
        typeBuilder.addField(FieldSpec.builder(AvroTypes.schema, "SCHEMA")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("$T.getClassSchema()", typeName)
            .build());
        typeBuilder.addField(FieldSpec.builder(AvroTypes.specificData, "SPECIFIC_DATA")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("new $T().getSpecificData()", typeName)
            .build());
        typeBuilder.addField(FieldSpec.builder(ParameterizedTypeName.get(AvroTypes.datumWriter, typeName), "WRITER")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("new $T<>(SCHEMA, SPECIFIC_DATA)", AvroTypes.datumWriter)
            .build());

        var method = MethodSpec.methodBuilder("writeBytes")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addAnnotation(Override.class)
            .addParameter(ParameterSpec.builder(typeName, "value")
                .addAnnotation(Nullable.class).build())
            .returns(ArrayTypeName.of(TypeName.BYTE));
        method.beginControlFlow("if (value == null)");
        method.addStatement("return EMPTY");
        method.endControlFlow();
        method.beginControlFlow("try (var os = new $T())", ByteArrayOutputStream.class);
        method.addStatement("var encoder = $T.get().directBinaryEncoder(os, null)", AvroTypes.encoderFactory);
        method.addStatement("WRITER.write(value, encoder)");
        method.addStatement("encoder.flush()");
        method.addStatement("return os.toByteArray()");
        method.endControlFlow();

        typeBuilder.addMethod(method.build());
        TypeSpec spec = typeBuilder.build();
        var packageElement = AvroUtils.classPackage(this.env.getElementUtils(), element);
        var javaFile = JavaFile.builder(packageElement, spec).build();
        CommonUtils.safeWriteTo(this.env, javaFile);
    }

    public void generateJson(TypeElement element) {
        var typeName = TypeName.get(element.asType());
        var typeBuilder = TypeSpec.classBuilder(AvroUtils.writerJsonName(element))
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                .addMember("value", CodeBlock.of("$S", AvroWriterGenerator.class.getCanonicalName()))
                .build())
            .addSuperinterface(ParameterizedTypeName.get(AvroTypes.writer, typeName))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(element);

        for (var typeParameter : element.getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }

        typeBuilder.addField(FieldSpec.builder(ArrayTypeName.of(TypeName.BYTE), "EMPTY")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("new byte[]{}")
            .build());
        typeBuilder.addField(FieldSpec.builder(AvroTypes.schema, "SCHEMA")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("$T.getClassSchema()", typeName)
            .build());
        typeBuilder.addField(FieldSpec.builder(AvroTypes.specificData, "SPECIFIC_DATA")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("new $T().getSpecificData()", typeName)
            .build());
        typeBuilder.addField(FieldSpec.builder(ParameterizedTypeName.get(AvroTypes.datumWriter, typeName), "WRITER")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("new $T<>(SCHEMA, SPECIFIC_DATA)", AvroTypes.datumWriter)
            .build());

        var method = MethodSpec.methodBuilder("writeBytes")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addAnnotation(Override.class)
            .addParameter(ParameterSpec.builder(typeName, "value")
                .addAnnotation(Nullable.class).build())
            .returns(ArrayTypeName.of(TypeName.BYTE));
        method.beginControlFlow("if (value == null)");
        method.addStatement("return EMPTY");
        method.endControlFlow();
        method.beginControlFlow("try (var os = new $T())", ByteArrayOutputStream.class);
        method.addStatement("var encoder = $T.get().jsonEncoder(SCHEMA, os)", AvroTypes.encoderFactory);
        method.addStatement("WRITER.write(new $T(), encoder)", typeName);
        method.addStatement("encoder.flush()");
        method.addStatement("return os.toByteArray()");
        method.endControlFlow();

        typeBuilder.addMethod(method.build());
        TypeSpec spec = typeBuilder.build();
        var packageElement = AvroUtils.classPackage(this.env.getElementUtils(), element);
        var javaFile = JavaFile.builder(packageElement, spec).build();
        CommonUtils.safeWriteTo(this.env, javaFile);
    }
}
