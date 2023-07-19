package ru.tinkoff.kora.database.annotation.processor.vertx.extension;

import ru.tinkoff.kora.database.annotation.processor.vertx.VertxTypes;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class VertxTypesExtensionFactory implements ExtensionFactory {

    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var type = processingEnvironment.getElementUtils().getTypeElement(VertxTypes.ROW_MAPPER.canonicalName());
        if (type == null) {
            return Optional.empty();
        }

        return Optional.of(new VertxTypesExtension(processingEnvironment));
    }
}
