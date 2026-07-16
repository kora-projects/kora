package io.koraframework.avro.annotation.processor.writer;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.avro.annotation.processor.AvroTypes;
import io.koraframework.avro.annotation.processor.AvroUtils;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AvroWriterGenerator {
    private final ProcessingEnvironment env;

    public AvroWriterGenerator(ProcessingEnvironment processingEnvironment) {
        this.env = processingEnvironment;
    }

    public void generate(TypeElement element) {
        var typeName = TypeName.get(element.asType());
        var typeBuilder = TypeSpec.classBuilder(AvroUtils.writerName(element))
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                .addMember("value", CodeBlock.of("$S", AvroWriterGenerator.class.getCanonicalName()))
                .build())
            .addAnnotation(AvroTypes.avro)
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
            .addParameter(ParameterSpec.builder(typeName, "value").addAnnotation(Nullable.class).build())
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
        var javaFile = JavaFile.builder(AvroUtils.classPackage(this.env.getElementUtils(), element), typeBuilder.build()).build();
        CommonUtils.safeWriteTo(this.env, javaFile);
    }
}
