package ru.tinkoff.kora.http.client.annotation.processor.extension;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.GenericTypeResolver;
import ru.tinkoff.kora.http.client.annotation.processor.HttpClientClassNames;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public final class HttpClientResponseMapperKoraExtension implements KoraExtension {
    private final Elements elements;
    private final Types types;

    public HttpClientResponseMapperKoraExtension(Elements elements, Types types) {
        this.elements = elements;
        this.types = types;
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, Set<String> tags) {
        if (!tags.isEmpty()) {
            return null;
        }
        var typeName = TypeName.get(typeMirror);
        if (!(typeName instanceof ParameterizedTypeName ptn)) {
            return null;
        }
        if (!ptn.rawType.equals(HttpClientClassNames.httpClientResponseMapper)) {
            return null;
        }
        if (ptn.typeArguments.get(0) instanceof ParameterizedTypeName future && future.rawType.equals(ClassName.get(CompletionStage.class))) {
            return null;
        }
        var dt = (DeclaredType) typeMirror;
        return () -> {
            var fromAsync = this.elements.getTypeElement(HttpClientClassNames.httpClientResponseMapper.canonicalName()).getEnclosedElements()
                .stream()
                .filter(e -> e.getKind() == ElementKind.METHOD && e.getModifiers().contains(Modifier.STATIC))
                .map(ExecutableElement.class::cast)
                .filter(m -> m.getSimpleName().contentEquals("fromAsync"))
                .findFirst()
                .orElseThrow();
            var tp = (TypeVariable) fromAsync.getTypeParameters().get(0).asType();
            var responseType = dt.getTypeArguments().get(0);
            var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, responseType), fromAsync.asType());
            return ExtensionResult.fromExecutable(fromAsync, executableType);
        };

    }
}
