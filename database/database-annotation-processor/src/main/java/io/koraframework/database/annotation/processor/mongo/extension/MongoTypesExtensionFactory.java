package io.koraframework.database.annotation.processor.mongo.extension;

import io.koraframework.database.annotation.processor.mongo.MongoTypes;
import io.koraframework.kora.app.annotation.processor.extension.ExtensionFactory;
import io.koraframework.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class MongoTypesExtensionFactory implements ExtensionFactory {

    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var type = processingEnvironment.getElementUtils().getTypeElement(MongoTypes.MONGO_ENTITY.canonicalName());
        if (type == null) {
            return Optional.empty();
        }

        return Optional.of(new MongoTypesExtension(processingEnvironment));
    }
}
