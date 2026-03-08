package io.koraframework.validation.annotation.processor.aop;

import io.koraframework.aop.annotation.processor.KoraAspect;
import io.koraframework.aop.annotation.processor.KoraAspectFactory;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class ValidateMethodKoraAspectFactory implements KoraAspectFactory {

    @Override
    public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
        return Optional.of(new ValidateMethodKoraAspect(processingEnvironment));
    }
}
