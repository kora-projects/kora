package ru.tinkoff.kora.json.annotation.processor.reader;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.IOException;

/**
 * Generates a delegating {@code JsonReader} for a non-enum type that declares a
 * {@code @JsonReader}-annotated {@code public static} factory method {@code (V) -> T}. The JSON value is read as
 * {@code V} and passed to the factory (Jackson {@code @JsonCreator(mode = DELEGATING)} semantics).
 */
public class DelegatingReaderGenerator {

    public record DelegatingRead(TypeName valueType, String methodName, boolean valueNullable) {}

    public TypeSpec generate(TypeElement typeElement) {
        var typeName = ClassName.get(typeElement);
        var factory = this.detectReaderFactory(typeElement);
        if (factory == null) {
            throw new ProcessingErrorException("No @JsonReader factory method found on " + typeName, typeElement);
        }
        var valueReaderType = ParameterizedTypeName.get(JsonTypes.jsonReader, factory.valueType().box());

        var readBuilder = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(JsonTypes.jsonParser, "__parser")
            .returns(typeName)
            .addAnnotation(Override.class)
            .addAnnotation(Nullable.class);
        if (factory.valueNullable()) {
            readBuilder.addStatement("return $T.$N(this.valueReader.read(__parser))", typeName, factory.methodName());
        } else {
            readBuilder.addStatement("var value = this.valueReader.read(__parser)")
                .addStatement("return value == null ? null : $T.$N(value)", typeName, factory.methodName());
        }
        var read = readBuilder.build();

        return TypeSpec.classBuilder(JsonUtils.jsonReaderName(typeElement))
            .addAnnotation(AnnotationUtils.generated(DelegatingReaderGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonReader, typeName))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(typeElement)
            .addField(valueReaderType, "valueReader", Modifier.PRIVATE, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(valueReaderType, "valueReader")
                .addStatement("this.valueReader = valueReader")
                .build())
            .addMethod(read)
            .build();
    }

    @Nullable
    public DelegatingRead detectReaderFactory(TypeElement typeElement) {
        var factories = typeElement.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .map(ExecutableElement.class::cast)
            .filter(e -> AnnotationUtils.isAnnotationPresent(e, JsonTypes.jsonReaderAnnotation))
            .toList();
        if (factories.isEmpty()) {
            return null;
        }
        if (factories.size() > 1) {
            throw new ProcessingErrorException(
                "Type " + typeElement.getSimpleName() + " has multiple @JsonReader factory methods, only one is allowed",
                factories.get(1));
        }
        var factory = factories.get(0);
        if (!factory.getModifiers().contains(Modifier.PUBLIC) || !factory.getModifiers().contains(Modifier.STATIC)) {
            throw new ProcessingErrorException("@JsonReader factory method must be public static", factory);
        }
        if (factory.getParameters().size() != 1) {
            throw new ProcessingErrorException(
                "@JsonReader factory method must have exactly one parameter, got " + factory.getParameters().size(),
                factory);
        }
        var typeName = ClassName.get(typeElement);
        if (!TypeName.get(factory.getReturnType()).equals(typeName)) {
            throw new ProcessingErrorException("@JsonReader factory method must return " + typeName, factory);
        }
        var hasReaderConstructor = CommonUtils.findConstructors(typeElement, m -> !m.contains(Modifier.PRIVATE)).stream()
            .anyMatch(c -> AnnotationUtils.isAnnotationPresent(c, JsonTypes.jsonReaderAnnotation)
                || AnnotationUtils.isAnnotationPresent(c, JsonTypes.json));
        if (hasReaderConstructor) {
            throw new ProcessingErrorException(
                "Type " + typeName + " has both a @JsonReader factory method and a @JsonReader/@Json constructor — only one is allowed",
                factory);
        }
        var valueNullable = CommonUtils.isNullable(factory.getParameters().get(0));
        return new DelegatingRead(TypeName.get(factory.getParameters().get(0).asType()), factory.getSimpleName().toString(), valueNullable);
    }
}
