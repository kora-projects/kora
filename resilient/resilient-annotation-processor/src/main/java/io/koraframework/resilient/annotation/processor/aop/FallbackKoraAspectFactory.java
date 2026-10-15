package io.koraframework.resilient.annotation.processor.aop;

import io.koraframework.aop.annotation.processor.KoraAspect;
import io.koraframework.aop.annotation.processor.KoraAspectFactory;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;

public class FallbackKoraAspectFactory implements KoraAspectFactory {

    @Override
    public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
        return Optional.of(new FallbackKoraAspect(processingEnvironment));
    }
}
