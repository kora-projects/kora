package io.koraframework.http.client.annotation.processor.extension;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.TagUtils;
import io.koraframework.http.client.annotation.processor.HttpClientAnnotationProcessor;
import io.koraframework.http.client.annotation.processor.HttpClientClassNames;
import io.koraframework.http.client.annotation.processor.HttpClientUtils;
import io.koraframework.kora.app.annotation.processor.extension.ExtensionResult;
import io.koraframework.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collections;
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
            if (mapper.typeArguments().getFirst() instanceof ParameterizedTypeName either && either.rawType().equals(HttpClientClassNames.either)) {
                var mapperTypeMirror = (DeclaredType) typeMirror;
                var eitherTypeMirror = (DeclaredType) mapperTypeMirror.getTypeArguments().getFirst();
                var responseMapperElement = this.elements.getTypeElement(HttpClientClassNames.httpClientResponseMapper.canonicalName());
                var eitherMapperElement = this.elements.getTypeElement(HttpClientClassNames.httpClientEitherResponseMapper.canonicalName());
                var successType = eitherTypeMirror.getTypeArguments().get(0);
                var errorType = eitherTypeMirror.getTypeArguments().get(1);
                var successTag = tag != null ? tag : TagUtils.parseTagValue(successType);
                var errorTag = tag != null ? tag : TagUtils.parseTagValue(errorType);
                return () -> new ExtensionResult.CodeBlockResult(
                    eitherMapperElement,
                    dependencies -> CodeBlock.of("new $T<>($L)", HttpClientClassNames.httpClientEitherResponseMapper, dependencies),
                    mapperTypeMirror,
                    tag,
                    List.of(
                        this.types.getDeclaredType(responseMapperElement, successType),
                        this.types.getDeclaredType(responseMapperElement, errorType)
                    ),
                    List.of(successTag, errorTag)
                );
            }
            if (mapper.typeArguments().getFirst() instanceof ParameterizedTypeName entity && entity.rawType().equals(HttpClientClassNames.httpResponseEntity)) {
                var mapperTypeMirror = (DeclaredType) typeMirror;
                var entityTypeMirror = (DeclaredType) mapperTypeMirror.getTypeArguments().getFirst();
                var responseMapperElement = this.elements.getTypeElement(HttpClientClassNames.httpClientResponseMapper.canonicalName());
                var responseEntityMapperElement = this.elements.getTypeElement(HttpClientClassNames.httpClientResponseEntityMapper.canonicalName());
                var responseType = entityTypeMirror.getTypeArguments().getFirst();
                if (TypeName.get(responseType) instanceof ParameterizedTypeName eitherResponse && eitherResponse.rawType().equals(HttpClientClassNames.either)) {
                    var eitherTypeMirror = (DeclaredType) responseType;
                    var successType = eitherTypeMirror.getTypeArguments().get(0);
                    var errorType = eitherTypeMirror.getTypeArguments().get(1);
                    var successTag = tag != null ? tag : TagUtils.parseTagValue(successType);
                    var errorTag = tag != null ? tag : TagUtils.parseTagValue(errorType);
                    return () -> new ExtensionResult.CodeBlockResult(
                        responseEntityMapperElement,
                        dependencies -> CodeBlock.of("new $T<>(new $T<>($L))", HttpClientClassNames.httpClientResponseEntityMapper, HttpClientClassNames.httpClientEitherResponseMapper, dependencies),
                        mapperTypeMirror,
                        tag,
                        List.of(
                            this.types.getDeclaredType(responseMapperElement, successType),
                            this.types.getDeclaredType(responseMapperElement, errorType)
                        ),
                        List.of(successTag, errorTag)
                    );
                }
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
            if (HttpClientClassNames.json.canonicalName().equals(tag)) {
                var mapperTypeMirror = (DeclaredType) typeMirror;
                var responseType = mapperTypeMirror.getTypeArguments().getFirst();
                var jsonReaderElement = this.elements.getTypeElement(HttpClientClassNames.jsonReader.canonicalName());
                var jsonMapperElement = this.elements.getTypeElement(HttpClientClassNames.jsonHttpClientResponseMapper.canonicalName());
                return () -> new ExtensionResult.CodeBlockResult(
                    jsonMapperElement,
                    dependencies -> CodeBlock.of("new $T<>($L)", HttpClientClassNames.jsonHttpClientResponseMapper, dependencies),
                    mapperTypeMirror,
                    tag,
                    List.of(this.types.getDeclaredType(jsonReaderElement, responseType)),
                    Collections.singletonList(null)
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
