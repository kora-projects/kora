package ru.tinkoff.kora.json.annotation.processor.extension;

import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class JsonLinkerExtensionFactory implements ExtensionFactory {
    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var json = processingEnvironment.getElementUtils().getTypeElement(JsonTypes.json.canonicalName());
        if (json == null) {
            return Optional.empty();
        } else {
            return Optional.of(new JsonKoraExtension(processingEnvironment));
        }
    }
}
