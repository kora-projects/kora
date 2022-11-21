package ru.tinkoff.kora.http.client.annotation.processor.extension;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.http.client.annotation.processor.HttpClientAnnotationProcessor;
import ru.tinkoff.kora.http.client.annotation.processor.HttpClientUtils;
import ru.tinkoff.kora.http.client.common.annotation.HttpClient;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class HttpClientKoraExtension implements KoraExtension {
    private final Elements elements;
    private final Types types;
    private final ProcessingEnvironment processingEnv;
    private final HttpClientAnnotationProcessor processor;

    public HttpClientKoraExtension(ProcessingEnvironment processingEnvironment) {
        this.processingEnv = processingEnvironment;
        this.elements = processingEnvironment.getElementUtils();
        this.types = processingEnvironment.getTypeUtils();
        this.processor = new HttpClientAnnotationProcessor();
        this.processor.init(processingEnvironment);
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror) {
        var element = this.types.asElement(typeMirror);
        if (element == null || element.getKind() != ElementKind.INTERFACE) {
            return null;
        }
        var annotation = element.getAnnotation(HttpClient.class);
        if (annotation == null) {
            return null;
        }
        return () -> {
            var typeElement = (TypeElement) element;
            var implName = HttpClientUtils.clientName(typeElement);
            var packageName = this.processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
            var maybeGenerated = this.elements.getTypeElement(packageName + "." + implName);
            if (maybeGenerated == null) {
                // annotation processor will handle it
                return ExtensionResult.nextRound();
            }
            var aopProxy = CommonUtils.getOuterClassesAsPrefix(maybeGenerated) + maybeGenerated.getSimpleName() + "__AopProxy";
            var aopProxyElement = this.elements.getTypeElement(packageName + "." + aopProxy);
            if (aopProxyElement == null) {
                // aop annotation processor will handle it
                return ExtensionResult.nextRound();
            }
            return maybeGenerated.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .map(ExtensionResult::fromExecutable)
                .findFirst()
                .orElseThrow();

        };
    }
}
