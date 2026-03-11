package io.koraframework.resilient.annotation.processor.aop;

import io.koraframework.aop.annotation.processor.KoraAspect;
import io.koraframework.aop.annotation.processor.KoraAspectFactory;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class RetryKoraAspectFactory implements KoraAspectFactory {

    @Override
    public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
        return Optional.of(new RetryKoraAspect(processingEnvironment));
    }
}
