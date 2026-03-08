package io.koraframework.http.server.annotation.processor.extension;

import io.koraframework.http.server.annotation.processor.HttpServerClassNames;
import io.koraframework.kora.app.annotation.processor.extension.ExtensionFactory;
import io.koraframework.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public final class HttpServerRequestMapperKoraExtensionFactory implements ExtensionFactory {
    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var httpServerRequestMapper = processingEnvironment.getElementUtils().getTypeElement(HttpServerClassNames.httpServerRequestMapper.canonicalName());
        if (httpServerRequestMapper == null) {
            return Optional.empty();
        }
        return Optional.of(new HttpServerRequestMapperKoraExtension(processingEnvironment.getElementUtils(), processingEnvironment.getTypeUtils()));
    }
}
