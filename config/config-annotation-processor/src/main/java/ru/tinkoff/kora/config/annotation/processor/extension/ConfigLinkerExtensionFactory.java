package ru.tinkoff.kora.config.annotation.processor.extension;

import ru.tinkoff.kora.config.annotation.processor.ConfigClassNames;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public final class ConfigLinkerExtensionFactory implements ExtensionFactory {
    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var configType = processingEnvironment.getElementUtils().getTypeElement(ConfigClassNames.config.canonicalName());
        if (configType == null) {
            return Optional.empty();
        } else {
            return Optional.of(new ConfigKoraExtension(processingEnvironment));
        }
    }
}
