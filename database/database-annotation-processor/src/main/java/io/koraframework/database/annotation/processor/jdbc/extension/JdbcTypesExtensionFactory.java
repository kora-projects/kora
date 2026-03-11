package io.koraframework.database.annotation.processor.jdbc.extension;

import io.koraframework.database.annotation.processor.jdbc.JdbcTypes;
import io.koraframework.kora.app.annotation.processor.extension.ExtensionFactory;
import io.koraframework.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class JdbcTypesExtensionFactory implements ExtensionFactory {

    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var type = processingEnvironment.getElementUtils().getTypeElement(JdbcTypes.JDBC_ENTITY.canonicalName());
        if (type == null) {
            return Optional.empty();
        }

        return Optional.of(new JdbcTypesExtension(processingEnvironment));
    }
}
