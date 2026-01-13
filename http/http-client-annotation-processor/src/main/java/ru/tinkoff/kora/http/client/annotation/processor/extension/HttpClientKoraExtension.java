package ru.tinkoff.kora.http.client.annotation.processor.extension;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.http.client.annotation.processor.HttpClientAnnotationProcessor;
import ru.tinkoff.kora.http.client.annotation.processor.HttpClientClassNames;
import ru.tinkoff.kora.http.client.annotation.processor.HttpClientUtils;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;

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
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, String tag) {
        var typeName = TypeName.get(typeMirror);
        if (typeName instanceof ParameterizedTypeName mapper && mapper.rawType().equals(HttpClientClassNames.httpClientResponseMapper)) {
            if (mapper.typeArguments().getFirst() instanceof ParameterizedTypeName entity && entity.rawType().equals(HttpClientClassNames.httpResponseEntity)) {
                var mapperTypeMirror = (DeclaredType) typeMirror;
                var entityTypeMirror = (DeclaredType) mapperTypeMirror.getTypeArguments().getFirst();
                var responseMapperElement = this.elements.getTypeElement(HttpClientClassNames.httpClientResponseMapper.canonicalName());
                var responseEntityMapperElement = this.elements.getTypeElement(HttpClientClassNames.httpClientResponseEntityMapper.canonicalName());
                var responseType = entityTypeMirror.getTypeArguments().getFirst();
                var tags = new ArrayList<String>();
                tags.add(tag);
                return () -> new ExtensionResult.CodeBlockResult(
                    responseEntityMapperElement,
                    dependencies -> CodeBlock.of("new $T<>($L)", HttpClientClassNames.httpClientResponseEntityMapper, dependencies),
                    mapperTypeMirror,
                    tag,
                    List.of(this.types.getDeclaredType(responseMapperElement, responseType)),
                    tags
                );
            }
            return null;
        }

        if (tag != null) return null;
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
