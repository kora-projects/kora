package io.koraframework.json.annotation.processor.codec;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.json.annotation.processor.*;
import org.jspecify.annotations.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

public class JsonDelegatingCodecGenerator {

    public TypeSpec generate(TypeElement typeElement) {
        var typeName = ClassName.get(typeElement);
        var factory = this.detectReaderFactory(typeElement);
        var value = this.detectWriterMethod(typeElement);

        if (factory == null && value == null) {
            return null;
        }

        String codecClassName = JsonUtils.jsonReaderName(typeElement).replace("JsonReader", "JsonCodec");

        var typeBuilder = TypeSpec.classBuilder(codecClassName)
            .addAnnotation(AnnotationUtils.generated(JsonDelegatingCodecGenerator.class))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(typeElement);

        var constructorBuilder = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        if (factory != null) {
            typeBuilder.addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonReader, typeName));
            var valueReaderType = ParameterizedTypeName.get(JsonTypes.jsonReader, factory.valueType().box());
            typeBuilder.addField(valueReaderType, "valueReader", Modifier.PRIVATE, Modifier.FINAL);
            constructorBuilder.addParameter(valueReaderType, "valueReader")
                .addStatement("this.valueReader = valueReader");
            this.addReadMethod(typeBuilder, typeName, factory);
        }

        if (value != null) {
            typeBuilder.addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonWriter, typeName));
            var valueWriterType = ParameterizedTypeName.get(JsonTypes.jsonWriter, value.valueType().box());
            typeBuilder.addField(valueWriterType, "valueWriter", Modifier.PRIVATE, Modifier.FINAL);
            constructorBuilder.addParameter(valueWriterType, "valueWriter")
                .addStatement("this.valueWriter = valueWriter");
            this.addWriteMethod(typeBuilder, typeName, value);
        }

        typeBuilder.addMethod(constructorBuilder.build());
        return typeBuilder.build();
    }

    private void addReadMethod(TypeSpec.Builder typeBuilder, ClassName typeName, DelegatingRead factory) {
        var readBuilder = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(JsonTypes.jsonParser, "__parser")
            .returns(typeName.withoutAnnotations().annotated(CommonClassNames.nullableAnnotation))
            .addAnnotation(Override.class);

        if (factory.valueNullable()) {
            readBuilder.addStatement("return $T.$N(this.valueReader.read(__parser))", typeName, factory.methodName());
        } else {
            readBuilder.addStatement("var value = this.valueReader.read(__parser)")
                .addStatement("return value == null ? null : $T.$N(value)", typeName, factory.methodName());
        }
        typeBuilder.addMethod(readBuilder.build());
    }

    private void addWriteMethod(TypeSpec.Builder typeBuilder, ClassName typeName, DelegatingWrite value) {
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

        typeBuilder.addMethod(write);
    }

    public record DelegatingRead(TypeName valueType, String methodName, boolean valueNullable) {}

    public record DelegatingWrite(TypeName valueType, String accessor, boolean isStatic) {}

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
        var factory = factories.getFirst();
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
        var valueNullable = CommonUtils.isNullable(factory.getParameters().getFirst());
        return new DelegatingRead(TypeName.get(factory.getParameters().getFirst().asType()), factory.getSimpleName().toString(), valueNullable);
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
        var method = methods.getFirst();
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
            if (!TypeName.get(method.getParameters().getFirst().asType()).equals(typeName)) {
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
