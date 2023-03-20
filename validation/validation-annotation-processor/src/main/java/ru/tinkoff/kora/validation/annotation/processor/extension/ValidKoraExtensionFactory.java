package ru.tinkoff.kora.validation.annotation.processor.extension;

import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;
import ru.tinkoff.kora.validation.annotation.processor.ValidMeta;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public final class ValidKoraExtensionFactory implements ExtensionFactory {

    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var element = processingEnvironment.getElementUtils().getTypeElement(ValidMeta.VALID_TYPE.canonicalName());
        return (element == null)
            ? Optional.empty()
            : Optional.of(new ValidKoraExtension(processingEnvironment));
    }
}
