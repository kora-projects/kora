package ru.tinkoff.kora.validation.annotation.processor.aop;

import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspectFactory;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class ValidateMethodKoraAspectFactory implements KoraAspectFactory {

    @Override
    public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
        return Optional.of(new ValidateMethodKoraAspect(processingEnvironment));
    }
}
