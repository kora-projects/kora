package ru.tinkoff.kora.http.client.annotation.processor.extension;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.http.client.annotation.processor.HttpClientAnnotationProcessor;
import ru.tinkoff.kora.http.client.annotation.processor.HttpClientClassNames;
import ru.tinkoff.kora.http.client.annotation.processor.HttpClientUtils;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Set;

public class HttpClientKoraExtension implements KoraExtension {
    private final Elements elements;
    private final Types types;
    private final HttpClientAnnotationProcessor processor;

    public HttpClientKoraExtension(ProcessingEnvironment processingEnvironment) {
        this.elements = processingEnvironment.getElementUtils();
        this.types = processingEnvironment.getTypeUtils();
        this.processor = new HttpClientAnnotationProcessor();
        this.processor.init(processingEnvironment);
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, Set<String> tags) {
        if (!tags.isEmpty()) return null;
        var element = this.types.asElement(typeMirror);
        if (element == null || element.getKind() != ElementKind.INTERFACE) {
            return null;
        }
        var annotation = AnnotationUtils.findAnnotation(element, HttpClientClassNames.httpClientAnnotation);
        if (annotation == null) {
            return null;
        }
        var typeElement = (TypeElement) element;
        var implName = HttpClientUtils.clientName(typeElement);
        return KoraExtensionDependencyGenerator.generatedFromWithName(elements, element, implName);
    }
}
