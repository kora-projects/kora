package io.koraframework.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Tags of form part converters, one per media type declared in {@code encoding.contentType} besides plain JSON.
 * A converter of a JSON-like type ({@code +json}) has a default component that delegates to the {@code @Json} tagged converter,
 * a converter of any other type is provided by an application
 */
public class FormPartsModuleGenerator extends AbstractJavaGenerator<Map<String, Object>> {

    public static final String CLASS_NAME = "ApiFormPartsModule";

    private record DefaultConverter(String tag, TypeName valueType) {}

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

        var tags = new TreeSet<String>();
        var defaultConverters = new LinkedHashMap<DefaultConverter, List<String>>();
        for (var operations : new TreeMap<>(operationsByClassName).values()) {
            for (var operation : operations.getOperations().getOperation()) {
                for (var p : operation.formParams) {
                    var tag = formPartTag(p);
                    if (tag == null) {
                        continue;
                    }
                    tags.add(tag);
                    if (hasDefaultFormPartConverter(p)) {
                        var paramType = asType(p);
                        // an array part is converted element by element
                        var valueType = (p.isArray ? ((ParameterizedTypeName) paramType).typeArguments().getFirst() : paramType).box().withoutAnnotations();
                        defaultConverters.computeIfAbsent(new DefaultConverter(tag, valueType), c -> new ArrayList<>())
                            .add("%s.%s (%s)".formatted(operation.operationId, p.baseName, formPartContentType(p)));
                    }
                }
            }
        }
        for (var tag : tags) {
            b.addType(TypeSpec.classBuilder(tag)
                .addAnnotation(generated())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .build());
        }

        var isClient = params.codegenMode.isClient();
        var converter = isClient ? Classes.stringParameterConverter : Classes.stringParameterReader;
        var delegate = isClient ? "jsonWriter" : "jsonReader";
        for (var entry : defaultConverters.entrySet()) {
            var tag = entry.getKey().tag();
            var converterType = ParameterizedTypeName.get(converter, entry.getKey().valueType());
            var methodName = simpleName(entry.getKey().valueType()) + tag + (isClient ? "FormPartWriter" : "FormPartReader");
            b.addMethod(MethodSpec.methodBuilder(Character.toLowerCase(methodName.charAt(0)) + methodName.substring(1))
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addAnnotation(formPartTagAnnotation(tag))
                .addAnnotation(Classes.defaultComponent)
                .returns(converterType)
                .addParameter(ParameterSpec.builder(converterType, delegate).addAnnotation(Classes.json).build())
                .addStatement("log.info($S)", logMessage(tag, converterType, isClient, entry.getValue()))
                .addStatement("return $N::$N", delegate, isClient ? "convert" : "read")
                .build());
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private static String logMessage(String tag, TypeName converterType, boolean isClient, List<String> usages) {
        return "OpenAPI form parts of a JSON-like media type are %s with the @Json converter: %s. Provide own @Tag(%s.%s.class) %s component to override it."
            .formatted(isClient ? "written" : "read", String.join(", ", usages), CLASS_NAME, tag, converterType);
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
