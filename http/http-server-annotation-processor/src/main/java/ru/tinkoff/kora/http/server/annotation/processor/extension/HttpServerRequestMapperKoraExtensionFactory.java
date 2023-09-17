package ru.tinkoff.kora.http.server.annotation.processor.extension;

import ru.tinkoff.kora.http.server.annotation.processor.HttpServerClassNames;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

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
