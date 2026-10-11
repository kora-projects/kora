package io.koraframework.avro.annotation.processor.extension;

import io.koraframework.avro.annotation.processor.AvroTypes;
import io.koraframework.kora.app.annotation.processor.extension.ExtensionFactory;
import io.koraframework.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class AvroExtensionFactory implements ExtensionFactory {

    @Override
    public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
        var avro = processingEnvironment.getElementUtils().getTypeElement(AvroTypes.avro.canonicalName());
        if (avro == null) {
            return Optional.empty();
        } else {
            return Optional.of(new AvroExtension(processingEnvironment));
        }
    }
}
