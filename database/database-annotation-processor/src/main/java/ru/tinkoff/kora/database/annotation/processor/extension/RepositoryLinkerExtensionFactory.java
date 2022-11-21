package ru.tinkoff.kora.database.annotation.processor.extension;


import ru.tinkoff.kora.database.annotation.processor.DbUtils;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class RepositoryLinkerExtensionFactory implements ExtensionFactory {
    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var repository = processingEnvironment.getElementUtils().getTypeElement(DbUtils.REPOSITORY_ANNOTATION.canonicalName());
        if (repository == null) {
            return Optional.empty();
        } else {
            return Optional.of(new RepositoryKoraExtension(processingEnvironment));
        }
    }
}
