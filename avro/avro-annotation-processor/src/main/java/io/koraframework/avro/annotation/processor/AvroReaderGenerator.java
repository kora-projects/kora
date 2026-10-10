package io.koraframework.avro.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.CommonUtils;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public class AvroReaderGenerator {
    private final ProcessingEnvironment env;

    public AvroReaderGenerator(ProcessingEnvironment processingEnvironment) {
        this.env = processingEnvironment;
    }

    public void generate(TypeElement element) {
        var typeName = TypeName.get(element.asType());
        var typeBuilder = TypeSpec.classBuilder(AvroUtils.readerName(element))
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                .addMember("value", CodeBlock.of("$S", AvroReaderGenerator.class.getCanonicalName()))
                .build())
            .addAnnotation(AvroTypes.avro)
            .addSuperinterface(ParameterizedTypeName.get(AvroTypes.reader, typeName))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(element);

        for (TypeParameterElement typeParameter : element.getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }

        typeBuilder.addField(FieldSpec.builder(AvroTypes.schema, "SCHEMA")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("$T.getClassSchema()", typeName)
            .build());
        typeBuilder.addField(FieldSpec.builder(AvroTypes.specificData, "SPECIFIC_DATA")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("new $T().getSpecificData()", typeName)
            .build());
        typeBuilder.addField(FieldSpec.builder(ParameterizedTypeName.get(AvroTypes.datumReader, typeName), "READER")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("new $T<>(SCHEMA, SCHEMA, SPECIFIC_DATA)", AvroTypes.datumReader)
            .build());
        typeBuilder.addField(FieldSpec.builder(
                ParameterizedTypeName.get(AvroTypes.concurrentMap, AvroTypes.schema, ParameterizedTypeName.get(AvroTypes.datumReader, typeName)), "READERS_BY_WRITER_SCHEMA")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .initializer("new $T<>()", AvroTypes.concurrentHashMap)
            .build());

        typeBuilder.addMethod(MethodSpec.methodBuilder("getSchema")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Override.class)
            .returns(AvroTypes.schema)
            .addStatement("return SCHEMA")
            .build());

        var method = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Nullable.class)
            .addAnnotation(Override.class)
            .addParameter(TypeName.get(InputStream.class), "value")
            .returns(typeName);
        method.beginControlFlow("try");
        method.beginControlFlow("if (value == null || value.available() == 0)");
        method.addStatement("return null");
        method.endControlFlow();
        method.addStatement("var decoder = $T.get().directBinaryDecoder(value, null)", AvroTypes.decoderFactory);
        method.addStatement("return READER.read(new $T(), decoder)", typeName);
        method.nextControlFlow("catch ($T e)", IOException.class);
        method.addStatement("throw new $T(e)", UncheckedIOException.class);
        method.endControlFlow();

        typeBuilder.addMethod(method.build());

        var resolvingMethod = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Nullable.class)
            .addAnnotation(Override.class)
            .addParameter(ParameterSpec.builder(AvroTypes.schema, "writerSchema").addAnnotation(Nullable.class).build())
            .addParameter(TypeName.get(InputStream.class), "value")
            .returns(typeName);
        resolvingMethod.beginControlFlow("try");
        resolvingMethod.beginControlFlow("if (value == null || value.available() == 0)");
        resolvingMethod.addStatement("return null");
        resolvingMethod.endControlFlow();
        resolvingMethod.beginControlFlow("if (writerSchema == null || SCHEMA.equals(writerSchema))");
        resolvingMethod.addStatement("return read(value)");
        resolvingMethod.endControlFlow();
        resolvingMethod.addStatement("var reader = READERS_BY_WRITER_SCHEMA.computeIfAbsent(writerSchema, ws -> new $T<>(ws, SCHEMA, SPECIFIC_DATA))", AvroTypes.datumReader);
        resolvingMethod.addStatement("var decoder = $T.get().directBinaryDecoder(value, null)", AvroTypes.decoderFactory);
        resolvingMethod.addStatement("return reader.read(new $T(), decoder)", typeName);
        resolvingMethod.nextControlFlow("catch ($T e)", IOException.class);
        resolvingMethod.addStatement("throw new $T(e)", UncheckedIOException.class);
        resolvingMethod.endControlFlow();

        typeBuilder.addMethod(resolvingMethod.build());
        var javaFile = JavaFile.builder(AvroUtils.classPackage(this.env.getElementUtils(), element), typeBuilder.build()).build();
        CommonUtils.safeWriteTo(this.env, javaFile);
    }
}
