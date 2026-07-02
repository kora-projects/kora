package ru.tinkoff.kora.json.annotation.processor.reader;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.IOException;

public class EnumReaderGenerator {

    public TypeSpec generateForEnum(TypeElement typeElement) {
        var typeName = ClassName.get(typeElement);
        var factory = this.detectReaderFactory(typeElement);
        if (factory != null) {
            return this.generateFactoryReader(typeElement, typeName, factory);
        }

        var enumValue = this.detectValueType(typeElement);

        var typeBuilder = TypeSpec.classBuilder(JsonUtils.jsonReaderName(typeElement))
            .addAnnotation(AnnotationUtils.generated(JsonReaderGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonReader, typeName))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(typeElement);
        var delegateType = ParameterizedTypeName.get(JsonTypes.enumJsonReader, typeName, enumValue.type().box());

        typeBuilder.addField(delegateType, "delegate", Modifier.PRIVATE, Modifier.FINAL);
        typeBuilder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ParameterizedTypeName.get(JsonTypes.jsonReader, enumValue.type().box()), "valueReader")
            .addCode("this.delegate = new $T<>($T.values(), $T::$N, valueReader);\n", JsonTypes.enumJsonReader, typeName, typeName, enumValue.accessor())
            .build());
        typeBuilder.addMethod(MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(JsonTypes.jsonParser, "__parser")
            .returns(typeName)
            .addAnnotation(Override.class)
            .addAnnotation(Nullable.class)
            .addCode("return this.delegate.read(__parser);\n")
            .build()
        );
        return typeBuilder.build();
    }

    record ReaderFactory(TypeName valueType, String methodName) {}

    private TypeSpec generateFactoryReader(TypeElement typeElement, ClassName typeName, ReaderFactory factory) {
        var valueReaderType = ParameterizedTypeName.get(JsonTypes.jsonReader, factory.valueType().box());
        var typeBuilder = TypeSpec.classBuilder(JsonUtils.jsonReaderName(typeElement))
            .addAnnotation(AnnotationUtils.generated(JsonReaderGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonReader, typeName))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(typeElement);
        typeBuilder.addField(valueReaderType, "valueReader", Modifier.PRIVATE, Modifier.FINAL);
        typeBuilder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(valueReaderType, "valueReader")
            .addStatement("this.valueReader = valueReader")
            .build());
        typeBuilder.addMethod(MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(JsonTypes.jsonParser, "__parser")
            .returns(typeName)
            .addAnnotation(Override.class)
            .addAnnotation(Nullable.class)
            .addStatement("var value = this.valueReader.read(__parser)")
            .addStatement("return value == null ? null : $T.$N(value)", typeName, factory.methodName())
            .build()
        );
        return typeBuilder.build();
    }

    @Nullable
    private ReaderFactory detectReaderFactory(TypeElement typeElement) {
        var factories = typeElement.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .filter(e -> e.getModifiers().contains(Modifier.STATIC))
            .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
            .map(ExecutableElement.class::cast)
            .filter(e -> AnnotationUtils.isAnnotationPresent(e, JsonTypes.jsonReaderAnnotation))
            .toList();
        if (factories.isEmpty()) {
            return null;
        }
        if (factories.size() > 1) {
            throw new ProcessingErrorException(
                "Enum " + typeElement.getSimpleName() + " has multiple @JsonReader factory methods, only one is allowed",
                typeElement
            );
        }
        var factory = factories.get(0);
        if (factory.getParameters().size() != 1) {
            throw new ProcessingErrorException(
                "@JsonReader factory method must have exactly one parameter, got " + factory.getParameters().size(),
                factory
            );
        }
        var typeName = ClassName.get(typeElement);
        if (!TypeName.get(factory.getReturnType()).equals(typeName)) {
            throw new ProcessingErrorException("@JsonReader factory method must return " + typeName, factory);
        }
        var valueType = TypeName.get(factory.getParameters().get(0).asType());
        return new ReaderFactory(valueType, factory.getSimpleName().toString());
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
