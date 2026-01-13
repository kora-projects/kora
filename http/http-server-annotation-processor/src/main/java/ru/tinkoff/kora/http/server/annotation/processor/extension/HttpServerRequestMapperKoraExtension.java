package ru.tinkoff.kora.http.server.annotation.processor.extension;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.server.annotation.processor.HttpServerClassNames;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;

public final class HttpServerRequestMapperKoraExtension implements KoraExtension {
    private final Elements elements;
    private final Types types;

    public HttpServerRequestMapperKoraExtension(Elements elements, Types types) {
        this.elements = elements;
        this.types = types;
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, String tag) {
        var typeName = TypeName.get(typeMirror);
        if (!(typeName instanceof ParameterizedTypeName ptn)) {
            return null;
        }
        if (!ptn.rawType().equals(HttpServerClassNames.httpServerResponseMapper)) {
            return null;
        }
        if (ptn.typeArguments().getFirst() instanceof ParameterizedTypeName entity && entity.rawType().equals(HttpServerClassNames.httpResponseEntity)) {
            var mapperTypeMirror = (DeclaredType) typeMirror;
            var entityTypeMirror = (DeclaredType) mapperTypeMirror.getTypeArguments().getFirst();
            var responseMapperElement = this.elements.getTypeElement(HttpServerClassNames.httpServerResponseMapper.canonicalName());
            var responseEntityMapperElement = this.elements.getTypeElement(HttpServerClassNames.httpServerResponseEntityMapper.canonicalName());
            var responseType = entityTypeMirror.getTypeArguments().getFirst();
            var tags = new ArrayList<String>();
            tags.add(tag);
            return () -> new ExtensionResult.CodeBlockResult(
                responseEntityMapperElement,
                dependencies -> CodeBlock.of("new $T<>($L)", HttpServerClassNames.httpServerResponseEntityMapper, dependencies),
                mapperTypeMirror,
                tag,
                List.of(this.types.getDeclaredType(responseMapperElement, responseType)),
                tags
            );

        }
        return null;
    }
}
