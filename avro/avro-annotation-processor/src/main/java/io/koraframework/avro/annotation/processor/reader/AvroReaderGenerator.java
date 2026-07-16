package io.koraframework.avro.annotation.processor.reader;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.avro.annotation.processor.AvroTypes;
import io.koraframework.avro.annotation.processor.AvroUtils;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import java.io.IOException;
import java.io.InputStream;

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

        var method = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addAnnotation(Nullable.class)
            .addAnnotation(Override.class)
            .addParameter(TypeName.get(InputStream.class), "value")
            .returns(typeName);
        method.beginControlFlow("if (value == null || value.available() == 0)");
        method.addStatement("return null");
        method.endControlFlow();
        method.addStatement("var decoder = $T.get().directBinaryDecoder(value, null)", AvroTypes.decoderFactory);
        method.addStatement("return READER.read(new $T(), decoder)", typeName);

        typeBuilder.addMethod(method.build());
        var javaFile = JavaFile.builder(AvroUtils.classPackage(this.env.getElementUtils(), element), typeBuilder.build()).build();
        CommonUtils.safeWriteTo(this.env, javaFile);
    }
}
