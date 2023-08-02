package ru.tinkoff.kora.logging.aspect;

import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspectFactory;
import ru.tinkoff.kora.common.Component;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

@Component
public class LogAspectFactory implements KoraAspectFactory {

    @Override
    public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
        return Optional.of(new LogAspect(processingEnvironment));
    }

}
