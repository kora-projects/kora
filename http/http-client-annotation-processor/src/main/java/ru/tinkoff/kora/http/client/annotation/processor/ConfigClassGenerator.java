package ru.tinkoff.kora.http.client.annotation.processor;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import static ru.tinkoff.kora.http.client.annotation.processor.HttpClientClassNames.declarativeHttpClientConfig;
import static ru.tinkoff.kora.http.client.annotation.processor.HttpClientClassNames.httpClientOperationConfig;

public class ConfigClassGenerator {

    public ConfigClassGenerator() {
    }

    public TypeSpec generate(TypeElement element) {
        var typeName = HttpClientUtils.configName(element);

        var b = TypeSpec.interfaceBuilder(typeName)
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(declarativeHttpClientConfig)
            .addAnnotation(CommonClassNames.configValueExtractorAnnotation);
        for (var enclosedElement : element.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getModifiers().contains(Modifier.STATIC) || enclosedElement.getModifiers().contains(Modifier.DEFAULT)) {
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
