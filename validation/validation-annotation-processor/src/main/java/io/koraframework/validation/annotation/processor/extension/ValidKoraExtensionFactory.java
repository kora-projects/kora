package io.koraframework.validation.annotation.processor.extension;

import io.koraframework.kora.app.annotation.processor.extension.ExtensionFactory;
import io.koraframework.kora.app.annotation.processor.extension.KoraExtension;
import io.koraframework.validation.annotation.processor.ValidMeta;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

import static io.koraframework.validation.annotation.processor.ValidTypes.VALID_TYPE;

public final class ValidKoraExtensionFactory implements ExtensionFactory {

    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var element = processingEnvironment.getElementUtils().getTypeElement(VALID_TYPE.canonicalName());
        return (element == null)
            ? Optional.empty()
            : Optional.of(new ValidKoraExtension(processingEnvironment));
    }
}
