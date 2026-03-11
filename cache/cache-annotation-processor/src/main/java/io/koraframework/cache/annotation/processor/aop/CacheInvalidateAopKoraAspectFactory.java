package io.koraframework.cache.annotation.processor.aop;

import io.koraframework.aop.annotation.processor.KoraAspect;
import io.koraframework.aop.annotation.processor.KoraAspectFactory;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class CacheInvalidateAopKoraAspectFactory implements KoraAspectFactory {

    @Override
    public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
        return Optional.of(new CacheInvalidateAopKoraAspect(processingEnvironment));
    }
}
