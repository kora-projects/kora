package io.koraframework.json.annotation.processor.extension;

import io.koraframework.json.annotation.processor.JsonTypes;
import io.koraframework.kora.app.annotation.processor.extension.ExtensionFactory;
import io.koraframework.kora.app.annotation.processor.extension.KoraExtension;

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
