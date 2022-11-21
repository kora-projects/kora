package ru.tinkoff.kora.aop.annotation.processor;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

public interface KoraAspectFactory {
    Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment);
}
