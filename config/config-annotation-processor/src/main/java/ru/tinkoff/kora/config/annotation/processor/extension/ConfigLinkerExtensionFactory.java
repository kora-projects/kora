package ru.tinkoff.kora.config.annotation.processor.extension;

import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public final class ConfigLinkerExtensionFactory implements ExtensionFactory {
    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var configValueExtractor = processingEnvironment.getElementUtils().getTypeElement(ConfigValueExtractor.class.getCanonicalName());
        if (configValueExtractor == null) {
            return Optional.empty();
        } else {
            return Optional.of(new ConfigKoraExtension(processingEnvironment));
        }
    }
}
