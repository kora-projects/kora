package ru.tinkoff.kora.avro.annotation.processor.extension;

import ru.tinkoff.kora.avro.annotation.processor.AvroTypes;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class AvroExtensionFactory implements ExtensionFactory {

    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var avro = processingEnvironment.getElementUtils().getTypeElement(AvroTypes.avroBinary.canonicalName());
        if (avro == null) {
            return Optional.empty();
        } else {
            return Optional.of(new AvroExtension(processingEnvironment));
        }
    }
}
