package ru.tinkoff.kora.http.client.annotation.processor.extension;

import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

import static ru.tinkoff.kora.http.client.annotation.processor.HttpClientClassNames.httpClientAnnotation;

public class HttpClientLinkerExtensionFactory implements ExtensionFactory {
    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var httpClient = processingEnvironment.getElementUtils().getTypeElement(httpClientAnnotation.canonicalName());
        if (httpClient == null) {
            return Optional.empty();
        } else {
            return Optional.of(new HttpClientKoraExtension(processingEnvironment));
        }
    }
}
