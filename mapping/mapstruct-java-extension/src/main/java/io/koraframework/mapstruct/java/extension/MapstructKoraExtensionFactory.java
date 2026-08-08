package io.koraframework.mapstruct.java.extension;

import io.koraframework.kora.app.annotation.processor.extension.ExtensionFactory;
import io.koraframework.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

import static io.koraframework.mapstruct.java.extension.MapstructKoraExtension.MAPPER_ANNOTATION;

public final class MapstructKoraExtensionFactory implements ExtensionFactory {
    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var mapper = processingEnvironment.getElementUtils().getTypeElement(MAPPER_ANNOTATION.canonicalName());
        if (mapper == null) {
            return Optional.empty();
        }
        return Optional.of(new MapstructKoraExtension(processingEnvironment));
    }
}
