package io.koraframework.json.annotation.processor.writer;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.json.annotation.processor.JsonTypes;
import io.koraframework.json.annotation.processor.JsonUtils;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

/**
 * Generates a delegating {@code JsonWriter} for a non-enum type that declares a
 * {@code @JsonWriter}-annotated method producing the single JSON value the type is serialized as
 * (Jackson {@code @JsonValue} semantics). The method is either an instance method {@code () -> V} or a static
 * method {@code (T) -> V}.
 */
public class DelegatingWriterGenerator {

    public record DelegatingWrite(TypeName valueType, String accessor, boolean isStatic) {}

    public TypeSpec generate(TypeElement typeElement) {
        var typeName = ClassName.get(typeElement);
        var value = this.detectWriterMethod(typeElement);
        if (value == null) {
            throw new ProcessingErrorException("No @JsonWriter method found on " + typeName, typeElement);
        }
        var valueWriterType = ParameterizedTypeName.get(JsonTypes.jsonWriter, value.valueType().box());

        CodeBlock extracted = value.isStatic()
            ? CodeBlock.of("$T.$N(_object)", typeName, value.accessor())
            : CodeBlock.of("_object.$N()", value.accessor());
        var write = MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(JsonTypes.jsonGenerator, "_gen")
            .addParameter(ParameterSpec.builder(typeName.withoutAnnotations().annotated(CommonClassNames.nullableAnnotation), "_object").build())
            .addAnnotation(Override.class)
            .beginControlFlow("if (_object == null)")
            .addStatement("_gen.writeNull()")
            .addStatement("return")
            .endControlFlow()
            .addStatement("this.valueWriter.write(_gen, $L)", extracted)
            .build();

        return TypeSpec.classBuilder(JsonUtils.jsonWriterName(typeElement))
            .addAnnotation(AnnotationUtils.generated(DelegatingWriterGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonWriter, typeName))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(typeElement)
            .addField(valueWriterType, "valueWriter", Modifier.PRIVATE, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(valueWriterType, "valueWriter")
                .addStatement("this.valueWriter = valueWriter")
                .build())
            .addMethod(write)
            .build();
    }

    @Nullable
    public DelegatingWrite detectWriterMethod(TypeElement typeElement) {
        var methods = typeElement.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .map(ExecutableElement.class::cast)
            .filter(e -> AnnotationUtils.isAnnotationPresent(e, JsonTypes.jsonWriterAnnotation))
            .toList();
        if (methods.isEmpty()) {
            return null;
        }
        if (methods.size() > 1) {
            throw new ProcessingErrorException(
                "Type " + typeElement.getSimpleName() + " has multiple @JsonWriter methods, only one is allowed",
                methods.get(1));
        }
        var method = methods.get(0);
        if (!method.getModifiers().contains(Modifier.PUBLIC)) {
            throw new ProcessingErrorException("@JsonWriter method must be public", method);
        }
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            throw new ProcessingErrorException("@JsonWriter method must return a value", method);
        }
        var typeName = ClassName.get(typeElement);
        boolean isStatic = method.getModifiers().contains(Modifier.STATIC);
        if (isStatic) {
            if (method.getParameters().size() != 1) {
                throw new ProcessingErrorException(
                    "@JsonWriter static method must have exactly one parameter (the value type), got " + method.getParameters().size(),
                    method);
            }
            if (!TypeName.get(method.getParameters().get(0).asType()).equals(typeName)) {
                throw new ProcessingErrorException("@JsonWriter static method parameter must be of type " + typeName, method);
            }
        } else {
            if (!method.getParameters().isEmpty()) {
                throw new ProcessingErrorException(
                    "@JsonWriter instance method must have no parameters, got " + method.getParameters().size(),
                    method);
            }
        }
        return new DelegatingWrite(TypeName.get(method.getReturnType()), method.getSimpleName().toString(), isStatic);
    }
}
