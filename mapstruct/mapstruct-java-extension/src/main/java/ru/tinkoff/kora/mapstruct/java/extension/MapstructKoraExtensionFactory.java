package ru.tinkoff.kora.mapstruct.java.extension;

import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

import static ru.tinkoff.kora.mapstruct.java.extension.MapstructKoraExtension.MAPPER_ANNOTATION;

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
