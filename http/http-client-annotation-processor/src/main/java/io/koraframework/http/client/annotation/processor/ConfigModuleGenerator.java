package io.koraframework.http.client.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import static io.koraframework.http.client.annotation.processor.HttpClientClassNames.httpClientAnnotation;

public class ConfigModuleGenerator {
    private final Elements elements;

    public ConfigModuleGenerator(ProcessingEnvironment processingEnvironment) {
        this.elements = processingEnvironment.getElementUtils();
    }

    /**
     * Client and config are written as nested types of the module: the number of generated source files matters for compilation time
     */
    public JavaFile generate(TypeElement element, TypeSpec client, TypeSpec config) {
        var lowercaseName = new StringBuilder(element.getSimpleName());
        lowercaseName.setCharAt(0, Character.toLowerCase(lowercaseName.charAt(0)));
        var packageName = this.elements.getPackageOf(element).getQualifiedName().toString();

        var configPath = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(AnnotationUtils.findAnnotation(element, httpClientAnnotation), "value");
        if (configPath == null || configPath.isBlank()) {
            configPath = "httpClient." + lowercaseName;
        }

        var moduleName = HttpClientUtils.moduleName(element);
        var configClass = HttpClientUtils.configClassName(this.elements, element);
        var extractorClass = ParameterizedTypeName.get(CommonClassNames.configValueMapper, configClass);

        var type = TypeSpec.interfaceBuilder(moduleName)
            .addOriginatingElement(element)
            .addAnnotation(AnnotationUtils.generated(ConfigModuleGenerator.class))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(CommonClassNames.module)
            .addMethod(MethodSpec.methodBuilder(lowercaseName + "_Config")
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .returns(configClass)
                .addParameter(ParameterSpec.builder(CommonClassNames.config, "config").build())
                .addParameter(ParameterSpec.builder(extractorClass, "mapper").build())
                .addStatement("return mapper.mapOrThrow(config.get($S))", configPath)
                .build())
            .addType(client)
            .addType(config);

        return JavaFile.builder(packageName, type.build()).build();
    }
}
