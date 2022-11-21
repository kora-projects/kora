package ru.tinkoff.kora.database.annotation.processor.jdbc.extension;

import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class JdbcTypesExtensionFactory implements ExtensionFactory {
    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var type = processingEnvironment.getElementUtils().getTypeElement("ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper");
        if (type == null) {
            return Optional.empty();
        }

        return Optional.of(new JdbcTypesExtension(processingEnvironment));
    }
}
