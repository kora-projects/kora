package ru.tinkoff.kora.database.annotation.processor.cassandra.extension;

import ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraTypes;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class CassandraTypesExtensionFactory implements ExtensionFactory {
    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var type = processingEnvironment.getElementUtils().getTypeElement(CassandraTypes.ROW_MAPPER.canonicalName());
        if (type == null) {
            return Optional.empty();
        }

        return Optional.of(new CassandraTypesExtension(processingEnvironment));
    }
}
