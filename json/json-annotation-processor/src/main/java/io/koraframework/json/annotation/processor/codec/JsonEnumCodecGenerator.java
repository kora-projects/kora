package io.koraframework.json.annotation.processor.codec;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.json.annotation.processor.*;
import io.koraframework.json.annotation.processor.reader.*;

import javax.lang.model.element.*;

public class JsonEnumCodecGenerator {

    public TypeSpec generateForEnum(TypeElement typeElement) {
        var typeName = ClassName.get(typeElement);
        var enumValue = this.detectValueType(typeElement);
        String codecClassName = JsonUtils.jsonReaderName(typeElement).replace("JsonReader", "JsonCodec");

        var typeBuilder = TypeSpec.classBuilder(codecClassName)
            .addAnnotation(AnnotationUtils.generated(JsonReaderGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonReader, typeName))
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonWriter, typeName))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(typeElement);

        var readerDelegateType = ParameterizedTypeName.get(JsonTypes.enumJsonReader, typeName, enumValue.type.box());
        var writerDelegateType = ParameterizedTypeName.get(JsonTypes.enumJsonWriter, typeName, enumValue.type.box());

        typeBuilder.addField(readerDelegateType, "readerDelegate", Modifier.PRIVATE, Modifier.FINAL);
        typeBuilder.addField(writerDelegateType, "writerDelegate", Modifier.PRIVATE, Modifier.FINAL);

        var extractorClassName = "_EnumExtractor";
        var extractorTypeSpec = TypeSpec.classBuilder(extractorClassName)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(ClassName.get("java.util.function", "Function"), typeName, enumValue.type.box()))
            .addMethod(MethodSpec.methodBuilder("apply")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(enumValue.type.box())
                .addParameter(typeName, "_enum")
                .addStatement("return _enum.$N()", enumValue.accessor)
                .build())
            .build();
        typeBuilder.addType(extractorTypeSpec);

        this.addCombinedConstructor(typeBuilder, typeName, enumValue);
        this.addReadMethod(typeBuilder, typeName);
        this.addWriteMethod(typeBuilder, typeName);

        return typeBuilder.build();
    }

    private void addCombinedConstructor(TypeSpec.Builder typeBuilder, ClassName typeName, EnumValue enumValue) {
        typeBuilder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ParameterizedTypeName.get(JsonTypes.jsonReader, enumValue.type.box()), "valueReader")
            .addParameter(ParameterizedTypeName.get(JsonTypes.jsonWriter, enumValue.type.box()), "valueWriter")
            .addCode("this.readerDelegate = new $T<>($T.values(), new _EnumExtractor(), valueReader);\n", JsonTypes.enumJsonReader, typeName)
            .addCode("this.writerDelegate = new $T<>($T.values(), new _EnumExtractor(), valueWriter);\n", JsonTypes.enumJsonWriter, typeName)
            .build());
    }

    private void addReadMethod(TypeSpec.Builder typeBuilder, ClassName typeName) {
        typeBuilder.addMethod(MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(JsonTypes.jsonParser, "__parser")
            .returns(typeName.withoutAnnotations().annotated(CommonClassNames.nullableAnnotation))
            .addAnnotation(Override.class)
            .addCode("return this.readerDelegate.read(__parser);\n")
            .build());
    }

    private void addWriteMethod(TypeSpec.Builder typeBuilder, ClassName typeName) {
        typeBuilder.addMethod(MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(JsonTypes.jsonGenerator, "_gen")
            .addParameter(ParameterSpec.builder(typeName.withoutAnnotations().annotated(CommonClassNames.nullableAnnotation), "_object").build())
            .addAnnotation(Override.class)
            .addCode("this.writerDelegate.write(_gen, _object);\n")
            .build());
    }

    record EnumValue(TypeName type, String accessor) {}

    private EnumValue detectValueType(TypeElement typeElement) {
        for (var enclosedElement : typeElement.getEnclosedElements()) {
            if (!enclosedElement.getModifiers().contains(Modifier.PUBLIC)) continue;
            if (enclosedElement.getModifiers().contains(Modifier.STATIC)) continue;
            if (enclosedElement.getKind() != ElementKind.METHOD) continue;
            if (enclosedElement instanceof ExecutableElement executableElement && executableElement.getParameters().isEmpty()) {
                if (AnnotationUtils.isAnnotationPresent(executableElement, JsonTypes.json)) {
                    var typeName = TypeName.get(executableElement.getReturnType());
                    return new EnumValue(typeName, executableElement.getSimpleName().toString());
                }
            }
        }
        var typeName = ClassName.get(String.class);
        return new EnumValue(typeName, "toString");
    }
}
