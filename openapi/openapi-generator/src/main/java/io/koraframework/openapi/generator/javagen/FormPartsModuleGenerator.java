package io.koraframework.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Default converters of form parts that declare a non-JSON {@code encoding.contentType}: a mapper asks for an untagged converter,
 * and the default one delegates to the {@code @Json} tagged converter until an own component replaces it
 */
public class FormPartsModuleGenerator extends AbstractJavaGenerator<Map<String, Object>> {

    public static final String CLASS_NAME = "ApiFormPartsModule";

    @Override
    public JavaFile generate(Map<String, Object> ctx) {
        var className = ClassName.get(apiPackage, CLASS_NAME);
        var b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Classes.module)
            .addField(FieldSpec.builder(ClassName.get(Logger.class), "log")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.getLogger($T.class)", LoggerFactory.class, className)
                .build());

        var isClient = params.codegenMode.isClient();
        var converter = isClient ? Classes.stringParameterConverter : Classes.stringParameterReader;
        var methodNames = new HashSet<String>();
        for (var entry : fallbackParts().entrySet()) {
            var valueType = entry.getKey();
            var converterType = ParameterizedTypeName.get(converter, valueType);
            var methodName = methodName(valueType, isClient ? "FormPartWriter" : "FormPartReader");
            for (int i = 2; !methodNames.add(methodName); i++) {
                methodName = methodName(valueType, (isClient ? "FormPartWriter" : "FormPartReader") + i);
            }
            var delegate = isClient ? "jsonWriter" : "jsonReader";
            b.addMethod(MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addAnnotation(Classes.defaultComponent)
                .returns(converterType)
                .addParameter(ParameterSpec.builder(converterType, delegate).addAnnotation(Classes.json).build())
                .addStatement("log.info($S)", logMessage(converterType, isClient, entry.getValue()))
                .addStatement("return $N::$N", delegate, isClient ? "convert" : "read")
                .build());
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private Map<TypeName, List<String>> fallbackParts() {
        var parts = new LinkedHashMap<TypeName, List<String>>();
        for (var operations : new TreeMap<>(operationsByClassName).values()) {
            for (var operation : operations.getOperations().getOperation()) {
                for (var p : operation.formParams) {
                    if (!isJsonFallbackFormPart(p)) {
                        continue;
                    }
                    var paramType = asType(p);
                    // an array part is converted element by element
                    var valueType = (p.isArray ? ((ParameterizedTypeName) paramType).typeArguments().getFirst() : paramType).box().withoutAnnotations();
                    parts.computeIfAbsent(valueType, t -> new ArrayList<>()).add("%s.%s (%s)".formatted(operation.operationId, p.baseName, p.contentType));
                }
            }
        }
        return parts;
    }

    private static String logMessage(TypeName converterType, boolean isClient, List<String> usages) {
        return "OpenAPI form parts with non-JSON `encoding.contentType` are %s as JSON: %s. Provide own %s component to %s them in the declared format."
            .formatted(isClient ? "written" : "read", String.join(", ", usages), converterType, isClient ? "write" : "read");
    }

    private static String methodName(TypeName valueType, String suffix) {
        var name = simpleName(valueType) + suffix;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static String simpleName(TypeName type) {
        if (type instanceof ClassName className) {
            return String.join("", className.simpleNames());
        }
        if (type instanceof ParameterizedTypeName parameterized) {
            var name = new StringBuilder(simpleName(parameterized.rawType()));
            for (var typeArgument : parameterized.typeArguments()) {
                name.append(simpleName(typeArgument));
            }
            return name.toString();
        }
        return type.withoutAnnotations().toString().replaceAll("\\W", "");
    }
}
