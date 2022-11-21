package ru.tinkoff.kora.kora.app.annotation.processor.extension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public interface ExtensionFactory {

    Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment);
}
