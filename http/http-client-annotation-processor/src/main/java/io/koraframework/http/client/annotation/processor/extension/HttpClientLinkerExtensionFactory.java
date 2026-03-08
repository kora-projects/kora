package io.koraframework.http.client.annotation.processor.extension;

import io.koraframework.kora.app.annotation.processor.extension.ExtensionFactory;
import io.koraframework.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

import static io.koraframework.http.client.annotation.processor.HttpClientClassNames.httpClientAnnotation;

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
