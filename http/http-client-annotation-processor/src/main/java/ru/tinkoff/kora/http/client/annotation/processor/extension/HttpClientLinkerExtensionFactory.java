package ru.tinkoff.kora.http.client.annotation.processor.extension;

import ru.tinkoff.kora.http.client.common.annotation.HttpClient;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class HttpClientLinkerExtensionFactory implements ExtensionFactory {
    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var httpClient = processingEnvironment.getElementUtils().getTypeElement(HttpClient.class.getCanonicalName());
        if (httpClient == null) {
            return Optional.empty();
        } else {
            return Optional.of(new HttpClientKoraExtension(processingEnvironment));
        }
    }
}
