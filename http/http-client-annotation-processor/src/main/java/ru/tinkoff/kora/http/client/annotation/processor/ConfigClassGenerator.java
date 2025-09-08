package ru.tinkoff.kora.http.client.annotation.processor;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import static ru.tinkoff.kora.http.client.annotation.processor.HttpClientClassNames.*;

public class ConfigClassGenerator {

    private final Elements elements;

    public ConfigClassGenerator(Elements elements) {
        this.elements = elements;
    }

    public TypeSpec generate(TypeElement element) {
        var typeName = HttpClientUtils.configName(element);

        var b = TypeSpec.interfaceBuilder(typeName)
            .addOriginatingElement(element)
            .addAnnotation(AnnotationUtils.generated(ConfigClassGenerator.class))
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(declarativeHttpClientConfig)
            .addAnnotation(CommonClassNames.configValueExtractorAnnotation);

        b.addMethod(MethodSpec.methodBuilder("telemetry")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(telemetryHttpClientConfig)
            .build());

        for (var enclosedElement : elements.getAllMembers(element)) {
            if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getModifiers().contains(Modifier.STATIC) || enclosedElement.getModifiers().contains(Modifier.DEFAULT)) {
                continue;
            }
            if (enclosedElement.getEnclosingElement().toString().equals("java.lang.Object")) {
                continue;
            }
            var method = (ExecutableElement) enclosedElement;
            b.addMethod(MethodSpec.methodBuilder(method.getSimpleName().toString())
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(httpClientOperationConfig)
                .build()
            );
        }

        return b.build();
    }
}
