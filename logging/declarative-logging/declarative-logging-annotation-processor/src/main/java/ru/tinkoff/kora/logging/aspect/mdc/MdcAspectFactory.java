package ru.tinkoff.kora.logging.aspect.mdc;

import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspectFactory;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public class MdcAspectFactory implements KoraAspectFactory {

    @Override
    public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
        return Optional.of(new MdcAspect());
    }
}
