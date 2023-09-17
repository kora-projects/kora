package ru.tinkoff.kora.http.client.annotation.processor.extension;

import ru.tinkoff.kora.http.client.annotation.processor.HttpClientClassNames;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public final class HttpClientResponseMapperKoraExtensionFactory implements ExtensionFactory {
    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var httpClientResponseMapper = processingEnvironment.getElementUtils().getTypeElement(HttpClientClassNames.httpClientResponseMapper.canonicalName());
        if (httpClientResponseMapper == null) {
            return Optional.empty();
        }
        return Optional.of(new HttpClientResponseMapperKoraExtension(processingEnvironment.getElementUtils(), processingEnvironment.getTypeUtils()));
    }
}
